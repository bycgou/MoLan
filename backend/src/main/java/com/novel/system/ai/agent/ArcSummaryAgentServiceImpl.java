package com.novel.system.ai.agent;

import com.novel.system.config.LangChainConfig;
import com.novel.system.entity.mysql.LlmConfig;
import com.novel.system.service.llm.LlmConfigService;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 弧段摘要 Agent 实现类
 * 
 * 【核心作用】
 * 实现 ArcSummaryAgent 接口，调用 LLM 生成弧段摘要
 * 
 * @author AI Assistant
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArcSummaryAgentServiceImpl implements ArcSummaryAgent {
    
    private final LangChainConfig langChainConfig;
    private final LlmConfigService llmConfigService;
    
    /**
     * 生成弧段摘要
     * 
     * @param prompt 包含故事线内容的提示
     * @return 200-400字的弧段摘要
     */
    @Override
    public String generateArcSummary(String prompt) {
        try {
            // 获取默认配置
            LlmConfig config = llmConfigService.getAnyActiveConfig("all");
            if (config == null) {
                log.error("没有可用的 LLM 配置");
                return null;
            }
            
            // 创建模型
            ChatLanguageModel chatLanguageModel = langChainConfig.createChatModel(config);
            
            // 构建消息列表
            List<ChatMessage> messages = new ArrayList<>();
            
            // 添加系统消息
            String systemPrompt = """
                你是专业的小说编辑。分析一段故事线，生成简洁的弧段摘要。
                
                【输出要求】
                1. 200-400字
                2. 包含主要情节、人物变化、伏笔埋设和回收
                3. 只输出摘要文本，不要JSON或其他格式
                4. 保持客观，不要添加个人评论
                """;
            messages.add(new SystemMessage(systemPrompt));
            
            // 添加用户消息
            messages.add(new UserMessage(prompt));
            
            // 调用 LLM
            String summary = chatLanguageModel.generate(messages).content().text();
            
            log.info("生成弧段摘要成功，长度: {} 字符", summary.length());
            
            return summary;
            
        } catch (Exception e) {
            log.error("生成弧段摘要失败: {}", e.getMessage(), e);
            return null;
        }
    }
}