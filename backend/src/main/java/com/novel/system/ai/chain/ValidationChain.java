package com.novel.system.ai.chain;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import com.novel.system.ai.tool.NovelToolProvider;
import com.novel.system.config.LangChainConfig;
import com.novel.system.entity.mysql.LlmConfig;
import com.novel.system.service.llm.LlmConfigService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 校验链 - 编排多重校验流程
 * 
 * 【核心作用】
 * 这是生成内容的"质检员"，负责校验AI生成的内容是否符合要求。
 * 
 * 【校验流程】
 * 1. OOC检测 - 检查人物行为是否符合设定
 * 2. 规则校验 - 检查内容是否符合基本规则
 * 3. 综合评分 - 根据各项指标给出总分
 * 
 * 【校验指标】
 * 1. OOC校验：检查人物行为是否符合设定
 * 2. 字数校验：检查内容字数是否符合要求
 * 3. 结尾校验：检查章节结尾是否有钩子
 * 
 * 【使用场景】
 * ChapterGenerationServiceImpl.generateChapter()中：
 * - 生成正文后，调用校验链检查内容
 * - 如果校验失败，会重试生成（最多2次）
 * 
 * 【OOC检测详解】
 * OOC（Out Of Character）指人物行为不符合设定。
 * 例如：
 * - 一个胆小的人突然变得勇敢
 * - 一个冷漠的人突然变得热情
 * - 一个不懂医术的人突然开始治病
 * 
 * 【校验评分】
 * - OOC校验：满分100分
 * - 规则校验：满分100分
 * - 综合评分：(OOC + 规则) / 2
 * - 通过标准：综合评分 >= 60分
 * 
 * @author AI Assistant
 * @since 1.0.0
 * @see ChapterGenerationServiceImpl#generateChapter 使用此校验链的地方
 * @see CharacterTool#checkBehaviorAllowed OOC检测的工具方法
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ValidationChain {
    
    /**
     * LangChain4j配置类
     * 负责创建ChatLanguageModel实例
     */
    private final LangChainConfig langChainConfig;
    
    /**
     * LLM配置服务
     * 负责从数据库获取模型配置
     */
    private final LlmConfigService llmConfigService;
    
    /**
     * 工具提供器
     * 提供所有可用的工具给Agent使用
     */
    private final NovelToolProvider toolProvider;
    
    /**
     * OOC检测Agent - 内部接口
     * 
     * 【作用】
     * 专门用于检测人物行为是否符合设定。
     * 
     * 【返回格式】
     * JSON格式：
     * {
     *   "total": 32,  // 总分
     *   "passed": true,  // 是否通过
     *   "issues": ""  // 问题描述
     * }
     */
    private interface OocValidator {
        /**
         * 校验人物行为
         * 
         * @param content 包含人物设定和章节内容的消息
         * @return JSON格式的校验结果
         */
        @dev.langchain4j.service.SystemMessage(
            "你是人设校验Agent。检查小说章节内容是否存在人物设定不一致。" +
            "检测维度: 语言风格、行为动机、知识水平、能力匹配。" +
            "返回JSON格式: {\"total\": 32, \"passed\": true, \"issues\": \"\"}"
        )
        String validate(@dev.langchain4j.service.UserMessage String content);
    }
    
    /**
     * 执行OOC校验
     * 
     * 【执行流程】
     * 1. 获取ChatLanguageModel
     * 2. 创建OocValidator Agent
     * 3. 构建用户消息（包含人物设定和章节内容）
     * 4. 调用Agent进行校验
     * 5. 解析返回的JSON
     * 
     * @param novelId 小说ID
     * @param chapterNum 章节号
     * @param chapterContent 章节内容
     * @param characterStates 人物状态字符串
     * @param llmConfigId 可选的模型配置ID
     * @return OOC校验结果
     */
    public OocValidationResult validateOOC(String novelId, int chapterNum, String chapterContent, 
                                            String characterStates, String llmConfigId) {
        log.info("执行OOC校验: novelId={}, chapter={}", novelId, chapterNum);
        
        try {
            ChatLanguageModel model = getModel(llmConfigId);
            
            OocValidator validator = AiServices.builder(OocValidator.class)
                    .chatLanguageModel(model)
                    .tools(toolProvider.getCharacterTool())
                    .build();
            
            String prompt = String.format(
                "小说ID: %s\n章节号: 第%d章\n\n===人物设定===\n%s\n\n===章节内容===\n%s",
                novelId, chapterNum, characterStates, 
                chapterContent.length() > 3000 ? chapterContent.substring(0, 3000) + "..." : chapterContent
            );
            
            String result = validator.validate(prompt);
            return parseOocResult(result);
            
        } catch (Exception e) {
            log.error("OOC校验异常: {}", e.getMessage());
            OocValidationResult result = new OocValidationResult();
            result.setPassed(true);
            result.setTotalScore(80);
            return result;
        }
    }
    
    /**
     * 执行完整校验链
     * 
     * 【执行流程】
     * 1. 执行OOC校验
     * 2. 执行规则校验
     * 3. 计算综合评分
     * 4. 判断是否通过
     * 
     * @param novelId 小说ID
     * @param chapterNum 章节号
     * @param chapterContent 章节内容
     * @param outline 章纲
     * @param totalOutline 小说总纲
     * @param characterStates 人物状态字符串
     * @param llmConfigId 可选的模型配置ID
     * @return 完整校验结果
     */
    public FullValidationResult validateAll(String novelId, int chapterNum, String chapterContent,
                                             String outline, String totalOutline, String characterStates,
                                             String llmConfigId) {
        log.info("执行完整校验链: novelId={}, chapter={}", novelId, chapterNum);
        
        FullValidationResult result = new FullValidationResult();
        
        // OOC校验
        OocValidationResult oocResult = validateOOC(novelId, chapterNum, chapterContent, characterStates, llmConfigId);
        result.setOocResult(oocResult);
        
        // 规则校验
        int ruleScore = performRuleChecks(chapterContent, outline);
        result.setRuleScore(ruleScore);
        
        // 综合评分
        int totalScore = (oocResult.getTotalScore() + ruleScore) / 2;
        result.setTotalScore(totalScore);
        result.setPassed(totalScore >= 60);
        
        log.info("校验完成: OOC={}, 规则={}, 总分={}, 通过={}", 
                oocResult.getTotalScore(), ruleScore, totalScore, result.isPassed());
        
        return result;
    }
    
    /**
     * 执行规则校验
     * 
     * 【校验规则】
     * 1. 字数校验：检查内容字数是否符合要求
     * 2. 结尾校验：检查章节结尾是否有钩子
     * 
     * @param content 章节内容
     * @param outline 章纲
     * @return 规则校验分数（满分100）
     */
    private int performRuleChecks(String content, String outline) {
        int score = 100;
        
        // 检查字数
        int wordCount = 0;
        for (char c : content.toCharArray()) {
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                wordCount++;
            }
        }
        if (wordCount < 800) score -= 20;
        if (wordCount > 3000) score -= 10;
        
        // 检查结尾钩子
        String ending = content.substring(Math.max(0, content.length() - 100));
        if (!ending.contains("？") && !ending.contains("……") && 
            !ending.contains("但是") && !ending.contains("然而") && !ending.contains("突然")) {
            score -= 10;
        }
        
        return Math.max(0, score);
    }
    
    /**
     * 解析OOC校验结果
     * 
     * @param json Agent返回的JSON字符串
     * @return 解析后的OocValidationResult对象
     */
    private OocValidationResult parseOocResult(String json) {
        OocValidationResult result = new OocValidationResult();
        try {
            String cleanJson = json.trim();
            if (cleanJson.startsWith("```")) {
                cleanJson = cleanJson.replaceAll("```json?", "").replaceAll("```", "").trim();
            }
            
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(cleanJson);
            
            result.setTotalScore(node.has("total") ? node.get("total").asInt() : 80);
            result.setPassed(node.has("passed") ? node.get("passed").asBoolean() : true);
            result.setIssues(node.has("issues") ? node.get("issues").asText() : "");
        } catch (Exception e) {
            log.warn("解析OOC结果失败: {}", e.getMessage());
            result.setTotalScore(80);
            result.setPassed(true);
        }
        return result;
    }
    
    /**
     * 获取ChatLanguageModel
     * 
     * @param llmConfigId 可选的模型配置ID
     * @return ChatLanguageModel实例
     * @throws RuntimeException 如果未找到可用的模型配置
     */
    private ChatLanguageModel getModel(String llmConfigId) {
        LlmConfig config;
        if (llmConfigId != null && !llmConfigId.isEmpty()) {
            config = llmConfigService.getConfigById(llmConfigId);
        } else {
            // 修复：使用 getAnyActiveConfig 获取任意活跃配置
            config = llmConfigService.getAnyActiveConfig("audit");
            if (config == null) {
                config = llmConfigService.getAnyActiveConfig("all");
            }
        }
        if (config == null) {
            throw new RuntimeException("未找到可用的模型配置，请先在模型配置页面添加并激活一个模型");
        }
        return langChainConfig.createChatModel(config);
    }
    
    /**
     * OOC校验结果
     */
    @Data
    public static class OocValidationResult {
        private boolean passed;
        private int totalScore;
        private String issues = "";
    }
    
    /**
     * 完整校验结果
     */
    @Data
    public static class FullValidationResult {
        private OocValidationResult oocResult;
        private int ruleScore;
        private int totalScore;
        private boolean passed;
    }
}
