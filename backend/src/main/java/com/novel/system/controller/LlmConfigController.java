package com.novel.system.controller;

import com.novel.system.dto.response.ApiResponse;
import com.novel.system.entity.mysql.LlmConfig;
import com.novel.system.service.llm.LlmConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * 模型配置控制器
 */
@Slf4j
@RestController
@RequestMapping("/llm-config")
@RequiredArgsConstructor
public class LlmConfigController {
    
    private final LlmConfigService llmConfigService;
    
    /**
     * 创建模型配置
     */
    @PostMapping
    public ApiResponse<LlmConfig> createConfig(@RequestBody LlmConfig config,
                                                jakarta.servlet.http.HttpServletRequest httpRequest) {
        String userId = (String) httpRequest.getAttribute("userId");
        if (userId == null) {
            return ApiResponse.error(401, "未登录或Token已过期");
        }
        config.setUserId(userId);
        log.info("创建模型配置: {}, 用户: {}", config.getConfigName(), userId);
        LlmConfig saved = llmConfigService.createConfig(config);
        return ApiResponse.success("创建成功", saved);
    }
    
    /**
     * 更新模型配置
     */
    @PutMapping("/{configId}")
    public ApiResponse<LlmConfig> updateConfig(@PathVariable String configId, @RequestBody LlmConfig config,
                                                jakarta.servlet.http.HttpServletRequest httpRequest) {
        String userId = (String) httpRequest.getAttribute("userId");
        if (userId == null) {
            return ApiResponse.error(401, "未登录或Token已过期");
        }
        // 校验归属：只能修改自己的配置
        LlmConfig existing = llmConfigService.getConfigById(configId);
        if (existing == null) {
            return ApiResponse.error(404, "配置不存在");
        }
        if (!userId.equals(existing.getUserId())) {
            return ApiResponse.error(403, "无权修改该配置");
        }
        config.setConfigId(configId);
        config.setUserId(existing.getUserId()); // 保持原 owner，不允许篡改
        LlmConfig updated = llmConfigService.updateConfig(config);
        return ApiResponse.success("更新成功", updated);
    }
    
    /**
     * 删除模型配置
     */
    @DeleteMapping("/{configId}")
    public ApiResponse<Void> deleteConfig(@PathVariable String configId,
                                          jakarta.servlet.http.HttpServletRequest httpRequest) {
        String userId = (String) httpRequest.getAttribute("userId");
        if (userId == null) {
            return ApiResponse.error(401, "未登录或Token已过期");
        }
        // 校验归属：只能删除自己的配置
        LlmConfig existing = llmConfigService.getConfigById(configId);
        if (existing == null) {
            return ApiResponse.error(404, "配置不存在");
        }
        if (!userId.equals(existing.getUserId())) {
            return ApiResponse.error(403, "无权删除该配置");
        }
        llmConfigService.deleteConfig(configId);
        return ApiResponse.success("删除成功", null);
    }
    
    /**
     * 获取用户所有模型配置
     */
    @GetMapping("/list")
    public ApiResponse<List<LlmConfig>> getConfigList(jakarta.servlet.http.HttpServletRequest httpRequest) {
        String userId = (String) httpRequest.getAttribute("userId");
        if (userId == null) {
            return ApiResponse.error(401, "未登录或Token已过期");
        }
        List<LlmConfig> configs = llmConfigService.getUserConfigs(userId);
        configs.forEach(this::maskApiKey);
        return ApiResponse.success(configs);
    }
    
    /**
     * 获取模型配置详情
     */
    @GetMapping("/{configId}")
    public ApiResponse<LlmConfig> getConfig(@PathVariable String configId) {
        LlmConfig config = llmConfigService.getConfigById(configId);
        if (config == null) {
            return ApiResponse.error(404, "配置不存在");
        }
        maskApiKey(config);
        return ApiResponse.success(config);
    }
    
    /**
     * 测试模型连接（用于创建时，直接传入完整配置）
     */
    @PostMapping("/test")
    public ApiResponse<Boolean> testConnection(@RequestBody LlmConfig config) {
        log.info("测试模型连接: {}", config.getModelName());
        boolean success = llmConfigService.testConnection(config);
        if (success) {
            return ApiResponse.success("连接成功", true);
        } else {
            return ApiResponse.error(500, "连接失败，请检查配置");
        }
    }

    /**
     * 测试已保存模型的连接（根据configId从数据库读取真实配置）
     */
    @PostMapping("/test/{configId}")
    public ApiResponse<Boolean> testSavedConfig(@PathVariable String configId) {
        LlmConfig config = llmConfigService.getConfigById(configId);
        if (config == null) {
            return ApiResponse.error(404, "配置不存在");
        }
        log.info("测试已保存模型连接: {}, configId: {}", config.getModelName(), configId);
        boolean success = llmConfigService.testConnection(config);
        if (success) {
            return ApiResponse.success("连接成功", true);
        } else {
            return ApiResponse.error(500, "连接失败，请检查配置");
        }
    }
    
    /**
     * 获取当前活跃配置
     */
    @GetMapping("/active")
    public ApiResponse<LlmConfig> getActiveConfig(
            jakarta.servlet.http.HttpServletRequest httpRequest,
            @RequestParam(required = false, defaultValue = "all") String usageType) {
        String userId = (String) httpRequest.getAttribute("userId");
        if (userId == null) {
            return ApiResponse.error(401, "未登录或Token已过期");
        }
        LlmConfig config = llmConfigService.getActiveConfig(userId, usageType);
        if (config == null) {
            return ApiResponse.error(404, "未找到活跃配置");
        }
        maskApiKey(config);
        return ApiResponse.success(config);
    }
    
    /**
     * API Key脱敏：只显示前4位和后4位
     */
    private void maskApiKey(LlmConfig config) {
        String apiKey = config.getApiKey();
        if (apiKey != null && apiKey.length() > 12) {
            config.setApiKey(apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4));
        } else if (apiKey != null && !apiKey.isEmpty()) {
            config.setApiKey("****");
        }
    }
}
