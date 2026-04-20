package com.novel.system.entity.mysql;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 模型配置实体
 */
@Data
public class LlmConfig {
    /** 配置ID，主键 */
    private String configId;
    /** 配置名称（如：GPT-4写作模型） */
    private String configName;
    /** API密钥 */
    private String apiKey;
    /** API基础地址（如：https://api.openai.com/v1） */
    private String baseUrl;
    /** 模型名称（如：gpt-4、claude-3-opus等） */
    private String modelName;
    /** 模型类型：openai/claude/qwen等 */
    private String modelType = "openai";
    /** API端点路径 */
    private String apiEndpoint;
    /** 认证类型：bearer/api_key/basic/anthropic */
    private String authType = "bearer";
    /** 最大生成Token数 */
    private Integer maxTokens = 4096;
    /** 生成温度（0.0-2.0，越高越随机） */
    private Double temperature = 0.7;
    /** 用途：outline-大纲/writing-正文/summary-摘要/audit-校验/all-通用 */
    private String usageType = "all";
    /** 是否激活：0-禁用，1-激活 */
    private Integer isActive = 1;
    /** 所属用户ID */
    private String userId;
    /** 配置描述 */
    private String description;
    /** Embedding模型名称（如：text-embedding-ada-002） */
    private String embeddingModel;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
    
    // ========== 前端兼容字段 ==========

    /** 前端期望 provider（后端是 modelType） - getter */
    @JsonProperty("provider")
    public String getProvider() {
        return modelType;
    }

    /** 前端期望 provider（后端是 modelType） - setter（反序列化用） */
    @JsonProperty("provider")
    public void setProvider(String provider) {
        this.modelType = provider;
    }

    /** 前端期望 apiBaseUrl（后端是 baseUrl） - getter */
    @JsonProperty("apiBaseUrl")
    public String getApiBaseUrl() {
        return baseUrl;
    }

    /** 前端期望 apiBaseUrl（后端是 baseUrl） - setter（反序列化用） */
    @JsonProperty("apiBaseUrl")
    public void setApiBaseUrl(String apiBaseUrl) {
        this.baseUrl = apiBaseUrl;
    }

    /** 前端期望 topP（默认值） */
    @JsonProperty("topP")
    public Double getTopP() {
        return 1.0;
    }
}
