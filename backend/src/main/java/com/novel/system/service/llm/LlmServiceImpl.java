package com.novel.system.service.llm;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import com.novel.system.ai.rag.EmbeddingService;
import com.novel.system.config.LangChainConfig;
import com.novel.system.entity.mysql.LlmConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
    private final EmbeddingService embeddingService;
    
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;
    
    @Override
    public String generate(String prompt, String modelType) {
        return generateWithUsage(null, prompt, modelType, getCurrentUserId());
    }

    @Override
    public String generateWithSystem(String systemInstruction, String userMessage, String modelType) {
        return generateWithUsage(systemInstruction, userMessage, modelType, getCurrentUserId());
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
        return generateWithUsage(systemInstruction, userMessage, usageType, getCurrentUserId());
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
        return embeddingService.embed(text);
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
                log.warn("LLM Rerank返回无scores数组，使用 bigram Jaccard 降级");
                for (int i = 0; i < documents.size(); i++) {
                    double score = bigramJaccard(query, documents.get(i));
                    scoredDocs.add(new AbstractMap.SimpleEntry<>(documents.get(i), score));
                }
                scoredDocs.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
            }
            
            scoredDocs.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
            return scoredDocs;
            
        } catch (Exception e) {
            log.error("LLM Rerank失败，使用 bigram Jaccard 降级: {}", e.getMessage());
            List<Map.Entry<String, Double>> scoredDocs = new ArrayList<>();
            for (int i = 0; i < documents.size(); i++) {
                double score = bigramJaccard(query, documents.get(i));
                scoredDocs.add(new AbstractMap.SimpleEntry<>(documents.get(i), score));
            }
            scoredDocs.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
            return scoredDocs;
        }
    }
    
    // ========== 私有方法 ==========

    /**
     * 从SecurityContextHolder获取当前用户ID
     */
    private String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof String) {
            String principal = (String) auth.getPrincipal();
            if (!"anonymousUser".equals(principal)) {
                return principal;
            }
        }
        return null;
    }

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
        ChatModel model = langChainConfig.createChatModel(config);
        
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
    public String generateWithMaxTokens(String systemInstruction, String userMessage,
                                         String usageType, String userId, int maxTokens, String configId) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                LlmConfig config = null;
                // 优先使用用户指定的 configId
                if (configId != null && !configId.isEmpty()) {
                    config = llmConfigService.getConfigById(configId);
                }
                // 未指定或未找到，走自动选择
                if (config == null && userId != null && !userId.isEmpty()) {
                    config = llmConfigService.getActiveConfig(userId, usageType);
                    if (config == null) config = llmConfigService.getActiveConfig(userId, "all");
                }
                if (config == null) config = llmConfigService.getAnyActiveConfig(usageType);
                if (config == null) throw new RuntimeException("未找到可用的模型配置");

                // 创建 config 副本，覆盖 maxTokens
                LlmConfig copy = new LlmConfig();
                copy.setConfigId(config.getConfigId() + "_mt" + maxTokens);
                copy.setConfigName(config.getConfigName());
                copy.setApiKey(config.getApiKey());
                copy.setBaseUrl(config.getBaseUrl());
                copy.setModelName(config.getModelName());
                copy.setModelType(config.getModelType());
                copy.setTemperature(config.getTemperature());
                copy.setMaxTokens(maxTokens);

                log.info("自定义maxTokens生成 -> 模型: {}, maxTokens: {}", copy.getModelName(), maxTokens);
                return callModel(copy, systemInstruction, userMessage);
            } catch (Exception e) {
                log.warn("LLM调用失败，第{}次重试: {}", attempt, e.getMessage());
                if (attempt < MAX_RETRIES) {
                    try { Thread.sleep(RETRY_DELAY_MS * attempt); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("重试被中断", ie);
                    }
                }
            }
        }
        throw new RuntimeException("LLM调用失败，已重试" + MAX_RETRIES + "次");
    }

    @Override
    public ChatModel getModelForAgent(String llmConfigId) {
        LlmConfig config = getLlmConfig(llmConfigId);
        if (config == null) {
            config = llmConfigService.getAnyActiveConfig("all");
        }
        if (config == null) {
            throw new RuntimeException("未找到可用的模型配置");
        }
        return langChainConfig.createChatModel(config);
    }

    @Override
    public ChatModel getModelForUsage(String usageType, String userId,
                                       com.novel.system.config.LangChainConfig.TaskType taskType) {
        LlmConfig config = null;
        if (userId != null && !userId.isEmpty()) {
            config = llmConfigService.getActiveConfig(userId, usageType);
            if (config == null) {
                config = llmConfigService.getActiveConfig(userId, "all");
            }
        }
        if (config == null) {
            config = llmConfigService.getAnyActiveConfig(usageType);
        }
        if (config == null) {
            throw new RuntimeException("未找到可用的模型配置，请先在模型配置页面添加模型");
        }

        log.info("模型选择 -> 用户: {}, 用途: [{}], 选择模型: {}", userId, usageType, config.getModelName());
        if (taskType != null) {
            return langChainConfig.createChatModel(config, taskType);
        }
        return langChainConfig.createChatModel(config);
    }

    private double bigramJaccard(String a, String b) {
        Set<String> aBigrams = getBigrams(a);
        Set<String> bBigrams = getBigrams(b);
        if (aBigrams.isEmpty() || bBigrams.isEmpty()) return 0;
        Set<String> intersection = new HashSet<>(aBigrams);
        intersection.retainAll(bBigrams);
        Set<String> union = new HashSet<>(aBigrams);
        union.addAll(bBigrams);
        return (double) intersection.size() / union.size();
    }

    private Set<String> getBigrams(String s) {
        Set<String> bigrams = new HashSet<>();
        for (int i = 0; i < s.length() - 1; i++) {
            bigrams.add(s.substring(i, i + 2));
        }
        return bigrams;
    }
}
