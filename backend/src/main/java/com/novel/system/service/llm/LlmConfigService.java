package com.novel.system.service.llm;

import com.novel.system.entity.mysql.LlmConfig;
import java.util.List;

/**
 * 模型配置服务接口
 */
public interface LlmConfigService {
    
    /**
     * 创建模型配置
     */
    LlmConfig createConfig(LlmConfig config);
    
    /**
     * 更新模型配置
     */
    LlmConfig updateConfig(LlmConfig config);
    
    /**
     * 删除模型配置
     */
    void deleteConfig(String configId);
    
    /**
     * 获取用户所有模型配置
     */
    List<LlmConfig> getUserConfigs(String userId);
    
    /**
     * 获取用户指定用途的活跃模型配置
     */
    LlmConfig getActiveConfig(String userId, String usageType);
    
    /**
     * 根据ID获取配置
     */
    LlmConfig getConfigById(String configId);
    
    /**
     * 测试模型连接
     */
    boolean testConnection(LlmConfig config);
    
    /**
     * 获取任意用户的活跃配置（兜底方案，当userId未知时使用）
     * @param usageType 用途类型
     * @return 活跃配置，可能为null
     */
    LlmConfig getAnyActiveConfig(String usageType);
}
