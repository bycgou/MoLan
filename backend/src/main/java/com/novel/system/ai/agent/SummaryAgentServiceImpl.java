package com.novel.system.ai.agent;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 摘要Agent服务实现 - 基于LangChain4j ChatLanguageModel
 * 
 * 使用LangChain4j的消息类构建Prompt，通过ChatLanguageModel直接调用LLM，
 * 替代原来的手动HTTP调用方式。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SummaryAgentServiceImpl implements SummaryAgentService {
    
    private final LlmService llmService;
    private final PromptTemplateService promptTemplateService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public SummaryAgentResponse generate(SummaryAgentRequest request) {
        log.info("开始生成剧情指纹，小说ID: {}, 章节号: {}", request.getNovelId(), request.getChapterNum());
        
        // 1. 构建Prompt变量
        Map<String, Object> variables = buildVariables(request);
        
        // 2. 渲染系统提示词
        String systemInstruction = promptTemplateService.renderPrompt("summary", variables);
        
        // 3. 构建用户消息（含JSON格式指令）
        String userMessage = "请根据以上规则，生成本章的剧情指纹。" + buildJsonFormatInstruction();
        
        // 4. 获取LangChain4j模型并调用
        ChatLanguageModel model = llmService.getModelForAgent(request.getLlmConfigId());
        List<ChatMessage> messages = List.of(
            new SystemMessage(systemInstruction),
            new UserMessage(userMessage)
        );
        
        ChatResponse response = model.chat(ChatRequest.builder().messages(messages).build());
        String responseText = response.aiMessage().text();
        
        // 5. 解析响应
        return parseSummaryResponse(responseText, request);
    }
    
    private Map<String, Object> buildVariables(SummaryAgentRequest request) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("novel_genre", request.getNovelGenre() != null ? request.getNovelGenre() : "玄幻");
        variables.put("chapter_content", truncateContent(request.getChapterContent(), 3000));
        variables.put("chapter_title", request.getChapterTitle() != null ? request.getChapterTitle() : "无");
        variables.put("chapter_outline", request.getChapterOutline() != null ? request.getChapterOutline() : "无");
        return variables;
    }
    
    private String buildJsonFormatInstruction() {
        return """
        
        请严格按照以下JSON格式输出：
        {
          "fingerprint": "300字以内的剧情指纹摘要",
          "keywords": ["关键词1", "关键词2", "关键词3"],
          "characters": ["出场人物1", "出场人物2"],
          "locations": ["出场地点1", "出场地点2"],
          "relations": [
            {"source": "人物A", "target": "人物B", "relation": "师徒/敌对/恋人等"}
          ]
        }
        """;
    }
    
    private SummaryAgentResponse parseSummaryResponse(String responseText, SummaryAgentRequest request) {
        SummaryAgentResponse result = new SummaryAgentResponse();
        
        try {
            String cleanResponse = cleanMarkdownJson(responseText);
            JsonNode rootNode = objectMapper.readTree(cleanResponse);
            
            result.setFingerprint(rootNode.has("fingerprint") ? rootNode.get("fingerprint").asText() : truncateToWords(responseText, 300));
            
            List<String> keywords = new ArrayList<>();
            if (rootNode.has("keywords") && rootNode.get("keywords").isArray()) {
                for (JsonNode node : rootNode.get("keywords")) keywords.add(node.asText());
            }
            result.setKeywords(keywords.isEmpty() ? extractKeywords(responseText) : keywords);
            
            List<String> characters = new ArrayList<>();
            if (rootNode.has("characters") && rootNode.get("characters").isArray()) {
                for (JsonNode node : rootNode.get("characters")) characters.add(node.asText());
            }
            result.setCharacters(characters.isEmpty() ? extractCharacters(request.getChapterContent()) : characters);
            
            List<String> locations = new ArrayList<>();
            if (rootNode.has("locations") && rootNode.get("locations").isArray()) {
                for (JsonNode node : rootNode.get("locations")) locations.add(node.asText());
            }
            result.setLocations(locations.isEmpty() ? extractLocations(request.getChapterContent()) : locations);
            
            if (rootNode.has("relations") && rootNode.get("relations").isArray()) {
                List<Map<String, String>> relations = new ArrayList<>();
                for (JsonNode node : rootNode.get("relations")) {
                    if (node.has("source") && node.has("target") && node.has("relation")) {
                        Map<String, String> rel = new HashMap<>();
                        rel.put("source", node.get("source").asText());
                        rel.put("target", node.get("target").asText());
                        rel.put("relation", node.get("relation").asText());
                        relations.add(rel);
                    }
                }
                result.setRelations(relations);
            }
            
        } catch (Exception e) {
            log.error("摘要JSON解析失败，退回正则提取: {}", e.getMessage());
            result.setFingerprint(truncateToWords(responseText, 300));
            result.setKeywords(extractKeywords(responseText));
            result.setCharacters(extractCharacters(request.getChapterContent()));
            result.setLocations(extractLocations(request.getChapterContent()));
        }
        
        log.info("剧情指纹生成完成，章节号: {}, 指纹长度: {}", 
            request.getChapterNum(), result.getFingerprint().length());
        
        return result;
    }
    
    private String cleanMarkdownJson(String text) {
        String cleaned = text.trim();
        if (cleaned.startsWith("```json")) cleaned = cleaned.substring(7);
        else if (cleaned.startsWith("```")) cleaned = cleaned.substring(3);
        if (cleaned.endsWith("```")) cleaned = cleaned.substring(0, cleaned.length() - 3);
        return cleaned.trim();
    }
    
    private String truncateContent(String content, int maxLength) {
        if (content == null) return "";
        if (content.length() <= maxLength) return content;
        return content.substring(0, maxLength) + "...(内容已截断)";
    }
    
    private String truncateToWords(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen);
    }
    
    private List<String> extractKeywords(String text) {
        List<String> keywords = new ArrayList<>();
        if (text != null) {
            String[] parts = text.split("[，。、；：]");
            for (String part : parts) {
                if (part.length() >= 2 && part.length() <= 10) {
                    keywords.add(part.trim());
                }
            }
        }
        return keywords.subList(0, Math.min(10, keywords.size()));
    }
    
    private List<String> extractCharacters(String content) {
        List<String> characters = new ArrayList<>();
        Pattern pattern = Pattern.compile("([\\u4e00-\\u9fa5]{2,4})(?:说|道|问|答|想|看|走|跑)");
        Matcher matcher = pattern.matcher(content != null ? content : "");
        Set<String> found = new HashSet<>();
        while (matcher.find()) {
            String name = matcher.group(1);
            if (found.add(name)) characters.add(name);
        }
        return characters;
    }
    
    private List<String> extractLocations(String content) {
        List<String> locations = new ArrayList<>();
        Pattern pattern = Pattern.compile("在([\\u4e00-\\u9fa5]{2,6})(?:里|中|旁|边|上)");
        Matcher matcher = pattern.matcher(content != null ? content : "");
        Set<String> found = new HashSet<>();
        while (matcher.find()) {
            String location = matcher.group(1);
            if (found.add(location)) locations.add(location);
        }
        return locations;
    }
}
