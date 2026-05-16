package com.novel.system.ai.dto;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;
import java.util.List;

/**
 * LLM 输出的卷编排 DTO（不含 MongoDB 持久化字段）
 */
@Data
public class ArcOrchestrationOutput {

    @Description("卷名称")
    private String arcName;

    @Description("本卷整体节奏描述，不超过50字")
    private String overallRhythm;

    @Description("高潮章节号数组，元素为章节序号整数")
    private List<Integer> climaxChapters;

    @Description("各章节编排计划")
    private List<ChapterPlanOutput> chapters;

    @Data
    public static class ChapterPlanOutput {
        @Description("章节序号，从1开始的整数")
        private Integer chapterNum;
        @Description("节奏标签，只能是 quest(任务驱动)、fire(冲突爆发)、constellation(多线交织) 之一")
        private String rhythmTag;
        @Description("节奏强度，1到10的整数")
        private Integer rhythmIntensity;
        @Description("叙事视角，只能是 主角、配角、反派、旁观者 之一")
        private String perspective;
        @Description("叙事模式，只能是 线性、倒叙、插叙、多线并行 之一")
        private String narrativeMode;
        @Description("伏笔计划")
        private CluePlanOutput cluePlan;
        @Description("冲突类型，只能是 外部战斗、内心挣扎、人际矛盾、道德困境、信息不对称 之一")
        private String conflictType;
        @Description("结局类型，只能是 险胜、惨胜、平局、撤退、得而复失、敌退、平淡过渡 之一")
        private String outcomeType;
        @Description("本章必须发生的核心事件，不超过20字")
        private String keyEvent;
        @Description("章尾钩子方向，不超过10字")
        private String hookDirection;
    }

    @Data
    public static class CluePlanOutput {
        @Description("本章需埋设的伏笔描述字符串数组")
        private List<String> bury;
        @Description("本章需回收的伏笔描述字符串数组")
        private List<String> recycle;
    }
}
