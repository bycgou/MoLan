package com.novel.system.ai.chain;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import com.novel.system.ai.agent.*;
import com.novel.system.ai.memory.StyleAwareChatMemory;
import com.novel.system.ai.tool.NovelToolProvider;
import com.novel.system.config.LangChainConfig;
import com.novel.system.entity.mysql.LlmConfig;
import com.novel.system.service.llm.LlmConfigService;
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
     * 负责创建ChatModel实例
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
     * 1. 获取ChatModel（根据llmConfigId或默认配置）
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

        ChatModel model = getModelWithMinTokens(llmConfigId, LangChainConfig.TaskType.STRUCTURED, 16384);

        if (toolProvider.hasMcpTools()) {
            log.info("MCP 工具可用，工具数: {}", toolProvider.getMcpToolSpecifications().size());
        }

        OutlineAgent outlineAgent = AiServices.builder(OutlineAgent.class)
                .chatModel(model)
                .tools(toolProvider.getAllTools())
                .build();

        String userPrompt = String.format(
            "小说ID: %s\\n章节号: 第%d章\\n\\n===上下文信息===\\n%s\\n\\n请生成第%d章的章纲。",
            novelId, chapterNum, contextPrompt, chapterNum
        );

        return outlineAgent.generateOutline(userPrompt);
    }
    
    /**
     * 执行写作链
     * 
     * 【执行流程】
     * 1. 获取ChatModel
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

        ChatModel model = getModelWithMinTokens(llmConfigId, LangChainConfig.TaskType.CREATIVE, 16384);

        WritingAgent writingAgent = AiServices.builder(WritingAgent.class)
                .chatModel(model)
                .tools(toolProvider.getAllTools())
                .build();

        String userPrompt = String.format(
            "小说ID: %s\\n章节号: 第%d章\\n\\n===章纲===\\n%s\\n\\n===上下文信息===\\n%s\\n\\n请根据章纲生成第%d章正文。",
            novelId, chapterNum, outline, contextPrompt, chapterNum
        );

        return writingAgent.generateChapter(userPrompt);
    }
    
    /**
     * 执行摘要链
     * 
     * 【执行流程】
     * 1. 获取ChatModel
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

        ChatModel model = getModel(llmConfigId, LangChainConfig.TaskType.STRUCTURED);

        SummaryAgentAiService summaryAgent = AiServices.builder(SummaryAgentAiService.class)
                .chatModel(model)
                .build();

        String truncatedContent = truncateForSummary(chapterContent);

        String userPrompt = String.format(
            "章节号: 第%d章\\n\\n===章纲===\\n%s\\n\\n===章节内容===\\n%s",
            chapterNum, outline != null ? outline : "无", truncatedContent
        );

        return summaryAgent.generateSummary(userPrompt);
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

        ChatModel model = getModelWithMinTokens(llmConfigId, LangChainConfig.TaskType.STRUCTURED, 32768);

        VolumeOutlineAgent agent = AiServices.builder(VolumeOutlineAgent.class)
                .chatModel(model)
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

        ChatModel model = getModel(llmConfigId);

        // 分离系统级（硬约束）和用户级（软引导）风格内容
        String systemStyleRules = "";
        String userStyleSamples = "";
        if (styleCardId != null && !styleCardId.isEmpty()) {
            systemStyleRules = styleCardService.buildSystemStyleRules(styleCardId);
            userStyleSamples = styleCardService.buildUserStyleSamples(styleCardId);
            if (!systemStyleRules.isEmpty() || !userStyleSamples.isEmpty()) {
                log.info("注入文风卡片: styleCardId={}, systemRulesLen={}, userSamplesLen={}",
                        styleCardId, systemStyleRules.length(), userStyleSamples.length());
            }
        }

        // 构建Agent，如果有系统级风格规则则使用StyleAwareChatMemory
        AiServices<WritingAgent> builder = AiServices.builder(WritingAgent.class)
                .chatModel(model)
                .tools(toolProvider.getAllTools());

        if (!systemStyleRules.isEmpty()) {
            // 从WritingAgent的@SystemMessage注解中提取基础系统指令，避免硬编码重复
            String baseSystemMessage = getBaseSystemMessage();
            ChatMemory chatMemory = new StyleAwareChatMemory(baseSystemMessage, systemStyleRules);
            ChatMemoryProvider memoryProvider = memoryId -> chatMemory;
            builder.chatMemoryProvider(memoryProvider);
        }

        WritingAgent writingAgent = builder.build();

        StringBuilder userPromptBuilder = new StringBuilder();
        userPromptBuilder.append(String.format("小说ID: %s\\n章节号: 第%d章\\n\\n", novelId, chapterNum));

        if (!userStyleSamples.isEmpty()) {
            userPromptBuilder.append(userStyleSamples).append("\\n\\n");
        }

        userPromptBuilder.append(String.format("===章纲===\\n%s\\n\\n===上下文信息===\\n%s\\n\\n请根据章纲生成第%d章正文。",
                outline, contextPrompt, chapterNum));

        return writingAgent.generateChapter(userPromptBuilder.toString());
    }

    /**
     * 流式执行写作生成链 — 通过 SseEmitter 逐 token 输出
     */
    public String executeWritingChainStreaming(String novelId, int chapterNum, String outline,
                                               String contextPrompt, String styleCardId,
                                               String llmConfigId,
                                               org.springframework.web.servlet.mvc.method.annotation.SseEmitter sseEmitter) throws Exception {
        log.info("执行流式写作生成链: novelId={}, chapter={}", novelId, chapterNum);

        dev.langchain4j.model.chat.StreamingChatModel streamingModel = getStreamingModel(llmConfigId);

        String systemStyleRules = "";
        String userStyleSamples = "";
        if (styleCardId != null && !styleCardId.isEmpty()) {
            systemStyleRules = styleCardService.buildSystemStyleRules(styleCardId);
            userStyleSamples = styleCardService.buildUserStyleSamples(styleCardId);
        }

        // 构建系统提示词
        String systemPrompt = getBaseSystemMessage();
        if (!systemStyleRules.isEmpty()) {
            systemPrompt += "\n\n" + systemStyleRules;
        }

        // 构建用户提示词
        StringBuilder userPromptBuilder = new StringBuilder();
        userPromptBuilder.append(String.format("小说ID: %s\n章节号: 第%d章\n\n", novelId, chapterNum));
        if (!userStyleSamples.isEmpty()) {
            userPromptBuilder.append(userStyleSamples).append("\n\n");
        }
        userPromptBuilder.append(String.format("===章纲===\n%s\n\n===上下文信息===\n%s\n\n请根据章纲生成第%d章正文。",
                outline, contextPrompt, chapterNum));

        // 使用 StreamingChatModel 流式生成
        StringBuilder contentBuilder = new StringBuilder();
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<Exception> errorRef = new java.util.concurrent.atomic.AtomicReference<>();

        dev.langchain4j.data.message.SystemMessage sysMsg = dev.langchain4j.data.message.SystemMessage.from(systemPrompt);
        dev.langchain4j.data.message.UserMessage userMsg = dev.langchain4j.data.message.UserMessage.from(userPromptBuilder.toString());
        dev.langchain4j.model.chat.request.ChatRequest chatRequest = dev.langchain4j.model.chat.request.ChatRequest.builder()
                .messages(sysMsg, userMsg)
                .build();

        streamingModel.chat(chatRequest, new dev.langchain4j.model.chat.response.StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String token) {
                contentBuilder.append(token);
                try {
                    sseEmitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                            .name("token").data(token));
                } catch (Exception e) {
                    log.warn("发送流式token失败: {}", e.getMessage());
                }
            }

            @Override
            public void onCompleteResponse(dev.langchain4j.model.chat.response.ChatResponse response) {
                latch.countDown();
            }

            @Override
            public void onError(Throwable error) {
                errorRef.set(new Exception(error));
                latch.countDown();
            }
        });

        // 等待流式完成（10分钟超时）
        if (!latch.await(10, java.util.concurrent.TimeUnit.MINUTES)) {
            throw new RuntimeException("流式写作生成超时");
        }
        if (errorRef.get() != null) {
            throw errorRef.get();
        }

        return contentBuilder.toString();
    }

    private dev.langchain4j.model.chat.StreamingChatModel getStreamingModel(String llmConfigId) {
        LlmConfig config;
        if (llmConfigId != null && !llmConfigId.isEmpty()) {
            config = llmConfigService.getConfigById(llmConfigId);
        } else {
            config = llmConfigService.getAnyActiveConfig("all");
        }
        if (config == null) {
            throw new RuntimeException("未找到可用的模型配置");
        }
        return langChainConfig.createStreamingChatModel(config, LangChainConfig.TaskType.CREATIVE);
    }

    /**
     * 获取ChatModel
     *
     * @param llmConfigId 可选的模型配置ID
     * @return ChatModel实例
     * @throws RuntimeException 如果未找到可用的模型配置
     */
    /**
     * 智能截断章节内容用于摘要生成
     *
     * 【策略】
     * 1. 保留开头（前1500字）：包含场景设定、人物出场
     * 2. 保留结尾（后1500字）：包含钩子、结局
     * 3. 中间部分：按段落边界截取关键情节段落
     *
     * @param content 原始章节内容
     * @return 截断后的内容
     */
    private String truncateForSummary(String content) {
        if (content == null || content.length() <= 4000) {
            return content;
        }

        int totalLen = content.length();
        int headLen = 1500;
        int tailLen = 1500;

        String head = content.substring(0, headLen);
        String tail = content.substring(totalLen - tailLen);

        // 中间部分：尝试在段落边界截取
        String middleSection = content.substring(headLen, totalLen - tailLen);
        String middle = extractKeyParagraphs(middleSection, 1000);

        return head + "\n\n...(中间关键段落)...\n\n" + middle + "\n\n...(省略)...\n\n" + tail;
    }

    /**
     * 从中间部分提取关键段落
     * 优先保留包含对话、动作描写的段落
     */
    private String extractKeyParagraphs(String section, int maxLen) {
        String[] paragraphs = section.split("\n+");
        StringBuilder sb = new StringBuilder();
        int currentLen = 0;

        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (trimmed.isEmpty()) continue;

            // 优先保留对话段落和较短的段落（节奏变化）
            boolean isDialogue = trimmed.contains("“") || trimmed.contains("「");
            boolean isShortPara = trimmed.length() < 100;

            if (isDialogue || isShortPara || currentLen < maxLen / 2) {
                if (currentLen + trimmed.length() <= maxLen) {
                    sb.append(trimmed).append("\n");
                    currentLen += trimmed.length();
                }
            }
        }

        return sb.length() > 0 ? sb.toString().trim() : section.substring(0, Math.min(maxLen, section.length()));
    }

    private ChatModel getModel(String llmConfigId) {
        return getModel(llmConfigId, LangChainConfig.TaskType.CREATIVE);
    }

    private ChatModel getModel(String llmConfigId, LangChainConfig.TaskType taskType) {
        return getModelWithMinTokens(llmConfigId, taskType, 0);
    }

    /**
     * 获取模型，保证 maxTokens 不低于指定最小值。
     * 用于向导模式等需要大输出的场景。
     */
    private ChatModel getModelWithMinTokens(String llmConfigId, LangChainConfig.TaskType taskType, int minMaxTokens) {
        LlmConfig config;
        if (llmConfigId != null && !llmConfigId.isEmpty()) {
            config = llmConfigService.getConfigById(llmConfigId);
        } else {
            config = llmConfigService.getAnyActiveConfig("all");
        }

        if (config == null) {
            throw new RuntimeException("未找到可用的模型配置，请先在模型配置页面添加并激活一个模型");
        }

        if (minMaxTokens > 0 && (config.getMaxTokens() == null || config.getMaxTokens() < minMaxTokens)) {
            LlmConfig copy = new LlmConfig();
            copy.setConfigId(config.getConfigId() + "_min" + minMaxTokens);
            copy.setConfigName(config.getConfigName());
            copy.setApiKey(config.getApiKey());
            copy.setBaseUrl(config.getBaseUrl());
            copy.setModelName(config.getModelName());
            copy.setModelType(config.getModelType());
            copy.setTemperature(config.getTemperature());
            copy.setMaxTokens(minMaxTokens);
            config = copy;
        }

        return langChainConfig.createChatModel(config, taskType);
    }

    /**
     * 从WritingAgent的@SystemMessage注解中提取基础系统指令
     *
     * 避免在ChapterGenerationChain中硬编码重复的系统消息。
     * 与StyleAwareChatMemory配合使用，确保文风卡片注入时基础指令完整。
     *
     * @return WritingAgent的@SystemMessage内容，提取失败时返回空字符串
     */
    private String getBaseSystemMessage() {
        try {
            dev.langchain4j.service.SystemMessage annotation =
                WritingAgent.class.getMethod("generateChapter", String.class)
                    .getAnnotation(dev.langchain4j.service.SystemMessage.class);
            if (annotation == null) {
                return "";
            }
            // LangChain4j @SystemMessage.value() returns String[]
            String[] values = annotation.value();
            if (values == null || values.length == 0) {
                return "";
            }
            return String.join("", values).trim();
        } catch (Exception e) {
            log.warn("无法获取WritingAgent系统消息: {}", e.getMessage());
            return "";
        }
    }
}
