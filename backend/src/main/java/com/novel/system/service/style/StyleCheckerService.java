package com.novel.system.service.style;

import com.novel.system.dto.style.StyleCardAnalysis;
import com.novel.system.dto.style.StyleCheckResult;
import com.novel.system.dto.style.StyleMetrics;
import com.novel.system.entity.mysql.StyleCard;
import com.novel.system.mapper.StyleCardMapper;
import com.novel.system.service.llm.LlmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 文风质量检查服务
 * 
 * 【核心作用】
 * 检查生成的文本是否符合目标文风，提供质量评分和改进建议
 * 
 * 【两种检查模式】
 * 1. 快速检查（quickCheck）：零LLM成本，检查量化指标
 * 2. 深度检查（deepCheck）：调用LLM，提供详细分析
 * 
 * @see StyleCard
 * @see StyleCheckResult
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StyleCheckerService {
    
    private final StyleCardMapper styleCardMapper;
    private final StyleTextAnalyzer textAnalyzer;
    private final LlmService llmService;
    
    // AI套话黑名单
    private static final String[] AI_CLICHES = {
        "不禁", "似乎", "仿佛", "眼神中闪过", "嘴角微微上扬",
        "心中涌起一股", "一种...的感觉", "空气中弥漫着", "不约而同",
        "若有所思", "意味深长", "难以名状", "若隐若现", "一股暖流",
        "与此同时", "话音刚落", "沉默片刻", "微微一怔"
    };
    
    /**
     * 快速检查（零LLM成本）
     * 
     * @param generatedText 生成的文本
     * @param cardId 文风卡片ID
     * @return 检查结果
     */
    public StyleCheckResult quickCheck(String generatedText, String cardId) {
        StyleCard card = styleCardMapper.selectById(cardId);
        if (card == null) {
            return StyleCheckResult.builder()
                    .score(0)
                    .pass(false)
                    .issues(List.of("文风卡片不存在"))
                    .checkType("quick")
                    .build();
        }
        
        return quickCheck(generatedText, card);
    }
    
    /**
     * 快速检查（零LLM成本）
     * 
     * @param generatedText 生成的文本
     * @param card 文风卡片
     * @return 检查结果
     */
    public StyleCheckResult quickCheck(String generatedText, StyleCard card) {
        StyleMetrics genMetrics = textAnalyzer.analyze(generatedText);
        List<String> issues = new ArrayList<>();
        double score = 100.0;
        
        // 检查AI套话
        for (String cliche : AI_CLICHES) {
            if (generatedText.contains(cliche)) {
                issues.add("出现AI套话：" + cliche);
                score -= 8;
            }
        }
        
        // 检查句长偏离（如果有统计数据）
        if (card.getStatsJson() != null) {
            try {
                // 简单检查：短句占比
                if (genMetrics.getShortSentenceRatio() < 0.15) {
                    issues.add("短句太少，缺乏节奏变化");
                    score -= 10;
                }
            } catch (Exception e) {
                log.warn("解析统计数据失败: {}", e.getMessage());
            }
        }
        
        // 检查对话比例
        if (genMetrics.getDialogueRatio() < 0.05) {
            issues.add("对话比例过低（" + String.format("%.1f%%", genMetrics.getDialogueRatio() * 100) + "）");
            score -= 10;
        }
        
        // 检查段落节奏
        if (genMetrics.getShortParagraphRatio() < 0.1) {
            issues.add("短段落太少，缺乏节奏变化");
            score -= 10;
        }
        
        return StyleCheckResult.builder()
                .score(Math.max(0, score))
                .pass(score >= 70)
                .issues(issues)
                .checkType("quick")
                .build();
    }
    
    /**
     * 深度检查（调用LLM）
     * 
     * @param generatedText 生成的文本
     * @param cardId 文风卡片ID
     * @return 检查结果
     */
    public StyleCheckResult deepCheck(String generatedText, String cardId) {
        StyleCard card = styleCardMapper.selectById(cardId);
        if (card == null) {
            return StyleCheckResult.builder()
                    .score(0)
                    .pass(false)
                    .issues(List.of("文风卡片不存在"))
                    .checkType("deep")
                    .build();
        }
        
        return deepCheck(generatedText, card);
    }
    
    /**
     * 深度检查（调用LLM）
     * 
     * @param generatedText 生成的文本
     * @param card 文风卡片
     * @return 检查结果
     */
    public StyleCheckResult deepCheck(String generatedText, StyleCard card) {
        // 截取生成文本避免超长
        String truncatedText = generatedText;
        if (truncatedText.length() > 3000) {
            truncatedText = truncatedText.substring(0, 1500) + "\n...(中间省略)...\n" 
                    + truncatedText.substring(truncatedText.length() - 1500);
        }
        
        String prompt = """
                以下是AI生成的小说片段和目标风格要求。请检查风格匹配度。
                
                目标风格：%s
                
                生成文本：
                %s
                
                检查并返回JSON：
                {
                  "score": 0-100,
                  "issues": ["具体问题描述"],
                  "suggestions": ["修改建议"],
                  "best_match_paragraph": "写得最像的段落（截取50字左右）",
                  "worst_match_paragraph": "偏离最大的段落（截取50字左右）"
                }
                
                重要：只返回JSON，不要有其他内容。
                """.formatted(
                card.getStyleDescription() != null ? card.getStyleDescription() : "无特定风格要求",
                truncatedText
                );
        
        try {
            String result = llmService.generate(prompt, "audit");
            return parseDeepCheckResult(result);
        } catch (Exception e) {
            log.error("深度检查失败: {}", e.getMessage());
            // 降级为快速检查
            StyleCheckResult quickResult = quickCheck(generatedText, card);
            quickResult.setCheckType("deep-fallback");
            return quickResult;
        }
    }
    
    /**
     * 解析深度检查结果
     */
    private StyleCheckResult parseDeepCheckResult(String result) {
        try {
            // 尝试提取JSON部分
            String json = result;
            if (result.contains("```json")) {
                json = result.substring(result.indexOf("```json") + 7, result.indexOf("```", result.indexOf("```json") + 7));
            } else if (result.contains("{")) {
                json = result.substring(result.indexOf("{"), result.lastIndexOf("}") + 1);
            }
            
            // 使用Jackson解析
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(json, StyleCheckResult.class);
        } catch (Exception e) {
            log.warn("解析深度检查结果失败: {}", e.getMessage());
            return StyleCheckResult.builder()
                    .score(50)
                    .pass(false)
                    .issues(List.of("检查结果解析失败"))
                    .checkType("deep-error")
                    .build();
        }
    }
    
    /**
     * 自动修复建议
     * 
     * @param generatedText 生成的文本
     * @param cardId 文风卡片ID
     * @return 修复后的文本
     */
    public String suggestFix(String generatedText, String cardId) {
        StyleCard card = styleCardMapper.selectById(cardId);
        if (card == null) {
            return generatedText;
        }
        
        // 先做快速检查
        StyleCheckResult checkResult = quickCheck(generatedText, card);
        if (checkResult.isPass()) {
            return generatedText;
        }
        
        // 构建修复提示
        StringBuilder issuesStr = new StringBuilder();
        for (String issue : checkResult.getIssues()) {
            issuesStr.append("- ").append(issue).append("\n");
        }
        
        String prompt = """
                以下是AI生成的小说片段，存在以下问题需要修复：
                
                %s
                
                原文：
                %s
                
                目标风格：%s
                
                请修复上述问题，保持原文情节不变，只修改表达方式。
                直接返回修复后的文本，不要有其他内容。
                """.formatted(issuesStr, generatedText, 
                        card.getStyleDescription() != null ? card.getStyleDescription() : "自然流畅的表达");
        
        try {
            return llmService.generate(prompt, "audit");
        } catch (Exception e) {
            log.error("自动修复失败: {}", e.getMessage());
            return generatedText;
        }
    }
}
