package com.novel.system.dto.response;

import lombok.Data;

/**
 * 批量生成进度DTO - 用于轮询批量章节生成进度
 */
@Data
public class BatchGenerationProgress {
    private String taskId;
    private String status;          // GENERATING, COMPLETED, FAILED, CANCELLED
    private Integer totalChapters;
    private Integer completedChapters;
    private Integer failedChapters = 0;
    private Integer currentChapterNum;
    private String currentPhase;    // "outlining" or "writing"
    private String errorMessage;
}
