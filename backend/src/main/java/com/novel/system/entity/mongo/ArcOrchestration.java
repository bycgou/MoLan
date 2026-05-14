package com.novel.system.entity.mongo;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.List;

/**
 * 卷级编排文档 (MongoDB)
 *
 * 存储一卷小说的章节级编排计划，包括节奏分布、伏笔埋收、视角交替等。
 * 在卷纲生成后、章纲生成前创建，作为章纲生成的全局约束。
 */
@Document(collection = "arc_orchestrations")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArcOrchestration {

    @Id
    private String id;

    /** 小说ID */
    @Indexed
    private String novelId;

    /** 卷ID（唯一索引，一个卷只有一份编排） */
    @Indexed(unique = true)
    private String arcId;

    /** 卷名 */
    private String arcName;

    /** 本卷整体节奏描述 */
    private String overallRhythm;

    /** 高潮章节号列表 */
    private List<Integer> climaxChapters;

    /** 各章节编排计划 */
    private List<ChapterPlan> chapters;

    /** 创建时间 */
    private String createdAt;

    // ========== 内部类定义 ==========

    /**
     * 单章编排计划
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChapterPlan {
        /** 章节号 */
        private Integer chapterNum;
        /** 节奏标签：quest-主线/fire-高潮/constellation-支线展开 */
        private String rhythmTag;
        /** 节奏强度（1-10） */
        private Integer rhythmIntensity;
        /** 叙事视角：主角|配角|反派|旁观者 */
        private String perspective;
        /** 叙事模式：线性|倒叙|插叙|多线并行 */
        private String narrativeMode;
        /** 伏笔计划 */
        private CluePlan cluePlan;
        /** 冲突类型 */
        private String conflictType;
        /** 结局类型 */
        private String outcomeType;
        /** 核心事件（20字内） */
        private String keyEvent;
        /** 章尾钩子方向（10字内） */
        private String hookDirection;
    }

    /**
     * 伏笔计划
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CluePlan {
        /** 本章需埋设的伏笔 */
        private List<String> bury;
        /** 本章需回收的伏笔 */
        private List<String> recycle;
    }
}
