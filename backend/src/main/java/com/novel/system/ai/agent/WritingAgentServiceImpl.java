package com.novel.system.ai.agent;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import com.novel.system.service.llm.LlmService;
import com.novel.system.service.llm.PromptTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.*;

/**
 * 写作Agent服务实现 - 基于LangChain4j ChatLanguageModel
 * 
 * 使用LangChain4j的消息类构建Prompt，通过ChatLanguageModel直接调用LLM，
 * 替代原来的手动HTTP调用方式。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WritingAgentServiceImpl implements WritingAgentService {
    
    private final LlmService llmService;
    private final PromptTemplateService promptTemplateService;
    
    @Override
    public WritingAgentResponse generate(WritingAgentRequest request) {
        log.info("开始生成正文，小说ID: {}, 章节号: {}", request.getNovelId(), request.getChapterNum());
        
        // 1. 构建Prompt变量
        Map<String, Object> variables = buildVariables(request);
        
        // 2. 渲染系统提示词
        String systemInstruction = promptTemplateService.renderPrompt("writing", variables);
        
        // 3. 构建用户消息
        String userMessage = buildUserMessage(request);
        
        // 4. 获取LangChain4j模型并调用
        ChatLanguageModel model = llmService.getModelForAgent(request.getLlmConfigId());
        List<ChatMessage> messages = List.of(
            new SystemMessage(systemInstruction),
            new UserMessage(userMessage)
        );
        
        ChatResponse response = model.chat(ChatRequest.builder().messages(messages).build());
        String responseText = response.aiMessage().text();
        
        // 5. 计算字数并返回
        int wordCount = countChineseChars(responseText);
        
        WritingAgentResponse result = new WritingAgentResponse();
        result.setContent(responseText);
        result.setWordCount(wordCount);
        
        log.info("正文生成完成，章节号: {}, 字数: {}", request.getChapterNum(), wordCount);
        return result;
    }
    
    private Map<String, Object> buildVariables(WritingAgentRequest request) {
        Map<String, Object> variables = new HashMap<>();
        
        variables.put("chapter_outline", request.getChapterOutline() != null ? request.getChapterOutline() : "章纲待生成");
        variables.put("selected_idea", request.getSelectedIdea() != null ? request.getSelectedIdea() : "无");
        variables.put("context", request.getContext() != null ? request.getContext() : "无");
        variables.put("character_states", request.getCharacterStates() != null ? request.getCharacterStates() : "暂无人物设定");
        variables.put("novel_genre", request.getNovelGenre() != null ? request.getNovelGenre() : "玄幻");
        variables.put("user_intent", request.getUserIntent() != null ? request.getUserIntent() : "无特殊要求");
        
        String totalOutline = request.getTotalOutline();
        if (totalOutline != null && totalOutline.length() > 2000) {
            totalOutline = totalOutline.substring(0, 2000) + "\n...(已截断)";
        }
        variables.put("total_outline", totalOutline != null ? totalOutline : "无");
        
        variables.put("arc_outline", request.getArcOutline() != null ? request.getArcOutline() : "无");
        variables.put("arc_name", request.getArcName() != null ? request.getArcName() : "未命名卷");
        variables.put("world_setting", request.getWorldSetting() != null ? request.getWorldSetting() : "无");
        variables.put("world_clock", "时间设定");
        variables.put("last_chapter_fingerprint", request.getLastChapterFingerprint() != null ? request.getLastChapterFingerprint() : "无（首章）");
        
        return variables;
    }
    
    private String buildUserMessage(WritingAgentRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("请根据以上规则，生成第").append(request.getChapterNum()).append("章的小说正文。\n\n");
        
        if (request.getTargetWordCount() != null) {
            sb.append("目标字数：").append(request.getTargetWordCount()).append("字左右\n");
        } else {
            sb.append("目标字数：1500字左右\n");
        }
        
        sb.append("\n请直接输出正文内容，不要添加章节标题、说明或任何标记。直接从第一段剧情开始。");
        return sb.toString();
    }
    
    private int countChineseChars(String text) {
        if (text == null) return 0;
        return text.replaceAll("\\s+", "").length();
    }
}
