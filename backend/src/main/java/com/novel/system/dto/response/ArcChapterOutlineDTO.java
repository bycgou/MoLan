package com.novel.system.dto.response;

import lombok.Data;
import java.util.List;

/**
 * 卷内章纲DTO - 用于向导流程中传输单章章纲数据
 */
@Data
public class ArcChapterOutlineDTO {
    private Integer chapterNum;
    private String chapterTitle;
    private String outline;
    private List<String> implementationIdeas;
}
