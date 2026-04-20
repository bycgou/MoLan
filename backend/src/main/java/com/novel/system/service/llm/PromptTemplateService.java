package com.novel.system.service.llm;

import java.util.Map;

/**
 * Prompt模板服务接口
 */
public interface PromptTemplateService {
    
    /**
     * 获取指定Agent类型的活跃模板
     * @param agentType Agent类型
     * @return 模板内容
     */
    String getActiveTemplate(String agentType);
    
    /**
     * 渲染Prompt（变量替换）
     * @param agentType Agent类型
     * @param variables 变量映射
     * @return 渲染后的Prompt
     */
    String renderPrompt(String agentType, Map<String, Object> variables);
    
    /**
     * 更新模板
     * @param templateId 模板ID
     * @param systemInstruction 新的系统指令
     */
    void updateTemplate(String templateId, String systemInstruction);
}
