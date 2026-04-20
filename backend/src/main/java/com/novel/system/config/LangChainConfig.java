package com.novel.system.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
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
 * 根据数据库中的LlmConfig动态创建ChatLanguageModel实例。
 * 这是连接业务配置和LangChain4j框架的桥梁。
 * 
 * 【设计模式】
 * 采用"工厂模式"，根据不同的配置创建不同的模型实例。
 * 
 * 【缓存机制】
 * 使用ConcurrentHashMap缓存模型实例，避免重复创建：
 * - modelCache: 缓存ChatLanguageModel实例
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
 * // 创建ChatLanguageModel
 * ChatLanguageModel model = langChainConfig.createChatModel(config);
 * 
 * // 使用模型
 * String response = model.generate("你好");
 * </pre>
 * 
 * @author AI Assistant
 * @since 1.0.0
 * @see LlmConfig 模型配置实体
 * @see ChatLanguageModel LangChain4j的聊天模型接口
 */
@Slf4j
@Component
public class LangChainConfig {
    
    /**
     * 模型实例缓存
     * key: configId, value: ChatLanguageModel实例
     * 
     * 【缓存策略】
     * - 使用ConcurrentHashMap保证线程安全
     * - 使用computeIfAbsent实现惰性加载
     * - 配置更新时清除缓存
     */
    private final Map<String, ChatLanguageModel> modelCache = new ConcurrentHashMap<>();
    
    /**
     * Embedding模型实例缓存
     * key: configId, value: EmbeddingModel实例
     */
    private final Map<String, EmbeddingModel> embeddingModelCache = new ConcurrentHashMap<>();
    
    /**
     * 根据LlmConfig创建ChatLanguageModel
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
     * @return ChatLanguageModel实例
     * @throws RuntimeException 如果配置无效或创建失败
     * 
     * @see #buildChatModel 实际创建模型的方法
     */
    public ChatLanguageModel createChatModel(LlmConfig config) {
        return modelCache.computeIfAbsent(config.getConfigId(), id -> buildChatModel(config));
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
        log.info("已清除模型缓存: {}", configId);
    }
    
    /**
     * 构建ChatLanguageModel
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
     * @return ChatLanguageModel实例
     * @throws RuntimeException 如果baseUrl为空
     */
    private ChatLanguageModel buildChatModel(LlmConfig config) {
        String baseUrl = buildBaseUrl(config);
        String apiKey = config.getApiKey();
        String modelName = config.getModelName();
        
        log.info("创建ChatLanguageModel: model={}, baseUrl={}", modelName, baseUrl);
        
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
        
        // 根据baseUrl判断使用哪个Embedding模型
        String embeddingModelName;
        if (config.getEmbeddingModel() != null && !config.getEmbeddingModel().isEmpty()) {
            // 用户指定了Embedding模型
            embeddingModelName = config.getEmbeddingModel();
        } else if (baseUrl.contains("dashscope") || baseUrl.contains("aliyuncs.com")) {
            // 阿里云DashScope使用text-embedding-v2
            embeddingModelName = "text-embedding-v2";
            log.info("检测到阿里云DashScope API，使用Embedding模型: {}", embeddingModelName);
        } else {
            // 默认使用OpenAI的text-embedding-ada-002
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
     * 构建Base URL — 直接使用前端传来的地址，不做任何拼接
     *
     * 【处理规则】
     * 1. 移除末尾的斜杠
     * 2. 直接返回，不添加任何版本路径
     *
     * 【为什么不做自动拼接？】
     * - Ollama: http://localhost:11434/v1
     * - OpenAI: https://api.openai.com/v1
     * - 兼容各种本地/远程模型，用户填什么就用什么
     *
     * @param config LlmConfig配置对象
     * @return 处理后的baseUrl（原样返回，仅去除尾部斜杠）
     */
    private String buildBaseUrl(LlmConfig config) {
        String baseUrl = config.getBaseUrl();

        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new RuntimeException("API地址不能为空");
        }

        baseUrl = baseUrl.trim();

        // 仅移除末尾的斜杠，其余不做任何处理
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        log.debug("使用原始URL: {}", baseUrl);
        return baseUrl;
    }
}
