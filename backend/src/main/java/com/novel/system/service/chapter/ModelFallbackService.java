package com.novel.system.service.chapter;

import com.novel.system.config.LangChainConfig;
import com.novel.system.entity.mysql.LlmConfig;
import com.novel.system.service.llm.LlmConfigService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 模型 Fallback 服务
 * 实现 Super-Ma 的模型自动切换机制
 * 
 * 【核心功能】
 * 1. 主模型调用失败时自动切换到备用模型
 * 2. 支持自定义 Fallback 回调（用于 SSE 通知前端）
 * 3. 所有模型都失败时抛出异常
 * 
 * @author AI Assistant
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelFallbackService {
    
    private final LlmConfigService llmConfigService;
    private final LangChainConfig langChainConfig;
    
    /**
     * 执行带 Fallback 的 AI 调用
     * 
     * @param primaryConfigId 主模型配置ID
     * @param task 执行任务（接收 ChatLanguageModel，返回结果）
     * @param onFallback 切换模型时的回调（用于 SSE 通知前端）
     * @return 任务执行结果
     * @param <T> 返回类型
     */
    public <T> T executeWithFallback(String primaryConfigId, 
                                      Function<ChatLanguageModel, T> task,
                                      Consumer<LlmConfig> onFallback) {
        List<LlmConfig> models = getFallbackList(primaryConfigId);
        
        for (int i = 0; i < models.size(); i++) {
            LlmConfig config = models.get(i);
            try {
                // 如果不是第一个模型，触发 Fallback 回调
                if (i > 0 && onFallback != null) {
                    log.info("模型 Fallback: 切换到备用模型 {}", config.getConfigName());
                    onFallback.accept(config);
                }
                
                // 创建模型并执行任务
                ChatLanguageModel model = langChainConfig.createChatModel(config);
                return task.apply(model);
                
            } catch (Exception e) {
                log.warn("模型 {} 调用失败: {}, 尝试下一个模型", 
                    config.getConfigName(), e.getMessage());
                
                // 如果是最后一个模型，抛出异常
                if (i == models.size() - 1) {
                    log.error("所有模型均失败，主模型: {}", primaryConfigId);
                    throw new RuntimeException("所有模型均失败", e);
                }
            }
        }
        
        throw new RuntimeException("无可用模型");
    }
    
    /**
     * 获取 Fallback 模型列表
     * 
     * @param primaryId 主模型配置ID
     * @return 模型列表（按优先级排序）
     */
    private List<LlmConfig> getFallbackList(String primaryId) {
        List<LlmConfig> fallbackList = new ArrayList<>();
        
        try {
            // 1. 主模型
            LlmConfig primary = llmConfigService.getConfigById(primaryId);
            if (primary != null && primary.getIsActive() == 1) {
                fallbackList.add(primary);
            }
            
            // 2. 尝试获取其他可用模型（使用 getAnyActiveConfig）
            LlmConfig backupConfig = llmConfigService.getAnyActiveConfig("all");
            if (backupConfig != null && backupConfig.getIsActive() == 1
                && !backupConfig.getConfigId().equals(primaryId)
                && !fallbackList.contains(backupConfig)) {
                fallbackList.add(backupConfig);
            }
            
            // 3. 尝试获取特定用途的配置
            LlmConfig writingConfig = llmConfigService.getAnyActiveConfig("writing");
            if (writingConfig != null && writingConfig.getIsActive() == 1
                && !writingConfig.getConfigId().equals(primaryId)
                && !fallbackList.contains(writingConfig)) {
                fallbackList.add(writingConfig);
            }
            
        } catch (Exception e) {
            log.error("获取 Fallback 模型列表失败: {}", e.getMessage());
        }
        
        return fallbackList;
    }
    
    /**
     * 简化版 Fallback（无回调）
     * 
     * @param primaryConfigId 主模型配置ID
     * @param task 执行任务
     * @return 任务执行结果
     * @param <T> 返回类型
     */
    public <T> T executeWithFallback(String primaryConfigId, 
                                      Function<ChatLanguageModel, T> task) {
        return executeWithFallback(primaryConfigId, task, null);
    }
}