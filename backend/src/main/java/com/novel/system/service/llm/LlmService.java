package com.novel.system.service.llm;

/**
 * LLM服务接口
 */
public interface LlmService {
    
    /**
     * 生成文本
     * @param prompt 提示词
     * @param modelType 模型类型: claude/qwen
     * @return 生成的文本
     */
    String generate(String prompt, String modelType);
    
    /**
     * 带系统指令的生成
     * @param systemInstruction 系统指令
     * @param userMessage 用户消息
     * @param modelType 模型类型
     * @return 生成的文本
     */
    String generateWithSystem(String systemInstruction, String userMessage, String modelType);
    
    /**
     * 使用指定配置ID生成文本
     * @param systemInstruction 系统指令
     * @param userMessage 用户消息
     * @param llmConfigId 模型配置ID
     * @return 生成的文本
     */
    String generateWithConfig(String systemInstruction, String userMessage, String llmConfigId);
    
    /**
     * 根据特定用途生成文本（自动实现Token优化策略）
     * @param systemInstruction 系统指令
     * @param userMessage 用户消息
     * @param usageType 用途(outline/writing/summary/audit)
     * @return 生成的文本
     */
    String generateWithUsage(String systemInstruction, String userMessage, String usageType);
    
    /**
     * 根据特定用途生成文本（指定用户ID，用于精确匹配用户配置）
     * @param systemInstruction 系统指令
     * @param userMessage 用户消息
     * @param usageType 用途(outline/writing/summary/audit)
     * @param userId 用户ID
     * @return 生成的文本
     */
    String generateWithUsage(String systemInstruction, String userMessage, String usageType, String userId);
    
    /**
     * 获取嵌入向量
     * @param text 文本
     * @return 嵌入向量
     */
    float[] getEmbedding(String text);
    /**
     * 重排序 (Rerank)
     * 使用LLM作为裁判，对召回的文档进行相关性重排序
     * @param query 查询语句
     * @param documents 待排序的文档列表
     * @return 排序后的文档及其相关性得分
     */
    java.util.List<java.util.Map.Entry<String, Double>> rerank(String query, java.util.List<String> documents);
    
    /**
     * 获取LangChain4j ChatModel实例 - 供Agent直接使用
     * @param llmConfigId 可选的模型配置ID，null则自动选择
     * @return ChatModel实例
     */
    dev.langchain4j.model.chat.ChatModel getModelForAgent(String llmConfigId);

    /**
     * 根据用途和用户获取ChatModel实例（统一模型选择逻辑）
     * @param usageType 用途(outline/writing/summary/audit/all)
     * @param userId 用户ID，null则全局兜底
     * @param taskType 任务类型，null则使用默认温度
     * @return ChatModel实例
     */
    dev.langchain4j.model.chat.ChatModel getModelForUsage(String usageType, String userId,
                                                           com.novel.system.config.LangChainConfig.TaskType taskType);

    /**
     * 使用自定义 maxTokens 生成文本（用于需要更大输出的场景，如文风分析）
     * @param systemInstruction 系统指令
     * @param userMessage 用户消息
     * @param usageType 用途
     * @param userId 用户ID
     * @param maxTokens 最大输出 token 数
     * @param configId 可选的模型配置ID，为 null 时自动选择
     * @return 生成的文本
     */
    String generateWithMaxTokens(String systemInstruction, String userMessage,
                                  String usageType, String userId, int maxTokens, String configId);
}
