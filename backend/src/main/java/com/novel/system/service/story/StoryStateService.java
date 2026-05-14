package com.novel.system.service.story;

import com.novel.system.entity.mongo.StoryState;

import java.util.List;

/**
 * 故事状态服务接口
 */
public interface StoryStateService {

    /**
     * 获取小说的故事状态，如果不存在则创建默认状态
     */
    StoryState getOrCreate(String novelId);

    /**
     * 保存/更新故事状态
     */
    StoryState save(StoryState storyState);

    /**
     * 章节生成后更新故事状态
     * @param novelId 小说ID
     * @param chapterNum 章节号
     * @param chapterSummary 本章摘要
     * @param chapterContent 本章正文（用于LLM提取状态变化）
     */
    StoryState updateAfterChapter(String novelId, int chapterNum, String chapterSummary, String chapterContent);

    /**
     * 格式化为 Prompt 注入文本（约500-800字）
     */
    String formatForPrompt(StoryState storyState);

    /**
     * 更新时间线信息
     */
    void updateTimeline(String novelId, String worldDate, String season, String timeOfDay);

    /**
     * 程序化兜底：更新主角位置
     */
    void updateProtagonistLocation(String novelId, String location);

    /**
     * 程序化兜底：更新主角实力等级
     */
    void updateProtagonistPowerLevel(String novelId, String powerLevel);

    /**
     * 程序化兜底：追加主角物品
     */
    void updateProtagonistInventory(String novelId, List<String> items);

    /**
     * 写入校验警告，供下章生成时参考
     */
    void addWarnings(String novelId, List<String> warnings);

    /**
     * 清除校验警告（已注入上下文后调用）
     */
    void clearWarnings(String novelId);
}
