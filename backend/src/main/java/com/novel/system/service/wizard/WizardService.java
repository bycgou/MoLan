package com.novel.system.service.wizard;

import com.novel.system.dto.request.WizardSaveRequest;
import com.novel.system.dto.request.WizardSettingsRequest;
import com.novel.system.dto.response.*;

import java.util.List;

/**
 * 向导服务接口 - 管理小说创建向导工作流
 */
public interface WizardService {

    /**
     * Step 1: 根据设定生成卷纲
     */
    List<ArcOutlineDTO> generateVolumeOutlines(WizardSettingsRequest request);

    /**
     * Step 3: 保存小说 + 卷纲到数据库
     */
    NovelResponse saveNovelWithArcs(WizardSaveRequest request, String userId);

    /**
     * Step 4: 生成指定卷的所有章纲
     */
    List<ArcChapterOutlineDTO> generateArcChapterOutlines(String novelId, int arcIndex);

    /**
     * Step 4: 确认/编辑章纲并保存到数据库
     */
    void confirmChapterOutlines(String novelId, int arcIndex, List<ArcChapterOutlineDTO> outlines);

    /**
     * Step 5: 批量生成所有章节正文（异步）
     */
    String batchGenerateChapters(String novelId);

    /**
     * Step 5: 查询批量生成进度
     */
    BatchGenerationProgress getBatchProgress(String novelId, String taskId);

    /**
     * 取消批量生成任务
     */
    void cancelBatchGeneration(String novelId, String taskId);
}
