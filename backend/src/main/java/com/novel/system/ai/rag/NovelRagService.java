package com.novel.system.ai.rag;

import com.novel.system.entity.mongo.ChapterSummary;
import com.novel.system.repository.mongo.ChapterSummaryRepository;
import com.novel.system.mapper.NovelChapterMapper;
import com.novel.system.entity.mysql.NovelChapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 小说RAG语义检索服务
 * 
 * 核心能力：
 * 1. 基于关键词/人物的章节摘要检索
 * 2. 基于章节号的近期上下文获取
 * 3. 组装完整的写作上下文
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NovelRagService {
    
    private final ChapterSummaryRepository chapterSummaryRepository;
    private final NovelChapterMapper novelChapterMapper;
    private final MongoTemplate mongoTemplate;
    private final EmbeddingService embeddingService;
    
    /**
     * 检索相关上下文 - 基于人物和关键词
     */
    public List<ChapterSummary> retrieveContext(String novelId, int currentChapterNum,
                                                 List<String> characters, List<String> keywords) {
        log.info("RAG检索: novelId={}, chapter={}, characters={}, keywords={}", 
                novelId, currentChapterNum, characters, keywords);
        
        // 从MongoDB查询摘要
        Query query = new Query();
        query.addCriteria(Criteria.where("novelId").is(novelId));
        query.addCriteria(Criteria.where("chapterNum").lt(currentChapterNum));
        query.limit(50); // 最多检索50条
        
        List<ChapterSummary> allSummaries = mongoTemplate.find(query, ChapterSummary.class);
        
        // 关键词匹配打分
        Map<ChapterSummary, Double> scored = new LinkedHashMap<>();
        for (ChapterSummary summary : allSummaries) {
            double score = calculateRelevance(summary, characters, keywords);
            if (score > 0) {
                scored.put(summary, score);
            }
        }
        
        // 按分数降序，取Top 5
        return scored.entrySet().stream()
                .sorted(Map.Entry.<ChapterSummary, Double>comparingByValue().reversed())
                .limit(5)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }
    
    /**
     * 获取最近N章的摘要 - 用于保持上下文连贯
     */
    public List<ChapterSummary> getRecentSummaries(String novelId, int currentChapterNum, int count) {
        Query query = new Query();
        query.addCriteria(Criteria.where("novelId").is(novelId));
        query.addCriteria(Criteria.where("chapterNum").lt(currentChapterNum));
        query.limit(count);
        query.with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "chapterNum"));
        
        List<ChapterSummary> summaries = mongoTemplate.find(query, ChapterSummary.class);
        // 反转为正序
        Collections.reverse(summaries);
        return summaries;
    }
    
    /**
     * 组装完整的写作上下文
     * 
     * @param novelId 小说ID
     * @param chapterNum 当前章节号
     * @param totalOutline 小说总纲
     * @param arcOutline 卷纲
     * @param characterStates 人物状态文本
     * @param clues 伏笔文本
     * @return 组装好的上下文字符串
     */
    public String assembleContext(String novelId, int chapterNum, String totalOutline,
                                   String arcOutline, String characterStates, String clues) {
        StringBuilder sb = new StringBuilder();
        
        // 1. 近期剧情摘要
        List<ChapterSummary> recentSummaries = getRecentSummaries(novelId, chapterNum, 5);
        if (!recentSummaries.isEmpty()) {
            sb.append("===前情提要===\n");
            for (ChapterSummary summary : recentSummaries) {
                sb.append("第").append(summary.getChapterNum()).append("章: ");
                sb.append(summary.getContent() != null ? summary.getContent() : "无摘要");
                sb.append("\n");
            }
            sb.append("\n");
        }
        
        // 2. 相关人物信息
        if (characterStates != null && !characterStates.isEmpty()) {
            sb.append("===出场人物===\n");
            sb.append(characterStates).append("\n\n");
        }
        
        // 3. 未回收伏笔
        if (clues != null && !clues.isEmpty()) {
            sb.append("===未回收伏笔===\n");
            sb.append(clues).append("\n\n");
        }
        
        return sb.toString();
    }
    
    /**
     * 保存章节摘要
     */
    public void saveSummary(ChapterSummary summary) {
        try {
            // 检查是否已存在
            Query query = new Query();
            query.addCriteria(Criteria.where("novelId").is(summary.getNovelId()));
            query.addCriteria(Criteria.where("chapterNum").is(summary.getChapterNum()));
            ChapterSummary existing = mongoTemplate.findOne(query, ChapterSummary.class);
            
            if (existing != null) {
                summary.setId(existing.getId());
            }
            
            mongoTemplate.save(summary);
            log.info("保存章节摘要: novelId={}, chapter={}", summary.getNovelId(), summary.getChapterNum());
        } catch (Exception e) {
            log.error("保存章节摘要失败: {}", e.getMessage());
        }
    }
    
    /**
     * 计算摘要与查询的相关性分数
     */
    private double calculateRelevance(ChapterSummary summary, List<String> characters, List<String> keywords) {
        double score = 0;
        
        // 人物匹配
        if (characters != null && summary.getCharacters() != null) {
            for (String char1 : characters) {
                for (String char2 : summary.getCharacters()) {
                    if (char2.contains(char1) || char1.contains(char2)) {
                        score += 3.0;
                    }
                }
            }
        }
        
        // 关键词匹配
        if (keywords != null && summary.getKeywords() != null) {
            for (String kw : keywords) {
                for (String summaryKw : summary.getKeywords()) {
                    if (summaryKw.contains(kw) || kw.contains(summaryKw)) {
                        score += 2.0;
                    }
                }
            }
        }
        
        // 近期加权 - 章节号越近分数越高
        if (summary.getChapterNum() != null) {
            score += 1.0 / (1 + Math.abs(summary.getChapterNum()) * 0.01);
        }
        
        return score;
    }
}
