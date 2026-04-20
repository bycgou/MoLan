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
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", config.getModelName());
            requestBody.put("max_tokens", 50);
            requestBody.put("messages", List.of(
                Map.of("role", "user", "content", "请回复：连接成功")
            ));
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            String url = buildApiUrl(config);
            log.info("测试连接URL: {}", url);
            
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("模型连接测试失败: {}", e.getMessage());
            return false;
        }
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
