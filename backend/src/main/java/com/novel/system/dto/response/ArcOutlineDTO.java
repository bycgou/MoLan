package com.novel.system.dto.response;

import lombok.Data;

/**
 * 卷纲DTO - 用于向导流程中传输卷纲数据
 */
@Data
public class ArcOutlineDTO {
    private String arcName;
    private String arcOutline;
    private Integer startChapter;
    private Integer endChapter;
    private Integer chapterCount;
    private String rhythmTag;
    private Integer rhythmIntensity;
}
