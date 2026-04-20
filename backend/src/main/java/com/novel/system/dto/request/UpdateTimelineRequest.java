package com.novel.system.dto.request;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新世界时间线请求DTO
 * 仅暴露允许修改的字段，防止越权修改 clockId、novelId 等敏感字段
 */
@Data
@NoArgsConstructor
public class UpdateTimelineRequest {

    /** 当前时间描述（如：子时、黄昏） */
    private String currentTime;

    /** 当前日期描述（如：天元三年五月初五） */
    private String currentDate;

    /** 当前季节 */
    private String season;

    /** 当前天气 */
    private String weather;

    /** 当前章节对应的天数 */
    private Integer chapterDay;

    /** 小说世界总天数 */
    private Integer totalDays;
}
