package com.novel.system.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import com.novel.system.entity.mysql.LlmConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LangChain4j配置类
 * 
 * 【核心作用】
 * 根据数据库中的LlmConfig动态创建ChatModel实例。
 * 这是连接业务配置和LangChain4j框架的桥梁。
 * 
 * 【设计模式】
 * 采用"工厂模式"，根据不同的配置创建不同的模型实例。
 * 
 * 【缓存机制】
 * 使用ConcurrentHashMap缓存模型实例，避免重复创建：
 * - modelCache: 缓存ChatModel实例
 * - embeddingModelCache: 缓存EmbeddingModel实例
 * 
 * 【支持的模型类型】
 * 支持所有OpenAI兼容的API，包括：
 * - OpenAI GPT系列
 * - 通义千问
 * - DeepSeek
 * - Claude（通过OpenAI兼容接口）
 * - 其他兼容OpenAI API的模型
 * 
 * 【使用示例】
 * <pre>
 * // 从数据库获取配置
 * LlmConfig config = llmConfigService.getConfigById(configId);
 * 
 * // 创建模型
 * ChatModel model = langChainConfig.createChatModel(config);
 * 
 * // 使用模型
 * String response = model.generate("你好");
 * </pre>
 * 
 * @author AI Assistant
 * @since 1.0.0
 * @see LlmConfig 数据库配置实体
 * @see OpenAiChatModel LangChain4j的OpenAI实现
 */
@Slf4j
@Component
public class LangChainConfig {
    
    /**
     * 任务类型枚举
     * 不同的任务类型使用不同的温度参数
     */
    public enum TaskType {
        /** 正文写作：使用用户配置的温度值 */
        CREATIVE(null),
        /** JSON提取：低随机性，确保输出结构化 */
        STRUCTURED(0.3f),
        /** 章节简介：适中温度 */
        SYNOPSIS(0.5f);
        
        private final Float temperature;
        
        TaskType(Float temperature) {
            this.temperature = temperature;
        }
        
        public Float getTemperature() {
            return temperature;
        }
    }
    
    /**
     * 模型缓存
     * key: 配置ID
     * value: ChatModel实例
     */
    private final Map<String, ChatModel> modelCache = new ConcurrentHashMap<>();
    
    /**
     * Embedding模型实例缓存
     * key: configId, value: EmbeddingModel实例
     */
    private final Map<String, EmbeddingModel> embeddingModelCache = new ConcurrentHashMap<>();
    
    /**
     * 根据LlmConfig创建ChatModel
     * 
     * 【执行流程】
     * 1. 检查缓存中是否已有该配置的模型实例
     * 2. 如果有，直接返回缓存的实例
     * 3. 如果没有，调用buildChatModel创建新实例
     * 4. 将新实例存入缓存
     * 5. 返回模型实例
     * 
     * @param config LlmConfig配置对象，包含：
     *               - configId: 配置ID（用于缓存key）
     *               - baseUrl: API基础地址
     *               - apiKey: API密钥
     *               - modelName: 模型名称
     *               - temperature: 温度参数
     *               - maxTokens: 最大token数
     * @return ChatModel实例
     * @throws RuntimeException 如果配置无效或创建失败
     * 
     * @see #buildChatModel 实际创建模型的方法
     */
    public ChatModel createChatModel(LlmConfig config) {
        return modelCache.computeIfAbsent(config.getConfigId(), id -> buildChatModel(config, null));
    }
    
    /**
     * 创建带任务类型的聊天模型
     * @param config LLM配置
     * @param taskType 任务类型
     * @return ChatModel 实例
     */
    public ChatModel createChatModel(LlmConfig config, TaskType taskType) {
        // 如果任务类型指定了温度，使用任务类型的温度
        Double temperature = taskType.getTemperature() != null 
            ? taskType.getTemperature().doubleValue() 
            : config.getTemperature();
        
        // 创建临时配置
        LlmConfig tempConfig = new LlmConfig();
        tempConfig.setConfigId(config.getConfigId() + "_" + taskType.name());
        tempConfig.setBaseUrl(config.getBaseUrl());
        tempConfig.setApiKey(config.getApiKey());
        tempConfig.setModelName(config.getModelName());
        tempConfig.setTemperature(temperature);
        tempConfig.setMaxTokens(config.getMaxTokens());
        
        return modelCache.computeIfAbsent(tempConfig.getConfigId(), id -> buildChatModel(tempConfig, taskType));
    }
    
    /**
     * 创建EmbeddingModel
     * 
     * 【EmbeddingModel的作用】
     * 将文本转换为向量，用于：
     * - 语义搜索
     * - RAG（检索增强生成）
     * - 相似度计算
     * 
     * @param config LlmConfig配置对象
     * @return EmbeddingModel实例
     */
    public EmbeddingModel createEmbeddingModel(LlmConfig config) {
        return embeddingModelCache.computeIfAbsent(config.getConfigId(), id -> buildEmbeddingModel(config));
    }
    
    /**
     * 清除缓存
     * 
     * 【使用场景】
     * 当配置更新或删除时，需要清除缓存，确保使用最新配置。
     * 
     * @param configId 配置ID
     */
    public void evictCache(String configId) {
        modelCache.remove(configId);
        embeddingModelCache.remove(configId);
        log.info("已清除模型缓存: configId={}", configId);
    }
    
    /**
     * 创建流式聊天模型
     * @param config LLM配置
     * @return StreamingChatModel 实例
     */
    public StreamingChatModel createStreamingChatModel(LlmConfig config) {
        return buildStreamingChatModel(config, null);
    }

    /**
     * 创建带任务类型的流式聊天模型
     * @param config LLM配置
     * @param taskType 任务类型
     * @return StreamingChatModel 实例
     */
    public StreamingChatModel createStreamingChatModel(LlmConfig config, TaskType taskType) {
        return buildStreamingChatModel(config, taskType);
    }

    /**
     * 构建StreamingChatModel
     * @param config LLM配置
     * @param taskType 任务类型（null表示默认启用thinking）
     * @return StreamingChatModel 实例
     */
    private StreamingChatModel buildStreamingChatModel(LlmConfig config, TaskType taskType) {
        String baseUrl = buildBaseUrl(config);
        String apiKey = config.getApiKey();
        String modelName = config.getModelName();

        log.info("创建StreamingChatModel: model={}, baseUrl={}", modelName, baseUrl);

        // 根据baseUrl判断超时时间
        Duration timeout;
        if (baseUrl.contains("dashscope") || baseUrl.contains("aliyuncs.com")) {
            timeout = Duration.ofSeconds(300);
        } else {
            timeout = Duration.ofSeconds(120);
        }

        OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder builder =
            OpenAiStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey != null ? apiKey : "***")
                .modelName(modelName)
                .temperature(config.getTemperature() != null ? config.getTemperature() : 0.7)
                .timeout(timeout);

        // 始终启用 thinking 回传：DeepSeek API 可能在未请求时也返回 reasoning_content，
        // 且要求后续请求必须将其传回，否则报错。thinking 内容在独立字段中，不干扰 JSON 提取。
        builder.returnThinking(true)
               .sendThinking(true);

        // 设置maxTokens（如果配置了的话）
        if (config.getMaxTokens() != null && config.getMaxTokens() > 0) {
            builder.maxTokens(config.getMaxTokens());
        }

        return builder.build();
    }
    
    /**
     * 构建ChatModel
     * 
     * 【构建过程】
     * 1. 构建baseUrl（处理各种URL格式）
     * 2. 创建OpenAiChatModel.Builder
     * 3. 设置各种参数（apiKey、modelName、temperature等）
     * 4. 构建并返回模型实例
     * 
     * 【URL处理逻辑】
     * 支持多种URL格式：
     * - https://api.openai.com/v1
     * - https://api.openai.com/v1/chat/completions
     * - https://api.openai.com（自动添加/v1）
     * 
     * @param config LlmConfig配置对象
     * @param taskType 任务类型（null表示默认启用thinking）
     * @return ChatModel实例
     * @throws RuntimeException 如果baseUrl为空
     */
    private ChatModel buildChatModel(LlmConfig config, TaskType taskType) {
        String baseUrl = buildBaseUrl(config);
        String apiKey = config.getApiKey();
        String modelName = config.getModelName();

        log.info("创建ChatModel: model={}, baseUrl={}", modelName, baseUrl);

        // 根据baseUrl判断超时时间
        Duration timeout;
        if (baseUrl.contains("dashscope") || baseUrl.contains("aliyuncs.com")) {
            // 阿里云DashScope需要更长的超时时间（5分钟）
            timeout = Duration.ofSeconds(300);
            log.info("检测到阿里云DashScope API，设置超时时间: 300秒");
        } else {
            // 默认超时时间（2分钟）
            timeout = Duration.ofSeconds(120);
        }

        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey != null ? apiKey : "***")
                .modelName(modelName)
                .temperature(config.getTemperature() != null ? config.getTemperature() : 0.7)
                .timeout(timeout);

        // 始终启用 thinking 回传：DeepSeek API 可能在未请求时也返回 reasoning_content，
        // 且要求后续请求必须将其传回，否则报错。thinking 内容在独立字段中，不干扰 JSON 提取。
        builder.returnThinking(true)
               .sendThinking(true);

        // 设置maxTokens（如果配置了的话）
        if (config.getMaxTokens() != null && config.getMaxTokens() > 0) {
            builder.maxTokens(config.getMaxTokens());
        }

        return builder.build();
    }
    
    /**
     * 构建EmbeddingModel
     * 
     * @param config LlmConfig配置对象
     * @return EmbeddingModel实例
     */
    private EmbeddingModel buildEmbeddingModel(LlmConfig config) {
        String baseUrl = buildBaseUrl(config);
        String apiKey = config.getApiKey();

        // 优先使用用户配置的 modelName，回退到旧逻辑
        String embeddingModelName;
        if (config.getModelName() != null && !config.getModelName().isEmpty()) {
            // 用户在配置中指定了模型名称（推荐方式）
            embeddingModelName = config.getModelName();
        } else if (config.getEmbeddingModel() != null && !config.getEmbeddingModel().isEmpty()) {
            // 兼容旧的 embeddingModel 字段
            embeddingModelName = config.getEmbeddingModel();
        } else if (baseUrl.contains("dashscope") || baseUrl.contains("aliyuncs.com")) {
            embeddingModelName = "text-embedding-v2";
        } else {
            embeddingModelName = "text-embedding-ada-002";
        }

        log.info("创建EmbeddingModel: model={}, baseUrl={}", embeddingModelName, baseUrl);
        
        return OpenAiEmbeddingModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey != null ? apiKey : "***")
                .modelName(embeddingModelName)
                .timeout(Duration.ofSeconds(60))
                .build();
    }
    
    /**
     * 构建Base URL — 处理各种URL格式，确保兼容性
     *
     * 【处理规则】
     * 1. 移除末尾的斜杠
     * 2. 如果包含 /chat/completions，移除它（LangChain4j 会自动追加）
     * 3. 如果包含 /v1/chat/completions，保留到 /v1
     * 4. 其他情况直接返回
     *
     * @param config LlmConfig配置对象
     * @return 处理后的baseUrl
     */
    private String buildBaseUrl(LlmConfig config) {
        String baseUrl = config.getBaseUrl();

        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new RuntimeException("API地址不能为空");
        }

        baseUrl = baseUrl.trim();

        // 移除末尾的斜杠
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        // 如果包含了完整路径 /chat/completions，需要移除（LangChain4j会自动追加）
        if (baseUrl.endsWith("/chat/completions")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - "/chat/completions".length());
            // 移除后可能又有斜杠，再次清理
            while (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
        }

        log.debug("处理后的baseUrl: {}", baseUrl);
        return baseUrl;
    }
}
