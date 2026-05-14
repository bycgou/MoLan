package com.novel.system.service.orchestration;

import com.novel.system.entity.mongo.ArcOrchestration;

/**
 * 卷级编排服务接口
 *
 * 负责为指定卷生成章节级编排计划，包括节奏分布、伏笔埋收、视角交替等。
 * 编排结果存储在MongoDB中，章纲生成时通过ContextBuilderService注入。
 */
public interface ArcOrchestrationService {

    /**
     * 为指定卷生成编排计划
     *
     * @param novelId 小说ID
     * @param arcId 卷ID
     * @param llmConfigId 模型配置ID
     * @return 编排结果
     */
    ArcOrchestration orchestrate(String novelId, String arcId, String llmConfigId);

    /**
     * 获取指定章节的编排约束
     *
     * @param novelId 小说ID
     * @param chapterNum 章节号
     * @return 章节编排计划，如果不存在则返回null
     */
    ArcOrchestration.ChapterPlan getChapterPlan(String novelId, int chapterNum);

    /**
     * 使编排失效（当用户修改卷纲时调用）
     *
     * @param arcId 卷ID
     */
    void invalidate(String arcId);
}
