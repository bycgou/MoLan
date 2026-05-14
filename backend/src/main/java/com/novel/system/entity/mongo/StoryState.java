package com.novel.system.entity.mongo;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 故事状态文档 (MongoDB)
 *
 * 记录小说的实时状态，是记忆系统的核心枢纽。
 * 每生成一章后自动更新，每次生成章节时全量注入 Prompt。
 */
@Document(collection = "story_state")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StoryState {

    @Id
    private String id;

    /** 小说ID（唯一索引） */
    @Indexed(unique = true)
    private String novelId;

    /** 版本号，每次更新递增 */
    private Integer version;

    /** 最后更新的章节号 */
    private Integer lastChapter;

    /** 时间线状态 */
    private TimelineState timeline;

    /** 主角状态 */
    private ProtagonistState protagonist;

    /** 场景上下文 */
    private SceneContextState sceneContext;

    /** 节奏状态 */
    private PacingState pacingState;

    /** 近期重大事件（最多保留10条） */
    private List<MajorEvent> recentMajorEvents;

    /** 叙事标记 */
    private NarrativeFlagsState narrativeFlags;

    /** 压缩后的全局背景摘要（替代NovelMemory） */
    private String compressedBackstory;

    /** 上次压缩背景的章节号 */
    private Integer lastCompressChapter;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    // ========== 内部类定义 ==========

    /**
     * 时间线状态
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimelineState {
        /** 故事内日期（如：天元历三年七月初十） */
        private String worldDate;
        /** 已过去的天数 */
        private Integer elapsedDays;
        /** 季节 */
        private String season;
        /** 一天中的时间段（如：黄昏、深夜） */
        private String timeOfDay;
    }

    /**
     * 主角状态
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProtagonistState {
        /** 主角姓名 */
        private String name;
        /** 当前实力等级（如：凝真高阶） */
        private String powerLevel;
        /** 当前位置 */
        private String location;
        /** 当前情绪 */
        private String mood;
        /** 身体状况（如：右手经脉受损） */
        private String physicalCondition;
        /** 当前持有的关键物品 */
        private List<String> inventory;
        /** 当前目标 */
        private String currentGoal;
    }

    /**
     * 场景上下文
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SceneContextState {
        /** 当前场景活跃角色 */
        private List<ActiveCharacter> activeCharacters;
        /** 立即威胁（如：敌人追兵） */
        private String immediateThreat;
        /** 上一个重大事件 */
        private String lastMajorEvent;
    }

    /**
     * 活跃角色
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActiveCharacter {
        /** 角色ID */
        private String id;
        /** 角色名称 */
        private String name;
        /** 角色当前状态描述 */
        private String state;
    }

    /**
     * 节奏状态
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PacingState {
        /** 当前紧张度（1-10） */
        private Integer tensionLevel;
        /** 距离上次高潮的章节数 */
        private Integer chaptersSinceLastClimax;
        /** 距离上次休息/缓冲的章节数 */
        private Integer chaptersSinceLastRest;
        /** 节奏建议（如：需要1-2章缓冲过渡） */
        private String recommendation;
    }

    /**
     * 重大事件
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MajorEvent {
        /** 事件发生的章节号 */
        private Integer chapter;
        /** 事件描述 */
        private String event;
    }

    /**
     * 叙事标记
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NarrativeFlagsState {
        /** 当前叙事视角（如：林辰） */
        private String currentPOV;
        /** 未解决的悬念列表 */
        private List<String> unresolvedCliffhangers;
        /** 近期揭露的秘密/信息 */
        private List<String> recentReveals;
        /** 校验警告列表（下章生成时注入，避免重犯） */
        private List<String> warnings;
    }
}
