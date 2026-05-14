package com.novel.system.entity.mysql;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 小说弧线/卷实体
 */
@Data
public class NovelArc {
    /** 弧线ID，主键 */
    private String arcId;
    /** 所属小说ID */
    private String novelId;
    /** 卷名称 */
    private String arcName;
    /** 卷大纲内容 */
    private String arcOutline;
    /** 本卷章节数量 */
    private Integer chapterCount = 0;
    /** 本卷起始章节号 */
    private Integer startChapter;
    /** 本卷结束章节号 */
    private Integer endChapter;
    /** 节奏标签：quest-主线/fire-感情线/constellation-世界观线 */
    private String rhythmTag;
    /** 节奏强度（1-10） */
    private Integer rhythmIntensity = 5;
    /** 章节级编排计划JSON（由ArcOrchestrationService生成） */
    private String chapterPlan;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
}
