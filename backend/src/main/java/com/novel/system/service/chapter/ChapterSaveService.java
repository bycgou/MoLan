package com.novel.system.service.chapter;

import com.novel.system.entity.mysql.NovelBase;
import com.novel.system.entity.mysql.NovelChapter;
import com.novel.system.entity.mongo.ChapterSummary;
import com.novel.system.mapper.NovelBaseMapper;
import com.novel.system.mapper.NovelChapterMapper;
import com.novel.system.ai.rag.RagService;
import com.novel.system.ai.rag.EmbeddingService;
import com.novel.system.ai.rag.RedisVectorService;
import com.novel.system.repository.mongo.NovelGraphRepository;
import com.novel.system.entity.mongo.NovelGraph;
import com.novel.system.service.story.StoryStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 章节保存服务 - 独立的事务方法
 *
 * 从 ChapterGenerationServiceImpl 抽取，消除自注入反模式。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChapterSaveService {

    private final NovelBaseMapper novelBaseMapper;
    private final NovelChapterMapper novelChapterMapper;
    private final RagService ragService;
    private final NovelGraphRepository novelGraphRepository;
    private final EmbeddingService embeddingService;
    private final RedisVectorService redisVectorService;
    private final StoryStateService storyStateService;
    @Qualifier("ioTaskExecutor")
    private final Executor ioTaskExecutor;

    /**
     * 保存章节生成结果 - 两阶段保存
     * 阶段1: MySQL事务写入（章节内容、字数统计）
     * 阶段2: 异步写入（向量化、知识图谱、故事状态）
     */
    public void saveGenerationResult(String novelId, int chapterNum, String chapterTitle,
                                     String chapterOutline, String chapterContent,
                                     int wordCount, String userIntent, String plotFingerprint,
                                     ChapterSummary summary) {
        // 阶段1: MySQL事务写入
        saveToMySQL(novelId, chapterNum, chapterTitle, chapterOutline, chapterContent,
                   wordCount, userIntent, plotFingerprint);

        // 阶段2: 异步写入（向量化、知识图谱、故事状态）
        saveToMongoAsync(novelId, chapterNum, chapterTitle, chapterContent, wordCount, summary);
    }

    /**
     * 保存章节生成结果并返回Future（用于链式调用）
     * 阶段1: MySQL事务写入（同步）
     * 阶段2: 异步写入（向量化、知识图谱、故事状态）
     *
     * @return CompletableFuture，MongoDB写入完成后完成
     */
    public CompletableFuture<Void> saveGenerationResultAsync(String novelId, int chapterNum, String chapterTitle,
                                     String chapterOutline, String chapterContent,
                                     int wordCount, String userIntent, String plotFingerprint,
                                     ChapterSummary summary) {
        // 阶段1: MySQL事务写入（同步）
        saveToMySQL(novelId, chapterNum, chapterTitle, chapterOutline, chapterContent,
                   wordCount, userIntent, plotFingerprint);

        // 阶段2: 异步写入（向量化、知识图谱、故事状态），使用指定线程池
        // 传播 SecurityContext 以便异步线程中能获取当前用户ID
        SecurityContext securityContext = SecurityContextHolder.getContext();
        return CompletableFuture.runAsync(() -> {
            SecurityContextHolder.setContext(securityContext);
            try {
                saveToMongoAsync(novelId, chapterNum, chapterTitle, chapterContent, wordCount, summary);
            } finally {
                SecurityContextHolder.clearContext();
            }
        }, ioTaskExecutor);
    }

    @Transactional
    public void saveToMySQL(String novelId, int chapterNum, String chapterTitle,
                               String chapterOutline, String chapterContent,
                               int wordCount, String userIntent, String plotFingerprint) {
        // 保存章节内容
        NovelChapter chapter = new NovelChapter();
        chapter.setNovelId(novelId);
        chapter.setChapterNum(chapterNum);
        chapter.setChapterTitle(chapterTitle);
        chapter.setChapterOutline(chapterOutline);
        chapter.setContent(chapterContent);
        chapter.setWordCount(wordCount);
        chapter.setGenerationStatus(2);
        chapter.setUserIntent(userIntent);
        chapter.setPlotFingerprint(plotFingerprint);
        chapter.setCreateTime(LocalDateTime.now());
        chapter.setUpdateTime(LocalDateTime.now());

        // 检查章节是否已存在
        NovelChapter existing = novelChapterMapper.selectByNovelIdAndChapterNum(novelId, chapterNum);
        if (existing != null) {
            chapter.setChapterId(existing.getChapterId());
            novelChapterMapper.updateById(chapter);
        } else {
            chapter.setChapterId(UUID.randomUUID().toString().replace("-", ""));
            novelChapterMapper.insert(chapter);
        }

        // 更新小说字数统计
        NovelBase novel = novelBaseMapper.selectById(novelId);
        if (novel != null) {
            novel.setWordCount((novel.getWordCount() != null ? novel.getWordCount() : 0) + wordCount);
            novel.setChapterCount(Math.max(novel.getChapterCount() != null ? novel.getChapterCount() : 0, chapterNum));
            novel.setUpdateTime(LocalDateTime.now());
            novelBaseMapper.updateById(novel);
        }

        log.info("MySQL写入完成: novelId={}, chapter={}", novelId, chapterNum);
    }

    @Async
    public void saveToMongoAsync(String novelId, int chapterNum, String chapterTitle,
                                    String chapterContent, int wordCount, ChapterSummary summary) {
        try {
            // 向量化章节内容（异步增强RAG检索）
            String textToEmbed = chapterContent.length() > 2000
                    ? chapterContent.substring(0, 2000) : chapterContent;
            float[] vector = embeddingService.embed(textToEmbed);
            if (vector.length > 0) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("novelId", novelId);
                metadata.put("chapterNum", chapterNum);
                metadata.put("chapterTitle", chapterTitle);
                metadata.put("wordCount", wordCount);
                String vectorKey = novelId + ":ch" + chapterNum;
                redisVectorService.saveVector(vectorKey, vector, metadata);
                log.info("章节向量化完成: 小说ID={}, 章节号={}, 向量维度={}", novelId, chapterNum, vector.length);
            }
        } catch (Exception e) {
            log.warn("章节向量化失败（不影响保存）: {}", e.getMessage());
        }

        // 保存摘要（MongoDB）
        try {
            ragService.saveSummary(summary);
        } catch (Exception e) {
            log.warn("保存摘要失败（不影响保存）: {}", e.getMessage());
        }

        // 更新知识图谱
        if (summary != null) {
            List<String> chars = summary.getCharacters() != null ? summary.getCharacters() : Collections.emptyList();
            List<Map<String, String>> rels = summary.getRelations() != null ? summary.getRelations() : Collections.emptyList();
            if (!chars.isEmpty() || !rels.isEmpty()) {
                updateNovelGraph(novelId, chapterNum, chars, rels);
            }
        }

        // 更新故事状态（核心枢纽）
        try {
            String summaryText = summary != null ? summary.getContent() : "";
            storyStateService.updateAfterChapter(novelId, chapterNum, summaryText, chapterContent);
            log.info("故事状态已更新: novelId={}, chapter={}", novelId, chapterNum);
        } catch (Exception e) {
            log.warn("更新故事状态失败（不影响保存）: {}", e.getMessage());
        }

        log.info("MongoDB异步写入完成: novelId={}, chapter={}", novelId, chapterNum);
    }

    private void updateNovelGraph(String novelId, int chapterNum,
                                   List<String> characters, List<Map<String, String>> relations) {
        try {
            NovelGraph graph = novelGraphRepository.findById(novelId).orElseGet(() -> {
                NovelGraph g = new NovelGraph();
                g.setNovelId(novelId);
                g.setNodes(new ArrayList<>());
                g.setEdges(new ArrayList<>());
                return g;
            });

            Set<String> existingNodes = new HashSet<>();
            for (NovelGraph.Node n : graph.getNodes()) {
                existingNodes.add(n.getName());
            }

            // 从 summary.characters 创建节点
            for (String charName : characters) {
                if (charName != null && !charName.isBlank() && !existingNodes.contains(charName)) {
                    NovelGraph.Node n = new NovelGraph.Node();
                    n.setId(UUID.randomUUID().toString());
                    n.setName(charName);
                    n.setType("person");
                    graph.getNodes().add(n);
                    existingNodes.add(charName);
                }
            }

            for (Map<String, String> rel : relations) {
                String source = rel.get("source");
                String target = rel.get("target");
                String relation = rel.get("relation");

                if (source != null && !existingNodes.contains(source)) {
                    NovelGraph.Node n = new NovelGraph.Node();
                    n.setId(UUID.randomUUID().toString());
                    n.setName(source);
                    n.setType("unknown");
                    graph.getNodes().add(n);
                    existingNodes.add(source);
                }
                if (target != null && !existingNodes.contains(target)) {
                    NovelGraph.Node n = new NovelGraph.Node();
                    n.setId(UUID.randomUUID().toString());
                    n.setName(target);
                    n.setType("unknown");
                    graph.getNodes().add(n);
                    existingNodes.add(target);
                }

                boolean edgeExists = false;
                for (NovelGraph.Edge e : graph.getEdges()) {
                    if (e.getSource().equals(source) && e.getTarget().equals(target)) {
                        e.setRelation(relation);
                        e.setLastUpdateChapter(chapterNum);
                        edgeExists = true;
                        break;
                    }
                }
                if (!edgeExists && source != null && target != null) {
                    NovelGraph.Edge e = new NovelGraph.Edge();
                    e.setSource(source);
                    e.setTarget(target);
                    e.setRelation(relation);
                    e.setLastUpdateChapter(chapterNum);
                    graph.getEdges().add(e);
                }
            }
            novelGraphRepository.save(graph);
        } catch (Exception e) {
            log.warn("更新知识图谱失败: {}", e.getMessage());
        }
    }
}
