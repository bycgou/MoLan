package com.novel.system.entity.mongo;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 剧情摘要文档 (MongoDB) - 用于向量检索
 */
@Document(collection = "chapter_summary")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChapterSummary {
    
    /** MongoDB文档ID */
    @Id
    private String id;
    
    /** 所属小说ID */
    @Indexed
    private String novelId;
    
    /** 章节序号 */
    @Indexed
    private Integer chapterNum;
    
    /** 剧情指纹（300字摘要）- 用于全文搜索 */
    @TextIndexed(weight = 2)
    private String content;
    
    /** 关键词列表 - 用于全文搜索 */
    @TextIndexed(weight = 1)
    private List<String> keywords;
    
    /** 出场人物ID列表 */
    private List<String> characters;
    
    /** 相关地点列表 */
    private List<String> locations;
    
    /** 优先级（用于伏笔相关，1-高，2-中，3-低） */
    private Integer priority;
    
    /** 情感倾向（如：紧张、温馨、悲伤） */
    private String emotion;
    
    /** 图谱关系（从SummaryAgent抽取，key包含source/relation/target） */
    private List<java.util.Map<String, String>> relations;
    
    /** 剧情类型（如：战斗、对话、回忆、转折） */
    private String plotType;
    
    /** 向量嵌入（用于相似度检索） */
    private List<Float> embedding;
    
    /** 创建时间 */
    private LocalDateTime createTime;
    
    // Explicit getters/setters (in case Lombok @Data is not working)
    public String getId() { return id; }
    public Integer getPriority() { return priority; }
    public Integer getChapterNum() { return chapterNum; }
    public String getNovelId() { return novelId; }
    public String getContent() { return content; }
    public List<String> getKeywords() { return keywords; }
    public void setEmbedding(List<Float> embedding) { this.embedding = embedding; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
