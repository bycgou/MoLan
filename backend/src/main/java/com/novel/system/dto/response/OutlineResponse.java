package com.novel.system.dto.response;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;

/**
 * 章纲响应DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OutlineResponse {
    
    /** 小说ID */
    private String novelId;
    /** 章节序号 */
    private Integer chapterNum;
    /** 章节大纲内容 */
    private String outline;
    /** 实现思路列表（AI生成的多个写作方向） */
    private List<String> implementationIdeas;
}
