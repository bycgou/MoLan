package com.novel.system.dto.response;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

/**
 * 小说信息响应DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NovelResponse {
    
    /** 小说ID */
    private String novelId;
    /** 小说名称 */
    private String novelName;
    /** 小说题材 */
    private String genre;
    /** 小说类型标签 */
    private String novelType;
    /** 故事基调 */
    private String tone;
    /** 禁止雷点 */
    private String tabooList;
    /** 小说总纲 */
    private String totalOutline;
    /** 世界观设定 */
    private String worldSetting;
    /** 总字数 */
    private Integer wordCount;
    /** 总章节数 */
    private Integer chapterCount;
    /** 状态：0-草稿，1-连载中，2-已完结 */
    private Integer status;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
}
