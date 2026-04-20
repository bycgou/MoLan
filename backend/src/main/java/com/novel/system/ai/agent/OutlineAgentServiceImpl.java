package com.novel.system.ai.agent;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.system.service.llm.LlmService;
import com.novel.system.service.llm.PromptTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.*;

/**
 * 大纲Agent服务实现 - 基于LangChain4j ChatLanguageModel
 * 
 * 使用LangChain4j的消息类构建Prompt，通过ChatLanguageModel直接调用LLM，
 * 替代原来的手动HTTP调用方式。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutlineAgentServiceImpl implements OutlineAgentService {
    
    private final LlmService llmService;
    private final PromptTemplateService promptTemplateService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public OutlineAgentResponse generate(OutlineAgentRequest request) {
        log.info("开始生成章纲，小说ID: {}, 章节号: {}", request.getNovelId(), request.getChapterNum());
        
        // 1. 构建Prompt变量
        Map<String, Object> variables = buildVariables(request);
        
        // 2. 渲染系统提示词
        String systemPrompt = promptTemplateService.renderPrompt("outline", variables);
        systemPrompt += buildJsonFormatInstruction();
        
        // 3. 构建用户消息
        String userMessage = "请根据以上规则，生成第" + request.getChapterNum() + "章的章纲和实现思路。必须输出为合法的JSON格式。";
        
        // 4. 获取LangChain4j模型并调用
        ChatLanguageModel model = llmService.getModelForAgent(request.getLlmConfigId());
        List<ChatMessage> messages = List.of(
            new SystemMessage(systemPrompt),
            new UserMessage(userMessage)
        );
        
        ChatResponse response = model.chat(ChatRequest.builder().messages(messages).build());
        String responseText = response.aiMessage().text();
        log.info("LLM响应长度: {}", responseText.length());
        
        // 5. 解析响应
        return parseOutlineResponse(responseText, request.getChapterNum());
    }
    
    private Map<String, Object> buildVariables(OutlineAgentRequest request) {
        Map<String, Object> variables = new HashMap<>();
        
        String totalOutline = request.getTotalOutline();
        if (totalOutline != null && totalOutline.length() > 4000) {
            totalOutline = totalOutline.substring(0, 4000) + "\n...(总纲已截断)";
        }
        variables.put("total_outline", totalOutline != null ? totalOutline : "总纲待配置");
        
        String arcOutline = request.getArcOutline();
        if (arcOutline != null && arcOutline.length() > 1500) {
            arcOutline = arcOutline.substring(0, 1500) + "\n...(卷纲已截断)";
        }
        variables.put("arc_outline", arcOutline != null ? arcOutline : "卷纲待配置");
        
        variables.put("arc_name", request.getArcName() != null ? request.getArcName() : "未命名卷");
        variables.put("arc_start_chapter", request.getArcStartChapter() != null ? request.getArcStartChapter() : 1);
        variables.put("arc_end_chapter", request.getArcEndChapter() != null ? request.getArcEndChapter() : 100);
        variables.put("world_setting", request.getWorldSetting() != null ? request.getWorldSetting() : "世界观待配置");
        variables.put("world_clock", request.getWorldClock() != null ? request.getWorldClock() : "未设定");
        variables.put("season", request.getSeason() != null ? request.getSeason() : "未设定");
        variables.put("weather", request.getWeather() != null ? request.getWeather() : "未设定");
        variables.put("character_states", request.getCharacterStates() != null ? request.getCharacterStates() : "暂无人物设定");
        variables.put("novel_genre", request.getNovelGenre() != null ? request.getNovelGenre() : "玄幻");
        variables.put("chapter_num", request.getChapterNum());
        variables.put("last_chapter_fingerprint", request.getLastChapterFingerprint() != null ? request.getLastChapterFingerprint() : "无（首章）");
        variables.put("user_intent", request.getUserIntent() != null ? request.getUserIntent() : "无特殊要求");
        variables.put("clues", formatClues(request.getUnrecycledClues()));
        
        return variables;
    }
    
    private String buildJsonFormatInstruction() {
        return """
        
        请严格按照以下JSON格式输出，不要包含任何额外的说明或Markdown代码块标记：
        {
          "outline": "这里是章纲的具体内容，包含场景、情节和冲突",
          "ideas": [
            "实现思路一：...",
            "实现思路二：...",
            "实现思路三：..."
          ]
        }
        """;
    }
    
    private String formatClues(List<String> clues) {
        if (clues == null || clues.isEmpty()) return "无未回收伏笔";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < clues.size(); i++) {
            sb.append(i + 1).append(". ").append(clues.get(i)).append("\n");
        }
        return sb.toString();
    }
    
    private OutlineAgentResponse parseOutlineResponse(String response, Integer chapterNum) {
        OutlineAgentResponse result = new OutlineAgentResponse();
        List<String> defaultIdeas = Arrays.asList(
            "按照章纲直接展开，注重场景描写和人物对话，营造沉浸感",
            "以主角视角为主线，穿插心理活动和环境描写，增强代入感"
        );
        
        try {
            String cleanResponse = cleanMarkdownJson(response);
            JsonNode rootNode = objectMapper.readTree(cleanResponse);
            
            result.setOutline(rootNode.has("outline") && !rootNode.get("outline").isNull()
                ? rootNode.get("outline").asText() : "大纲解析失败，未找到 outline 字段");
            
            if (rootNode.has("ideas") && rootNode.get("ideas").isArray()) {
                List<String> ideas = new ArrayList<>();
                for (JsonNode node : rootNode.get("ideas")) {
                    ideas.add(node.asText());
                }
                result.setImplementationIdeas(ideas.isEmpty() ? defaultIdeas : ideas);
            } else {
                result.setImplementationIdeas(defaultIdeas);
            }
            
            log.info("章纲JSON解析完成，章纲长度: {}, 实现思路数量: {}", 
                result.getOutline().length(), result.getImplementationIdeas().size());
            return result;
            
        } catch (Exception e) {
            log.error("解析大纲JSON失败，退回到原始文本处理: {}", e.getMessage());
            result.setOutline(response.length() > 500 ? response.substring(0, 500) : response);
            result.setImplementationIdeas(defaultIdeas);
            return result;
        }
    }
    
    private String cleanMarkdownJson(String text) {
        String cleaned = text.trim();
        if (cleaned.startsWith("```json")) cleaned = cleaned.substring(7);
        else if (cleaned.startsWith("```")) cleaned = cleaned.substring(3);
        if (cleaned.endsWith("```")) cleaned = cleaned.substring(0, cleaned.length() - 3);
        return cleaned.trim();
    }
}
