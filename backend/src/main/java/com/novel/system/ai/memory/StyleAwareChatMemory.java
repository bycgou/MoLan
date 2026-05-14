package com.novel.system.ai.memory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.memory.ChatMemory;

import java.util.ArrayList;
import java.util.List;

/**
 * 带文风规则的聊天记忆
 *
 * 在系统消息中注入文风禁用规则和对话规则（硬约束），
 * 同时保留基础系统指令。
 *
 * 【设计原理】
 * LangChain4j的AiServices在有@SystemMessage注解时会自动将系统消息加入ChatMemory。
 * 本实现拦截该系统消息，将其与文风硬约束合并后再存储，避免重复。
 *
 * @since 1.0.0
 */
public class StyleAwareChatMemory implements ChatMemory {

    private static final String MEMORY_ID = "style-aware-writing";

    private final List<ChatMessage> messages = new ArrayList<>();
    private final String baseSystemMessage;
    private final String styleRules;

    /**
     * @param baseSystemMessage 基础系统指令（来自WritingAgent的@SystemMessage内容）
     * @param styleRules        文风硬约束（禁用规则 + 对话规则），可为空
     */
    public StyleAwareChatMemory(String baseSystemMessage, String styleRules) {
        this.baseSystemMessage = baseSystemMessage;
        this.styleRules = styleRules;
    }

    @Override
    public Object id() {
        return MEMORY_ID;
    }

    @Override
    public void add(ChatMessage message) {
        if (message instanceof SystemMessage && !hasSystemMessage()) {
            // 拦截第一个SystemMessage（来自@SystemMessage注解），
            // 替换为 baseSystemMessage + styleRules 的合并版本
            String fullSystem = baseSystemMessage;
            if (styleRules != null && !styleRules.isEmpty()) {
                fullSystem += styleRules;
            }
            messages.add(new SystemMessage(fullSystem));
            return;
        }
        messages.add(message);
    }

    @Override
    public List<ChatMessage> messages() {
        return new ArrayList<>(messages);
    }

    @Override
    public void clear() {
        messages.clear();
    }

    private boolean hasSystemMessage() {
        return messages.stream().anyMatch(m -> m instanceof SystemMessage);
    }
}
