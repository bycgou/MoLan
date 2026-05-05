package com.novel.system.entity.mysql;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 章节实体
 */
@Data
public class NovelChapter {
    /** 章节ID，主键 */
    private String chapterId;
    /** 所属小说ID */
    private String novelId;
    /** 所属卷/弧线ID */
    private String arcId;
    /** 章节序号 */
    private Integer chapterNum;
    /** 章节标题 */
    private String chapterTitle;
    /** 章节大纲/章纲内容 */
    private String chapterOutline;
    /** 实现思路（AI生成的写作方向） */
    private String implementationIdea;
    /** 章节正文内容 */
    private String content;
    /** 章节字数 */
    private Integer wordCount = 0;
    /** 节奏标签：quest-主线/fire-感情线/constellation-世界观线 */
    private String rhythmTag;
    /** 节奏强度（1-10） */
    private Integer rhythmIntensity = 5;
    /** 剧情指纹（300字摘要，用于RAG检索） */
    private String plotFingerprint;
    /** 用户意图/写作要求 */
    private String userIntent;
    /** 生成状态：0-待生成, 1-生成中, 2-已生成, 3-生成失败 */
    private Integer generationStatus = 0;
    /** 是否在回收站（软删除标记） */
    private Boolean isDeleted = false;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
    
    /**
     * 结尾钩子
     * 章节结尾的悬念，用于承接下一章
     */
    private String hook;
    
    /**
     * 关键情节点（JSON格式）
     * 本章的关键事件列表，用于事件去重
     */
    private String keyPoints;
    
    /**
     * 章节摘要
     * 冗余存储，加速查询，避免每次从正文提取
     */
    private String chapterSummary;
    
    /**
     * 本章使用的意象/场景（JSON格式）
     * 用于意象去重，禁止近5章重复使用
     */
    private String usedTropes;
    
    /**
     * 关联的章节简介ID
     * 指向 chapter_synopses 表的 ID
     */
    private Long synopsisId;
}
