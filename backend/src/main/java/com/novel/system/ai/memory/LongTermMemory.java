package com.novel.system.ai.memory;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 长期记忆服务 - 基于MongoDB
 * 
 * 存储小说创作过程中的关键信息：
 * - 人物状态变化
 * - 伏笔回收记录
 * - 重要情节节点
 * - 世界观扩展
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LongTermMemory {
    
    private final MongoTemplate mongoTemplate;
    
    /**
     * 写入记忆
     */
    public void writeMemory(MemoryEntry entry) {
        entry.setCreateTime(LocalDateTime.now());
        mongoTemplate.save(entry);
        log.info("写入长期记忆: novelId={}, type={}, chapter={}", 
                entry.getNovelId(), entry.getMemoryType(), entry.getChapterNum());
    }
    
    /**
     * 查询小说的记忆
     */
    public List<MemoryEntry> queryMemory(String novelId, String memoryType, int limit) {
        Query query = new Query();
        query.addCriteria(Criteria.where("novelId").is(novelId));
        if (memoryType != null) {
            query.addCriteria(Criteria.where("memoryType").is(memoryType));
        }
        query.limit(limit);
        query.with(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "createTime"));
        
        return mongoTemplate.find(query, MemoryEntry.class);
    }
    
    /**
     * 查询指定章节范围的记忆
     */
    public List<MemoryEntry> queryMemoryByChapterRange(String novelId, int startChapter, int endChapter) {
        Query query = new Query();
        query.addCriteria(Criteria.where("novelId").is(novelId));
        query.addCriteria(Criteria.where("chapterNum").gte(startChapter).lte(endChapter));
        query.with(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.ASC, "chapterNum"));
        
        return mongoTemplate.find(query, MemoryEntry.class);
    }
    
    /**
     * 查询相关人物的记忆
     */
    public List<MemoryEntry> queryCharacterMemory(String novelId, String characterId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("novelId").is(novelId));
        query.addCriteria(Criteria.where("relatedCharacters").in(characterId));
        query.limit(20);
        query.with(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "chapterNum"));
        
        return mongoTemplate.find(query, MemoryEntry.class);
    }
    
    /**
     * 获取格式化的记忆文本 - 用于注入到Prompt
     */
    public String getFormattedMemory(String novelId, int currentChapter) {
        // 获取最近20章的记忆
        List<MemoryEntry> entries = queryMemoryByChapterRange(novelId, 
                Math.max(1, currentChapter - 20), currentChapter - 1);
        
        if (entries.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("===长期记忆===\n");
        for (MemoryEntry entry : entries) {
            sb.append("[第").append(entry.getChapterNum()).append("章|");
            sb.append(entry.getMemoryType()).append("] ");
            String content = entry.getContent();
            if (content.length() > 200) content = content.substring(0, 200) + "...";
            sb.append(content).append("\n");
        }
        return sb.toString();
    }
    
    /**
     * 记录人物状态变化
     */
    public void recordCharacterChange(String novelId, int chapterNum, String characterId, String change) {
        MemoryEntry entry = new MemoryEntry();
        entry.setNovelId(novelId);
        entry.setChapterNum(chapterNum);
        entry.setMemoryType("character_change");
        entry.setContent(change);
        entry.setRelatedCharacters(List.of(characterId));
        entry.setImportance(3);
        writeMemory(entry);
    }
    
    /**
     * 记录伏笔回收
     */
    public void recordClueResolution(String novelId, int chapterNum, String clueId, String clueContent) {
        MemoryEntry entry = new MemoryEntry();
        entry.setNovelId(novelId);
        entry.setChapterNum(chapterNum);
        entry.setMemoryType("clue_resolved");
        entry.setContent("伏笔回收: " + clueContent);
        entry.setRelatedClues(List.of(clueId));
        entry.setImportance(4);
        writeMemory(entry);
    }
    
    /**
     * 记录重要情节
     */
    public record ImportantPlot(String novelId, int chapterNum, String description) {}
    
    public void recordImportantPlot(String novelId, int chapterNum, String description) {
        MemoryEntry entry = new MemoryEntry();
        entry.setNovelId(novelId);
        entry.setChapterNum(chapterNum);
        entry.setMemoryType("important_plot");
        entry.setContent(description);
        entry.setImportance(5);
        writeMemory(entry);
    }
    
    @org.springframework.data.mongodb.core.mapping.Document(collection = "novel_memory")
    @Data
    public static class MemoryEntry {
        @org.springframework.data.annotation.Id
        private String id;
        private String novelId;
        private Integer chapterNum;
        /** character_change/clue_resolved/important_plot/world_building */
        private String memoryType;
        private String content;
        private List<String> relatedCharacters;
        private List<String> relatedClues;
        /** 重要程度 1-5 */
        private Integer importance = 3;
        private LocalDateTime createTime;
    }
}
