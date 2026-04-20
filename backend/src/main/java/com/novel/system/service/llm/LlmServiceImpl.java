package com.novel.system.service.llm;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.data.embedding.Embedding;
import com.novel.system.config.LangChainConfig;
import com.novel.system.entity.mysql.LlmConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * LLM服务实现 - 基于LangChain4j
 * 替换手写HTTP调用，支持OpenAI兼容API
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmServiceImpl implements LlmService {
    
    private final LlmConfigService llmConfigService;
    private final LangChainConfig langChainConfig;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;
    
    @Override
    public String generate(String prompt, String modelType) {
        return generateWithConfig(null, prompt, null);
    }
    
    @Override
    public String generateWithSystem(String systemInstruction, String userMessage, String modelType) {
        return generateWithConfig(systemInstruction, userMessage, null);
    }
    
    @Override
    public String generateWithConfig(String systemInstruction, String userMessage, String llmConfigId) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                LlmConfig config = getLlmConfig(llmConfigId);
                if (config == null) {
                    throw new RuntimeException("未找到可用的模型配置，请先在模型配置页面添加模型");
                }
                
                log.info("使用模型配置: {} - {}", config.getConfigName(), config.getModelName());
                return callModel(config, systemInstruction, userMessage);
                
            } catch (Exception e) {
                log.warn("LLM调用失败，第{}次重试: {}", attempt, e.getMessage());
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("LLM调用中断", ie);
                    }
                } else {
                    throw new RuntimeException("LLM调用失败: " + e.getMessage(), e);
                }
            }
        }
        throw new RuntimeException("LLM调用失败");
    }
    
    @Override
    public String generateWithUsage(String systemInstruction, String userMessage, String usageType) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                // 不指定用户时，查找任意活跃配置（跨用户兜底）
                LlmConfig config = llmConfigService.getAnyActiveConfig(usageType);
                if (config == null) {
                    throw new RuntimeException("未找到可用的模型配置，请先在模型配置页面添加模型");
                }
                
                log.info("Token策略路由 -> 用途: [{}], 选择模型: {}", usageType, config.getModelName());
                return callModel(config, systemInstruction, userMessage);
                
            } catch (Exception e) {
                log.warn("LLM调用失败，用途: {} 第{}次重试: {}", usageType, attempt, e.getMessage());
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("LLM调用中断", ie);
                    }
                } else {
                    throw new RuntimeException("LLM调用失败: " + e.getMessage(), e);
                }
            }
        }
        throw new RuntimeException("LLM调用失败");
    }
    
    @Override
    public String generateWithUsage(String systemInstruction, String userMessage, String usageType, String userId) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                // 先查指定用户的配置
                LlmConfig config = null;
                if (userId != null && !userId.isEmpty()) {
                    config = llmConfigService.getActiveConfig(userId, usageType);
                    if (config == null) {
                        config = llmConfigService.getActiveConfig(userId, "all");
                    }
                }
                // 找不到则兜底查找任意活跃配置
                if (config == null) {
                    config = llmConfigService.getAnyActiveConfig(usageType);
                }
                if (config == null) {
                    throw new RuntimeException("未找到可用的模型配置，请先在模型配置页面添加模型");
                }
                
                log.info("Token策略路由 -> 用户: {}, 用途: [{}], 选择模型: {}", userId, usageType, config.getModelName());
                return callModel(config, systemInstruction, userMessage);
                
            } catch (Exception e) {
                log.warn("LLM调用失败，用途: {} 第{}次重试: {}", usageType, attempt, e.getMessage());
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("LLM调用中断", ie);
                    }
                } else {
                    throw new RuntimeException("LLM调用失败: " + e.getMessage(), e);
                }
            }
        }
        throw new RuntimeException("LLM调用失败");
    }
    
    @Override
    public float[] getEmbedding(String text) {
        try {
            LlmConfig config = getLlmConfig(null);
            if (config == null) {
                log.warn("未找到模型配置，返回空向量");
                return new float[0];
            }
            
            EmbeddingModel embeddingModel = langChainConfig.createEmbeddingModel(config);
            String truncatedText = text.substring(0, Math.min(text.length(), 8000));
            Embedding embedding = embeddingModel.embed(truncatedText).content();
            
            return embedding.vector();
            
        } catch (Exception e) {
            log.error("获取嵌入向量失败: {}", e.getMessage());
            return new float[0];
        }
    }
    
    @Override
    public List<Map.Entry<String, Double>> rerank(String query, List<String> documents) {
        if (documents == null || documents.isEmpty()) {
            return Collections.emptyList();
        }
        
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("作为小说编辑，请评估以下历史剧情片段对当前写作查询的相关性。\n");
        promptBuilder.append("当前查询：").append(query).append("\n\n");
        for (int i = 0; i < documents.size(); i++) {
            promptBuilder.append("[文档 ").append(i).append("]\n").append(documents.get(i)).append("\n");
        }
        promptBuilder.append("\n请严格按照JSON格式返回打分(0-10分，分数越高越相关)：\n");
        promptBuilder.append("{\"scores\": [8.5, 2.1, 9.0]}");
        
        try {
            String response = generateWithUsage("你是一个专业的文本相关性评分器。", promptBuilder.toString(), "summary");
            
            String cleanJson = response.trim();
            if (cleanJson.startsWith("```json")) cleanJson = cleanJson.substring(7);
            else if (cleanJson.startsWith("```")) cleanJson = cleanJson.substring(3);
            if (cleanJson.endsWith("```")) cleanJson = cleanJson.substring(0, cleanJson.length() - 3);
            cleanJson = cleanJson.trim();
            
            com.fasterxml.jackson.databind.JsonNode rootNode = objectMapper.readTree(cleanJson);
            
            List<Map.Entry<String, Double>> scoredDocs = new ArrayList<>();
            if (rootNode.has("scores") && rootNode.get("scores").isArray()) {
                com.fasterxml.jackson.databind.JsonNode scoresArray = rootNode.get("scores");
                for (int i = 0; i < documents.size(); i++) {
                    double score = i < scoresArray.size() ? scoresArray.get(i).asDouble() : 0.0;
                    scoredDocs.add(new AbstractMap.SimpleEntry<>(documents.get(i), score));
                }
            } else {
                for (int i = 0; i < documents.size(); i++) {
                    scoredDocs.add(new AbstractMap.SimpleEntry<>(documents.get(i), 10.0 - i * 0.1));
                }
            }
            
            scoredDocs.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
            return scoredDocs;
            
        } catch (Exception e) {
            log.error("LLM Rerank失败: {}", e.getMessage());
            List<Map.Entry<String, Double>> scoredDocs = new ArrayList<>();
            for (int i = 0; i < documents.size(); i++) {
                scoredDocs.add(new AbstractMap.SimpleEntry<>(documents.get(i), 10.0 - i * 0.1));
            }
            return scoredDocs;
        }
    }
    
    // ========== 私有方法 ==========
    
    /**
     * 获取LLM配置
     */
    private LlmConfig getLlmConfig(String llmConfigId) {
        if (llmConfigId != null && !llmConfigId.isEmpty()) {
            LlmConfig config = llmConfigService.getConfigById(llmConfigId);
            if (config != null && config.getIsActive() == 1) {
                return config;
            }
        }
        return llmConfigService.getAnyActiveConfig("all");
    }
    
    /**
     * 使用LangChain4j调用模型
     */
    private String callModel(LlmConfig config, String systemInstruction, String userMessage) {
        ChatLanguageModel model = langChainConfig.createChatModel(config);
        
        // 构建消息列表
        List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
        if (systemInstruction != null && !systemInstruction.isEmpty()) {
            messages.add(new SystemMessage(systemInstruction));
        }
        messages.add(new UserMessage(userMessage));
        
        // 调用模型 - 使用ChatRequest包装
        dev.langchain4j.model.chat.request.ChatRequest request = dev.langchain4j.model.chat.request.ChatRequest.builder()
                .messages(messages)
                .build();
        dev.langchain4j.model.chat.response.ChatResponse response = model.chat(request);
        return response.aiMessage().text();
    }
    
    @Override
    public ChatLanguageModel getModelForAgent(String llmConfigId) {
        LlmConfig config = getLlmConfig(llmConfigId);
        if (config == null) {
            config = llmConfigService.getAnyActiveConfig("all");
        }
        if (config == null) {
            throw new RuntimeException("未找到可用的模型配置");
        }
        return langChainConfig.createChatModel(config);
    }
}
