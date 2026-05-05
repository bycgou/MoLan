package com.novel.system.ai.chain;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import com.novel.system.ai.agent.*;
import com.novel.system.ai.tool.NovelToolProvider;
import com.novel.system.config.LangChainConfig;
import com.novel.system.entity.mysql.LlmConfig;
import com.novel.system.service.llm.LlmConfigService;
import com.novel.system.service.chapter.ModelFallbackService;
import com.novel.system.service.style.StyleCardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 章节生成链 - 使用LangChain4j编排章节生成流程
 * 
 * 【核心作用】
 * 这是整个AI生成系统的"指挥中心"，负责编排三个Agent的执行流程：
 * 1. OutlineAgent（大纲Agent）- 生成章纲
 * 2. WritingAgent（写作Agent）- 生成正文
 * 3. SummaryAgent（摘要Agent）- 生成摘要
 * 
 * 【架构设计】
 * 采用"链式调用"模式，每个Agent负责一个独立的任务：
 * ```
 * 用户请求 → OutlineAgent → WritingAgent → SummaryAgent → 返回结果
 * ```
 * 
 * 【与旧实现的区别】
 * - 原来：手动构建prompt → 调用LlmService → 手动解析响应
 * - 现在：使用LangChain4j AI Services，Agent自动调用工具、自动解析
 * 
 * 【工具集成】
 * 每个Agent都可以使用工具查询小说数据：
 * - CharacterTool：查询人物信息
 * - ClueTool：查询伏笔信息
 * - NovelTool：查询小说基本信息
 * 
 * 【使用示例】
 * <pre>
 * // 生成章纲
 * String outlineJson = chapterGenerationChain.executeOutlineChain(
 *     novelId, chapterNum, contextPrompt, llmConfigId);
 * 
 * // 生成正文
 * String chapterContent = chapterGenerationChain.executeWritingChain(
 *     novelId, chapterNum, outline, contextPrompt, llmConfigId);
 * 
 * // 生成摘要
 * String summaryJson = chapterGenerationChain.executeSummaryChain(
 *     novelId, chapterNum, chapterContent, outline, llmConfigId);
 * </pre>
 * 
 * @author AI Assistant
 * @since 1.0.0
 * @see OutlineAgent 章纲生成Agent
 * @see WritingAgent 正文生成Agent
 * @see SummaryAgent 摘要生成Agent
 * @see NovelToolProvider 工具提供器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChapterGenerationChain {
    
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
     * 模型 Fallback 服务
     * 主模型失败时自动切换备用模型
     */
    private final ModelFallbackService modelFallbackService;
    
    /**
     * 文风卡片服务
     * 用于获取文风注入内容
     */
    private final StyleCardService styleCardService;
    
    /**
     * 摘要Agent - 内部接口
     * 
     * 【注意】
     * 这是一个内部接口，不需要工具，所以没有使用@Tool注解
     * 只需要生成JSON格式的摘要响应
     */
    private interface SummaryAgentAiService {
        /**
         * 生成摘要
         * 
         * @param userPrompt 包含章节内容的消息
         * @return JSON格式的摘要响应
         */
        @dev.langchain4j.service.SystemMessage(
            "你是专业的小说摘要Agent。从章节内容中提取结构化摘要。" +
            "输出JSON: {\"strand_type\":\"quest\",\"fingerprint\":\"一句话描述\",\"core_events\":[],\"hook_level\":\"中\"}"
        )
        String generateSummary(@dev.langchain4j.service.UserMessage String userPrompt);
    }
    
    /**
     * 执行章纲生成链
     * 
     * 【执行流程】
     * 1. 获取ChatLanguageModel（根据llmConfigId或默认配置）
     * 2. 创建带工具的OutlineAgent
     * 3. 构建用户消息（包含上下文信息）
     * 4. 调用Agent生成章纲
     * 5. 返回JSON格式的章纲
     * 
     * 【上下文信息】
     * 包含以下内容：
     * - 小说总纲
     * - 卷信息（卷名、卷纲、章节范围）
     * - 世界观设定
     * - 世界状态（时间、季节、天气）
     * - 人物状态
     * - 上一章摘要
     * - 用户意图
     * - 未回收伏笔
     * 
     * @param novelId 小说ID
     * @param chapterNum 章节号
     * @param contextPrompt 上下文提示词（包含总纲、卷纲、人物状态等）
     * @param llmConfigId 可选的模型配置ID，如果为空则使用默认配置
     * @return 生成的章纲JSON字符串
     * @throws RuntimeException 如果模型配置不存在或生成失败
     * 
     * @see OutlineAgent 章纲Agent的接口定义
     * @see ChapterGenerationServiceImpl#buildOutlineContext 构建上下文的方法
     */
    public String executeOutlineChain(String novelId, int chapterNum, String contextPrompt, String llmConfigId) {
        log.info("执行章纲生成链: novelId={}, chapter={}", novelId, chapterNum);
        
        // 获取 LlmConfig
        LlmConfig llmConfig = llmConfigService.getConfigById(llmConfigId);
        
        // 使用 Fallback 机制执行
        return modelFallbackService.executeWithFallback(llmConfigId, model -> {
            // 创建带工具的OutlineAgent，使用 STRUCTURED 任务类型
            OutlineAgent outlineAgent = AiServices.builder(OutlineAgent.class)
                    .chatLanguageModel(langChainConfig.createChatModel(llmConfig, LangChainConfig.TaskType.STRUCTURED))
                    .tools(toolProvider.getAllTools())
                    .build();
            
            // 构建用户消息
            String userPrompt = String.format(
                "小说ID: %s\\n章节号: 第%d章\\n\\n===上下文信息===\\n%s\\n\\n请生成第%d章的章纲。",
                novelId, chapterNum, contextPrompt, chapterNum
            );
            
            // 调用Agent生成章纲
            return outlineAgent.generateOutline(userPrompt);
        });
    }
    
    /**
     * 执行写作链
     * 
     * 【执行流程】
     * 1. 获取ChatLanguageModel
     * 2. 创建带工具的WritingAgent
     * 3. 构建用户消息（包含章纲和上下文信息）
     * 4. 调用Agent生成正文
     * 5. 返回生成的正文内容
     * 
     * 【写作要求】
     * - 语言流畅，描写生动
     * - 人物对话自然
     * - 场景描写具体
     * - 情节发展合理
     * - 避免重复描写
     * - 控制节奏，张弛有度
     * 
     * @param novelId 小说ID
     * @param chapterNum 章节号
     * @param outline 章纲内容（由OutlineAgent生成）
     * @param contextPrompt 上下文提示词
     * @param llmConfigId 可选的模型配置ID
     * @return 生成的小说正文
     * @throws RuntimeException 如果模型配置不存在或生成失败
     * 
     * @see WritingAgent 写作Agent的接口定义
     */
    public String executeWritingChain(String novelId, int chapterNum, String outline, 
                                      String contextPrompt, String llmConfigId) {
        log.info("执行写作生成链: novelId={}, chapter={}", novelId, chapterNum);
        
        // 获取 LlmConfig
        LlmConfig llmConfig = llmConfigService.getConfigById(llmConfigId);
        
        // 使用 Fallback 机制执行
        return modelFallbackService.executeWithFallback(llmConfigId, model -> {
            // 创建带工具的WritingAgent，使用 CREATIVE 任务类型
            WritingAgent writingAgent = AiServices.builder(WritingAgent.class)
                    .chatLanguageModel(langChainConfig.createChatModel(llmConfig, LangChainConfig.TaskType.CREATIVE))
                    .tools(toolProvider.getAllTools())
                    .build();
            
            // 构建用户消息
            String userPrompt = String.format(
                "小说ID: %s\\n章节号: 第%d章\\n\\n===章纲===\\n%s\\n\\n===上下文信息===\\n%s\\n\\n请根据章纲生成第%d章正文。",
                novelId, chapterNum, outline, contextPrompt, chapterNum
            );
            
            // 调用Agent生成正文
            return writingAgent.generateChapter(userPrompt);
        });
    }
    
    /**
     * 执行摘要链
     * 
     * 【执行流程】
     * 1. 获取ChatLanguageModel
     * 2. 创建摘要Agent（不需要工具）
     * 3. 构建用户消息（包含章节内容）
     * 4. 调用Agent生成摘要
     * 5. 返回JSON格式的摘要
     * 
     * 【摘要内容】
     * - fingerprint：一句话剧情指纹
     * - core_events：核心事件列表
     * - hook_level：钩子强度
     * 
     * @param novelId 小说ID
     * @param chapterNum 章节号
     * @param chapterContent 章节正文内容
     * @param outline 章纲（可选）
     * @param llmConfigId 可选的模型配置ID
     * @return 生成的摘要JSON字符串
     * @throws RuntimeException 如果模型配置不存在或生成失败
     * 
     * @see SummaryAgentAiService 摘要Agent的内部接口定义
     */
    public String executeSummaryChain(String novelId, int chapterNum, String chapterContent, 
                                      String outline, String llmConfigId) {
        log.info("执行摘要生成链: novelId={}, chapter={}", novelId, chapterNum);
        
        // 获取 LlmConfig
        LlmConfig llmConfig = llmConfigService.getConfigById(llmConfigId);
        
        // 使用 Fallback 机制执行
        return modelFallbackService.executeWithFallback(llmConfigId, model -> {
            // 摘要Agent不需要工具，使用 STRUCTURED 任务类型
            SummaryAgentAiService summaryAgent = AiServices.builder(SummaryAgentAiService.class)
                    .chatLanguageModel(langChainConfig.createChatModel(llmConfig, LangChainConfig.TaskType.STRUCTURED))
                    .build();
            
            // 截取内容避免超长（LLM有上下文窗口限制）
            String truncatedContent = chapterContent;
            if (truncatedContent.length() > 4000) {
                truncatedContent = truncatedContent.substring(0, 2000) + "\n...(中间省略)...\n" 
                    + truncatedContent.substring(truncatedContent.length() - 2000);
            }
            
            // 构建用户消息
            String userPrompt = String.format(
                "章节号: 第%d章\\n\\n===章纲===\\n%s\\n\\n===章节内容===\\n%s",
                chapterNum, outline != null ? outline : "无", truncatedContent
            );
            
            // 调用Agent生成摘要
            return summaryAgent.generateSummary(userPrompt);
        });
    }
    
    /**
     * 执行卷纲生成链
     *
     * 根据小说设定生成全书卷纲结构，返回JSON数组。
     *
     * @param totalOutline 小说总纲
     * @param genre 小说类型
     * @param worldSetting 世界观设定
     * @param prompt 核心创意
     * @param llmConfigId 可选的模型配置ID
     * @return JSON数组格式的卷纲字符串
     */
    public String executeVolumeOutlineChain(String totalOutline, String genre,
                                            String worldSetting, String prompt,
                                            String novelType, String tone, String tabooList,
                                            String llmConfigId) {
        log.info("执行卷纲生成链");

        LlmConfig llmConfig = llmConfigService.getConfigById(llmConfigId);

        return modelFallbackService.executeWithFallback(llmConfigId, model -> {
            VolumeOutlineAgent agent = AiServices.builder(VolumeOutlineAgent.class)
                    .chatLanguageModel(langChainConfig.createChatModel(llmConfig, LangChainConfig.TaskType.STRUCTURED))
                    .build();

            StringBuilder promptBuilder = new StringBuilder();
            promptBuilder.append(String.format("小说题材：%s\n", genre != null ? genre : "未指定"));
            if (novelType != null && !novelType.isEmpty()) {
                promptBuilder.append(String.format("小说类型：%s\n", novelType));
            }
            if (tone != null && !tone.isEmpty()) {
                promptBuilder.append(String.format("故事基调：%s\n", tone));
            }
            if (tabooList != null && !tabooList.isEmpty()) {
                promptBuilder.append(String.format("禁止雷点（绝对不能出现的内容）：%s\n", tabooList));
            }
            promptBuilder.append(String.format("总纲：%s\n世界观设定：%s\n核心创意：%s\n\n请根据以上信息生成全书卷纲。",
                    totalOutline != null ? totalOutline : "无",
                    worldSetting != null ? worldSetting : "无",
                    prompt != null && !prompt.isEmpty() ? prompt : "请自由发挥"));

            return agent.generateVolumeOutlines(promptBuilder.toString());
        });
    }

    /**
     * 执行写作链（支持文风卡片）
     * 
     * 【新增功能】
     * 支持传入styleCardId，自动注入文风参考内容
     * 
     * @param novelId 小说ID
     * @param chapterNum 章节号
     * @param outline 章纲内容
     * @param contextPrompt 上下文提示词
     * @param styleCardId 文风卡片ID（可选）
     * @param llmConfigId 模型配置ID（可选）
     * @return 生成的小说正文
     */
    public String executeWritingChain(String novelId, int chapterNum, String outline, 
                                      String contextPrompt, String styleCardId, String llmConfigId) {
        log.info("执行写作生成链: novelId={}, chapter={}, styleCardId={}", novelId, chapterNum, styleCardId);
        
        // 获取 LlmConfig
        LlmConfig llmConfig = llmConfigService.getConfigById(llmConfigId);
        
        // 构建文风注入内容（final修饰，供lambda使用）
        final String styleInjection;
        if (styleCardId != null && !styleCardId.isEmpty()) {
            String injection = styleCardService.buildStyleInjection(styleCardId);
            if (!injection.isEmpty()) {
                log.info("注入文风卡片: styleCardId={}", styleCardId);
                styleInjection = injection;
            } else {
                styleInjection = "";
            }
        } else {
            styleInjection = "";
        }
        
        // 使用 Fallback 机制执行
        return modelFallbackService.executeWithFallback(llmConfigId, model -> {
            // 创建带工具的WritingAgent，使用 CREATIVE 任务类型
            WritingAgent writingAgent = AiServices.builder(WritingAgent.class)
                    .chatLanguageModel(langChainConfig.createChatModel(llmConfig, LangChainConfig.TaskType.CREATIVE))
                    .tools(toolProvider.getAllTools())
                    .build();
            
            // 构建用户消息（包含文风注入）
            StringBuilder userPromptBuilder = new StringBuilder();
            userPromptBuilder.append(String.format("小说ID: %s\\n章节号: 第%d章\\n\\n", novelId, chapterNum));
            
            // 如果有文风注入，放在最前面
            if (!styleInjection.isEmpty()) {
                userPromptBuilder.append(styleInjection).append("\\n\\n");
            }
            
            userPromptBuilder.append(String.format("===章纲===\\n%s\\n\\n===上下文信息===\\n%s\\n\\n请根据章纲生成第%d章正文。",
                    outline, contextPrompt, chapterNum));
            
            // 调用Agent生成正文
            return writingAgent.generateChapter(userPromptBuilder.toString());
        });
    }
    
    /**
     * 获取ChatLanguageModel
     * 
     * 【获取逻辑】
     * 1. 如果指定了llmConfigId，从数据库获取对应的配置
     * 2. 如果没有指定，获取任意活跃的模型配置
     * 3. 如果都没有找到，抛出异常
     * 
     * @param llmConfigId 可选的模型配置ID
     * @return ChatLanguageModel实例
     * @throws RuntimeException 如果未找到可用的模型配置
     * 
     * @see LangChainConfig#createChatModel 创建模型实例的方法
     * @see LlmConfigService#getAnyActiveConfig 获取任意活跃配置的方法
     */
    private ChatLanguageModel getModel(String llmConfigId) {
        LlmConfig config;
        if (llmConfigId != null && !llmConfigId.isEmpty()) {
            // 指定了配置ID，从数据库获取
            config = llmConfigService.getConfigById(llmConfigId);
        } else {
            // 没有指定，获取任意活跃配置
            config = llmConfigService.getAnyActiveConfig("all");
        }
        
        if (config == null) {
            throw new RuntimeException("未找到可用的模型配置，请先在模型配置页面添加并激活一个模型");
        }
        
        // 使用LangChainConfig创建ChatLanguageModel实例
        return langChainConfig.createChatModel(config);
    }
}
