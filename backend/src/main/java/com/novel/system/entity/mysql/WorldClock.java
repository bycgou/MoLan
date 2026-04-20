package com.novel.system.entity.mysql;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 世界状态时钟实体
 */
@Data
public class WorldClock {
    /** 时钟ID，主键 */
    private String clockId;
    /** 所属小说ID */
    private String novelId;
    /** 当前时间描述（如：子时、黄昏） */
    private String currentTime;
    /** 当前日期描述（如：天元三年五月初五） */
    private String currentDate;
    /** 当前季节 */
    private String season;
    /** 当前天气 */
    private String weather;
    /** 当前章节对应的天数 */
    private Integer chapterDay = 0;
    /** 小说世界总天数 */
    private Integer totalDays = 0;
    /** 最后更新该时钟的章节号 */
    private Integer updateChapter = 0;
    /** 更新时间 */
    private LocalDateTime updateTime;
}
