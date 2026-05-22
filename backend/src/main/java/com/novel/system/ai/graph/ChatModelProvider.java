package com.novel.system.ai.graph;

import com.novel.system.config.LangChainConfig;
import com.novel.system.entity.mysql.LlmConfig;
import com.novel.system.service.llm.LlmConfigService;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * ChatModel 工厂 — 封装 LlmConfigService + LangChainConfig
 * 供图节点调用 LLM 使用
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatModelProvider {

    private final LlmConfigService llmConfigService;
    private final LangChainConfig langChainConfig;

    /**
     * 获取 ChatModel
     *
     * @param llmConfigId 模型配置ID（null 时使用任意活跃配置）
     * @param taskType    任务类型（决定 temperature）
     * @return ChatModel 实例（已缓存）
     */
    public ChatModel getModel(String llmConfigId, LangChainConfig.TaskType taskType) {
        LlmConfig config;
        if (llmConfigId != null && !llmConfigId.isEmpty()) {
            config = llmConfigService.getConfigById(llmConfigId);
        } else {
            config = llmConfigService.getAnyActiveConfig("all");
        }
        if (config == null) {
            throw new RuntimeException("未找到可用的模型配置，请先在模型配置页面添加并激活一个模型");
        }
        return langChainConfig.createChatModel(config, taskType);
    }

    /**
     * 获取创意写作模型（默认 temperature）
     */
    public ChatModel getCreativeModel(String llmConfigId) {
        return getModel(llmConfigId, LangChainConfig.TaskType.CREATIVE);
    }

    /**
     * 获取结构化输出模型（低 temperature）
     */
    public ChatModel getStructuredModel(String llmConfigId) {
        return getModel(llmConfigId, LangChainConfig.TaskType.STRUCTURED);
    }
}
