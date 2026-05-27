package com.novel.system.ai.rag;

import com.novel.system.entity.mongo.ChapterSummary;
import com.novel.system.entity.mongo.StoryState;
import com.novel.system.repository.mongo.ChapterSummaryRepository;
import com.novel.system.service.llm.LlmService;
import com.novel.system.service.story.StoryStateService;
import com.novel.system.ai.rag.RagService;
import com.novel.system.ai.rag.RedisVectorService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.data.mongodb.core.query.TextQuery;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * RAG检索服务实现 — 五层记忆架构第六层：RAG向量检索
 * 支持Rerank和混合检索（向量 + BM25 + RRF融合）。
 * 当前仍为Push模式（自动注入），待改为Agent @Tool Pull模式。
 */
@Service
@RequiredArgsConstructor
public class RagServiceImpl implements RagService {

    private final ChapterSummaryRepository chapterSummaryRepository;
    private final MongoTemplate mongoTemplate;
    private final LlmService llmService;
    private final RedisVectorService redisVectorService;
    private final StoryStateService storyStateService;

    // RRF融合参数
    private static final double RRF_K = 60.0;
    private static final Logger log = LoggerFactory.getLogger(RagServiceImpl.class);
    
    @Override
    public List<ChapterSummary> retrieveContext(String novelId, int currentChapterNum, 
                                                List<String> characters, List<String> keywords) {
        log.info("开始混合检索，小说ID: {}, 当前章节: {}", novelId, currentChapterNum);
        
        List<ChapterSummary> results = new ArrayList<>();
        
        // 1. 获取最近10章的摘要
        List<ChapterSummary> recentSummaries = getRecentSummaries(novelId, currentChapterNum, 10);
        results.addAll(recentSummaries);
        
        // Build set of already-seen IDs for O(1) dedup
        Set<String> seenIds = new HashSet<>();
        for (ChapterSummary s : results) {
            seenIds.add(s.getId());
        }

        // 2. 混合检索（向量 + BM25 + RRF）
        try {
            String queryText = buildQueryFromRecentSummaries(currentChapterNum, recentSummaries);
            List<ChapterSummary> hybridResults = hybridSearch(novelId, queryText, 5);

            for (ChapterSummary summary : hybridResults) {
                if (seenIds.add(summary.getId())) {
                    results.add(summary);
                }
            }
        } catch (Exception e) {
            log.warn("混合检索失败: {}", e.getMessage());
        }

        // 3. 关键词检索
        if (keywords != null && !keywords.isEmpty()) {
            try {
                List<ChapterSummary> keywordResults = chapterSummaryRepository
                    .findByNovelIdAndKeywordsIn(novelId, keywords);
                for (ChapterSummary summary : keywordResults) {
                    if (seenIds.add(summary.getId())) {
                        results.add(summary);
                    }
                }
            } catch (Exception e) {
                log.warn("关键词检索失败: {}", e.getMessage());
            }
        }
        
        // 4. 优先级排序
        results.sort((a, b) -> {
            int priorityCompare = Integer.compare(
                a.getPriority() != null ? a.getPriority() : 3,
                b.getPriority() != null ? b.getPriority() : 3
            );
            if (priorityCompare != 0) return priorityCompare;
            return Integer.compare(
                b.getChapterNum() != null ? b.getChapterNum() : 0,
                a.getChapterNum() != null ? a.getChapterNum() : 0
            );
        });
        
        // 限制返回数量
        return results.stream().limit(15).collect(Collectors.toList());
    }
    
    @Override
    public List<ChapterSummary> getRecentSummaries(String novelId, int currentChapterNum, int count) {
        int startChapter = Math.max(1, currentChapterNum - count);
        return chapterSummaryRepository.findRecentSummaries(novelId, startChapter, currentChapterNum);
    }
    
    @Override
    public List<ChapterSummary> vectorSearch(String novelId, String queryText, int topK) {
        try {
            // 生成查询向量
            float[] queryVector = llmService.getEmbedding(queryText);
            if (queryVector == null || queryVector.length == 0) {
                log.warn("无法获取有效的查询向量，跳过向量搜索");
                return Collections.emptyList();
            }
            
            // 构建过滤条件
            String filter = "@novelId:{" + novelId + "}";
            
            // 执行向量搜索
            List<RedisVectorService.VectorSearchResult> results = 
                redisVectorService.search(queryVector, topK, filter);
            
            // 获取对应的ChapterSummary
            List<ChapterSummary> summaries = new ArrayList<>();
            for (RedisVectorService.VectorSearchResult result : results) {
                Map<String, Object> metadata = result.getMetadata();
                if (metadata != null && metadata.containsKey("chapterNum")) {
                    int chapterNum = ((Number) metadata.get("chapterNum")).intValue();
                    chapterSummaryRepository.findByNovelIdAndChapterNum(novelId, chapterNum)
                        .ifPresent(summaries::add);
                }
            }
            
            return summaries;
            
        } catch (Exception e) {
            log.error("向量搜索失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
    
    @Override
    public List<ChapterSummary> bm25Search(String novelId, String queryText, int topK) {
        try {
            // 使用MongoDB全文索引（对中文使用整个查询文本，由MongoDB的zh locale处理分词）
            TextCriteria criteria = TextCriteria.forDefaultLanguage()
                .matching(queryText);

            Query query = TextQuery.queryText(criteria)
                .sortByScore()
                .addCriteria(Criteria.where("novelId").is(novelId))
                .limit(topK);

            return mongoTemplate.find(query, ChapterSummary.class);

        } catch (Exception e) {
            log.warn("BM25搜索失败，回退到关键词搜索: {}", e.getMessage());

            // 回退到基于摘要keywords字段的匹配
            Query query = new Query();
            query.addCriteria(Criteria.where("novelId").is(novelId));
            query.addCriteria(Criteria.where("content").regex(Pattern.quote(queryText.substring(0, Math.min(queryText.length(), 50)))));
            query.with(Sort.by(Sort.Direction.DESC, "chapterNum"));
            query.limit(topK);

            return mongoTemplate.find(query, ChapterSummary.class);
        }
    }
    
    @Override
    public List<ChapterSummary> hybridSearch(String novelId, String queryText, int topK) {
        log.info("执行混合检索，小说ID: {}, 查询: {}", novelId, queryText);
        
        // 1. 向量检索 + BM25检索（并行执行）
        CompletableFuture<List<ChapterSummary>> vectorFuture = CompletableFuture.supplyAsync(
            () -> vectorSearch(novelId, queryText, topK * 2));
        CompletableFuture<List<ChapterSummary>> bm25Future = CompletableFuture.supplyAsync(
            () -> bm25Search(novelId, queryText, topK * 2));
        CompletableFuture.allOf(vectorFuture, bm25Future).join();
        List<ChapterSummary> vectorResults = vectorFuture.join();
        List<ChapterSummary> bm25Results = bm25Future.join();
        
        // 3. RRF融合
        Map<String, Double> rrfScores = new HashMap<>();
        Map<String, ChapterSummary> summaryMap = new HashMap<>();
        
        // 向量检索结果RRF评分
        for (int i = 0; i < vectorResults.size(); i++) {
            ChapterSummary s = vectorResults.get(i);
            String key = s.getNovelId() + ":" + s.getChapterNum();
            double rrfScore = 1.0 / (RRF_K + i + 1);
            rrfScores.merge(key, rrfScore, Double::sum);
            summaryMap.put(key, s);
        }
        
        // BM25检索结果RRF评分
        for (int i = 0; i < bm25Results.size(); i++) {
            ChapterSummary s = bm25Results.get(i);
            String key = s.getNovelId() + ":" + s.getChapterNum();
            double rrfScore = 1.0 / (RRF_K + i + 1);
            rrfScores.merge(key, rrfScore, Double::sum);
            summaryMap.put(key, s);
        }
        
        // 4. 按RRF分数排序
        List<Map.Entry<String, Double>> sortedEntries = rrfScores.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(topK * 2)
            .collect(Collectors.toList());
        
        List<ChapterSummary> fusedResults = new ArrayList<>();
        for (Map.Entry<String, Double> entry : sortedEntries) {
            ChapterSummary summary = summaryMap.get(entry.getKey());
            if (summary != null) {
                fusedResults.add(summary);
            }
        }
        
        // 5. Rerank重排序
        List<ChapterSummary> rerankedResults = rerank(queryText, fusedResults, topK);
        
        log.info("混合检索完成，返回{}条结果", rerankedResults.size());
        
        return rerankedResults;
    }
    
    @Override
    public List<ChapterSummary> rerank(String queryText, List<ChapterSummary> summaries, int topK) {
        if (summaries == null || summaries.isEmpty()) {
            return summaries;
        }
        
        try {
            // 计算每个摘要与查询的相关性分数
            Map<ChapterSummary, Double> scores = new HashMap<>();
            
            for (ChapterSummary summary : summaries) {
                double score = calculateRelevanceScore(queryText, summary);
                scores.put(summary, score);
            }
            
            // 按分数排序
            return scores.entrySet().stream()
                .sorted(Map.Entry.<ChapterSummary, Double>comparingByValue().reversed())
                .limit(topK)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.warn("Rerank失败，返回原始顺序: {}", e.getMessage());
            return summaries.stream().limit(topK).collect(Collectors.toList());
        }
    }
    
    private String buildQueryFromRecentSummaries(int currentChapterNum, List<ChapterSummary> recentSummaries) {
        List<ChapterSummary> querySummaries = recentSummaries.size() > 3
            ? recentSummaries.subList(0, 3)
            : recentSummaries;
        StringBuilder sb = new StringBuilder();
        for (ChapterSummary s : querySummaries) {
            if (s.getContent() != null && !s.getContent().isEmpty()) {
                String snippet = s.getContent().length() > 200
                    ? s.getContent().substring(0, 200)
                    : s.getContent();
                sb.append(snippet).append("。");
            }
        }
        return sb.length() > 0
            ? sb.toString().trim()
            : "第" + currentChapterNum + "章相关剧情";
    }

    /**
     * 计算相关性分数（使用中文字符二元组bigram进行匹配）
     */
    private double calculateRelevanceScore(String query, ChapterSummary summary) {
        if (summary.getContent() == null) {
            return 0.0;
        }

        String content = summary.getContent().toLowerCase();
        String queryLower = query.toLowerCase();

        double score = 0.0;

        // 使用字符二元组（bigram）进行中文友好匹配
        Set<String> queryBigrams = extractBigrams(queryLower);
        Set<String> contentBigrams = extractBigrams(content);

        if (!queryBigrams.isEmpty()) {
            int overlap = 0;
            for (String bigram : queryBigrams) {
                if (contentBigrams.contains(bigram)) {
                    overlap++;
                }
            }
            // Overlap ratio as relevance score
            score += (double) overlap / queryBigrams.size() * 3.0;
        }

        // 额外的整串包含匹配（对短查询有效）
        if (queryLower.length() >= 2 && content.contains(queryLower)) {
            score += 2.0;
        }

        // 关键词匹配加分
        if (summary.getKeywords() != null) {
            for (String keyword : summary.getKeywords()) {
                String kw = keyword.toLowerCase();
                if (queryLower.contains(kw) || content.contains(kw)) {
                    score += 0.5;
                }
            }
        }

        // 优先级加权
        int priority = summary.getPriority() != null ? summary.getPriority() : 3;
        score *= (4 - priority) * 0.25 + 0.5;

        return score;
    }

    /**
     * 从文本中提取字符二元组（bigram），用于中文文本相似度计算。
     * 会跳过标点和空白字符，只保留有意义的字符对。
     */
    private Set<String> extractBigrams(String text) {
        Set<String> bigrams = new HashSet<>();
        // 过滤掉标点和空白，只保留有意义的字符
        StringBuilder cleaned = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (!Character.isWhitespace(c) && !Character.isISOControl(c)
                && !String.valueOf(c).matches("[，。！？、；：\\p{Punct}]")) {
                cleaned.append(c);
            }
        }
        for (int i = 0; i < cleaned.length() - 1; i++) {
            bigrams.add(cleaned.substring(i, i + 2));
        }
        // 也把单字加入，处理极短文本
        if (cleaned.length() == 1) {
            bigrams.add(cleaned.toString());
        }
        return bigrams;
    }
    
    @Override
    public ChapterSummary saveSummary(ChapterSummary summary) {
        // 处理summary为null的情况
        if (summary == null) {
            log.warn("摘要对象为null，跳过保存");
            return null;
        }
        
        // 生成向量嵌入
        if (summary.getContent() != null) {
            try {
                float[] embedding = llmService.getEmbedding(summary.getContent());
                
                // 保存到Redis Stack向量数据库
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("novelId", summary.getNovelId());
                metadata.put("chapterNum", summary.getChapterNum());
                metadata.put("content", summary.getContent());
                metadata.put("keywords", String.join(",", summary.getKeywords() != null ? summary.getKeywords() : List.of()));
                metadata.put("priority", summary.getPriority() != null ? summary.getPriority() : 3);
                
                String key = summary.getNovelId() + ":ch" + summary.getChapterNum();
                redisVectorService.saveVector(key, embedding, metadata);
                
                // 同时保存到MongoDB
                List<Float> embeddingList = new ArrayList<>();
                for (float f : embedding) {
                    embeddingList.add(f);
                }
                summary.setEmbedding(embeddingList);
                
                log.info("向量保存成功: {}", key);
                
            } catch (Exception e) {
                log.error("向量化失败，跳过Redis向量保存（摘要仍会保存到MongoDB）: {}", e.getMessage());
                summary.setEmbedding(null);
            }
        }
        
        summary.setCreateTime(LocalDateTime.now());
        return chapterSummaryRepository.save(summary);
    }
    
    @Override
    public void compressMemory(String novelId, int endChapter) {
        log.info("开始记忆压缩，小说ID: {}, 结束章节: {}", novelId, endChapter);

        // 从StoryState获取之前的压缩记忆
        StoryState storyState = storyStateService.getOrCreate(novelId);
        String previousMemory = storyState.getCompressedBackstory() != null ? storyState.getCompressedBackstory() : "";
        int startChapter = storyState.getLastCompressChapter() != null ? storyState.getLastCompressChapter() + 1 : 1;

        if (startChapter >= endChapter) {
            log.info("没有需要压缩的新章节");
            return;
        }

        List<ChapterSummary> summariesToCompress = chapterSummaryRepository.findRecentSummaries(novelId, startChapter, endChapter);
        if (summariesToCompress.isEmpty()) return;

        StringBuilder contentToCompress = new StringBuilder();
        for (ChapterSummary summary : summariesToCompress) {
            contentToCompress.append("第").append(summary.getChapterNum()).append("章剧情: ")
                             .append(summary.getContent() != null ? summary.getContent() : "").append("\n");
        }

        String systemPrompt = "你是一个专业的小说世界观架构师，请将以下数十章零碎的剧情指纹，浓缩成一个精炼的'全局背景和现状'。必须保留核心伏笔、重要人物生死状态和世界格局变化，丢弃次要细节。字数控制在800字以内。";
        String userMessage = "之前已有的全局背景：\n" + previousMemory + "\n\n新发生的剧情：\n" + contentToCompress.toString();

        try {
            String newGlobalMemory = CompletableFuture.supplyAsync(() ->
                llmService.generateWithUsage(systemPrompt, userMessage, "summary")
            ).orTimeout(30, TimeUnit.SECONDS).exceptionally(ex -> {
                log.warn("compressMemory LLM call timed out or failed", ex);
                return previousMemory;  // return original on failure
            }).join();

            // 更新StoryState中的压缩背景
            storyState.setCompressedBackstory(newGlobalMemory);
            storyState.setLastCompressChapter(endChapter);
            storyStateService.save(storyState);

            log.info("记忆压缩完成，压缩后的全局记忆长度: {}", newGlobalMemory.length());

        } catch (Exception e) {
            log.error("记忆压缩失败: {}", e.getMessage());
        }
    }

    @Override
    public String getGlobalMemory(String novelId) {
        StoryState storyState = storyStateService.getOrCreate(novelId);
        return storyState.getCompressedBackstory() != null ? storyState.getCompressedBackstory() : "";
    }
    
    @Override
    public String assembleContext(String novelId, int chapterNum, String totalOutline, 
                                  String arcOutline, String characterStates, String unrecycledClues) {
        StringBuilder context = new StringBuilder();
        
        // 0. 载入全局压缩记忆
        String globalMemory = getGlobalMemory(novelId);
        if (globalMemory != null && !globalMemory.isEmpty()) {
            context.append("## 全局背景记忆\n");
            context.append(globalMemory).append("\n\n");
        }
        
        // 1. 小说总纲
        context.append("## 小说总纲\n");
        String outline = totalOutline != null ? totalOutline : "无";
        if (outline.length() > 2000) {
            outline = outline.substring(0, 2000) + "...(已截断)";
        }
        context.append(outline).append("\n\n");
        
        // 2. 当前卷纲
        context.append("## 当前卷纲\n");
        String arc = arcOutline != null ? arcOutline : "无";
        if (arc.length() > 1000) {
            arc = arc.substring(0, 1000) + "...(已截断)";
        }
        context.append(arc).append("\n\n");
        
        // 3. 未回收伏笔
        context.append("## 未回收伏笔\n");
        context.append(unrecycledClues != null ? unrecycledClues : "无").append("\n\n");
        
        // 4. 人物状态
        context.append("## 人物状态\n");
        String states = characterStates != null ? characterStates : "无";
        if (states.length() > 1000) {
            states = states.substring(0, 1000) + "...(已截断)";
        }
        context.append(states).append("\n\n");
        
        // 5. 混合检索相关剧情
        try {
            List<ChapterSummary> recentForQuery = getRecentSummaries(novelId, chapterNum, 3);
            String queryText = buildQueryFromRecentSummaries(chapterNum, recentForQuery);
            List<ChapterSummary> relevantSummaries = hybridSearch(novelId, queryText, 5);
            
            if (!relevantSummaries.isEmpty()) {
                context.append("## 相关剧情（混合检索）\n");
                for (ChapterSummary summary : relevantSummaries) {
                    context.append("第").append(summary.getChapterNum()).append("章: ");
                    String content = summary.getContent();
                    if (content != null && content.length() > 200) {
                        content = content.substring(0, 200) + "...";
                    }
                    context.append(content).append("\n");
                }
                context.append("\n");
            }
        } catch (Exception e) {
            log.warn("混合检索失败，使用最近章节: {}", e.getMessage());
            
            // 回退到最近章节
            List<ChapterSummary> recentSummaries = getRecentSummaries(novelId, chapterNum, 3);
            if (!recentSummaries.isEmpty()) {
                context.append("## 近期剧情\n");
                for (ChapterSummary summary : recentSummaries) {
                    context.append("第").append(summary.getChapterNum()).append("章: ");
                    String content = summary.getContent();
                    if (content != null && content.length() > 300) {
                        content = content.substring(0, 300) + "...";
                    }
                    context.append(content).append("\n");
                }
                context.append("\n");
            }
        }
        
        return context.toString();
    }
}
