package com.novel.system.service.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.system.entity.mysql.LlmConfig;
import com.novel.system.mapper.LlmConfigMapper;
import com.novel.system.service.llm.LlmConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 模型配置服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmConfigServiceImpl implements LlmConfigService {
    
    private final LlmConfigMapper llmConfigMapper;
    private final com.novel.system.config.LangChainConfig langChainConfig;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    @Override
    public LlmConfig createConfig(LlmConfig config) {
        config.setConfigId(UUID.randomUUID().toString().replace("-", ""));
        config.setCreateTime(LocalDateTime.now());
        config.setUpdateTime(LocalDateTime.now());
        llmConfigMapper.insert(config);
        return config;
    }
    
    @Override
    public LlmConfig updateConfig(LlmConfig config) {
        config.setUpdateTime(LocalDateTime.now());
        llmConfigMapper.updateById(config);
        // 更新配置后清除模型缓存，确保新配置生效
        langChainConfig.evictCache(config.getConfigId());
        return config;
    }
    
    @Override
    public void deleteConfig(String configId) {
        llmConfigMapper.deleteById(configId);
        // 删除配置后清除模型缓存
        langChainConfig.evictCache(configId);
    }
    
    @Override
    public List<LlmConfig> getUserConfigs(String userId) {
        return llmConfigMapper.selectByUserId(userId);
    }
    
    @Override
    public LlmConfig getActiveConfig(String userId, String usageType) {
        // 先查找指定用途的配置
        LlmConfig config = llmConfigMapper.selectActiveByUsageType(userId, usageType);
        if (config != null) {
            return config;
        }
        // 再查找all用途的配置
        config = llmConfigMapper.selectActiveByUsageType(userId, "all");
        if (config != null) {
            return config;
        }
        // 查找任意活跃配置
        List<LlmConfig> configs = llmConfigMapper.selectByUserIdAndIsActive(userId, 1);
        return configs.isEmpty() ? null : configs.get(0);
    }
    
    @Override
    public LlmConfig getConfigById(String configId) {
        return llmConfigMapper.selectById(configId);
    }
    
    @Override
    public LlmConfig getAnyActiveConfig(String usageType) {
        // 先按指定用途查找
        LlmConfig config = llmConfigMapper.selectActiveByUsageTypeGlobal(usageType);
        if (config != null) return config;
        // 再按all用途查找
        config = llmConfigMapper.selectActiveByUsageTypeGlobal("all");
        if (config != null) return config;
        // 最后返回任意激活配置
        List<LlmConfig> configs = llmConfigMapper.selectAllActive();
        return configs.isEmpty() ? null : configs.get(0);
    }

    @Override
    public boolean testConnection(LlmConfig config) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + config.getApiKey());
            
            // 根据模型类型选择不同的测试端点
            String url = buildApiUrl(config);
            Map<String, Object> requestBody;
            
            if (isEmbeddingModel(config)) {
                // Embedding模型
                requestBody = new HashMap<>();
                requestBody.put("model", config.getModelName());
                
                // 优先使用 Ollama 原生端点（兼容性更好）
                String baseUrl = config.getBaseUrl().trim();
                while (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
                
                if (baseUrl.contains("localhost:11434") || baseUrl.contains("127.0.0.1:11434")) {
                    // Ollama 本地：使用原生 /api/embed 端点
                    url = baseUrl.replace("/v1", "") + "/api/embed";
                    requestBody.put("input", List.of("测试连接"));
                } else {
                    // 远程 API：使用 OpenAI 兼容端点
                    url = buildEmbeddingUrl(config);
                    requestBody.put("input", "测试连接");
                }
            } else {
                // Chat模型：调用 /v1/chat/completions
                requestBody = new HashMap<>();
                requestBody.put("model", config.getModelName());
                requestBody.put("max_tokens", 50);
                requestBody.put("messages", List.of(
                    Map.of("role", "user", "content", "请回复：连接成功")
                ));
            }
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            log.info("测试连接URL: {}, 模型类型: {}", url, config.getModelType());
            
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("模型连接测试失败: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 判断是否为Embedding模型
     */
    private boolean isEmbeddingModel(LlmConfig config) {
        String type = config.getModelType();
        String name = config.getModelName();
        
        // 检查modelType字段
        if ("embedding".equalsIgnoreCase(type)) {
            return true;
        }
        
        // 检查模型名称关键字
        if (name != null && (name.contains("embedding") || name.contains("embed"))) {
            return true;
        }
        
        // 检查embeddingModel字段是否被设置（且modelName相同）
        if (config.getEmbeddingModel() != null && !config.getEmbeddingModel().isEmpty()
            && config.getEmbeddingModel().equals(name)) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 构建Embedding API URL
     */
    private String buildEmbeddingUrl(LlmConfig config) {
        String baseUrl = config.getBaseUrl().trim();
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        
        // 如果已经包含完整端点路径，直接返回
        if (baseUrl.endsWith("/embeddings") || baseUrl.endsWith("/v1/embeddings")) {
            return baseUrl;
        }
        
        // 追加 /embeddings（OpenAI 兼容格式）
        // 如果以 /v1 结尾，直接加 /embeddings；否则加 /v1/embeddings
        if (baseUrl.endsWith("/v1")) {
            return baseUrl + "/embeddings";
        }
        return baseUrl + "/v1/embeddings";
    }
    
    private String buildApiUrl(LlmConfig config) {
        String baseUrl = config.getBaseUrl();
        
        if (baseUrl == null || baseUrl.isEmpty()) {
            throw new RuntimeException("API地址不能为空");
        }
        
        baseUrl = baseUrl.trim();
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        
        // 如果已经包含完整端点路径，直接返回
        if (baseUrl.endsWith("/chat/completions") || 
            baseUrl.endsWith("/messages") || 
            baseUrl.endsWith("/completions")) {
            return baseUrl;
        }
        
        // 否则追加 /chat/completions（OpenAI 兼容格式）
        return baseUrl + "/chat/completions";
    }
}
