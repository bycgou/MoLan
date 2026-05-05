package com.novel.system.dto.style;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 文风检查结果DTO
 * 
 * @see com.novel.system.service.style.StyleCheckerService
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StyleCheckResult {
    
    /**
     * 风格匹配分数（0-100）
     */
    private double score;
    
    /**
     * 是否通过（>=70分）
     */
    private boolean pass;
    
    /**
     * 发现的问题列表
     */
    private List<String> issues;
    
    /**
     * 修改建议（深度检查时提供）
     */
    private List<String> suggestions;
    
    /**
     * 写得最像原文风格的段落
     */
    private String bestMatchParagraph;
    
    /**
     * 偏离最大的段落
     */
    private String worstMatchParagraph;
    
    /**
     * 检查类型：quick（快速检查）/ deep（深度检查）
     */
    private String checkType;
}
