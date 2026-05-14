package com.novel.system.service.event;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.system.entity.mongo.EventNode;
import com.novel.system.service.llm.LlmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于LLM的事件提取服务
 * 从章节内容中提取关键事件，生成EventNode对象。
 * 提取失败时静默降级，不影响主流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventExtractionService {

    private final LlmService llmService;
    private final ObjectMapper objectMapper;

    /**
     * 从章节内容中提取事件列表
     *
     * @param novelId 小说ID
     * @param chapterNum 章节号
     * @param content 章节内容
     * @param knownCharacters 已知角色名称集合
     * @return 提取的事件节点列表（可能为空，不会为null）
     */
    public List<EventNode> extractEvents(String novelId, int chapterNum,
                                          String content, Set<String> knownCharacters) {
        List<EventNode> events = new ArrayList<>();
        if (content == null || content.isEmpty()) return events;

        try {
            // 截取内容避免token超限
            String contentForExtraction = content.length() > 4000
                ? content.substring(0, 4000) : content;

            String systemPrompt = "你是一个小说事件分析助手。请从章节内容中提取关键事件。" +
                "每个事件描述不超过200字。只返回JSON，不要其他内容。";

            String userMessage = String.format(
                "已知角色：%s\n\n" +
                "章节内容：\n%s\n\n" +
                "请返回JSON数组，每个元素包含：\n" +
                "- type: 事件类型（dialogue=对话, action=动作, discovery=发现, " +
                "foreshadow=伏笔, conflict=冲突, transition=过渡）\n" +
                "- content: 事件描述（200字以内，保留关键细节如对话内容、动作描写）\n" +
                "- characters: 参与角色名称数组\n" +
                "- location: 事件地点（如能识别）\n" +
                "- importance: 重要性评分1-10（10=最关键）\n" +
                "- tags: 标签数组（如：首次出场, 伏笔埋设, 实力突破, 获得物品, 关系变化等）\n\n" +
                "提取规则：\n" +
                "1. 优先提取：关键对话、重要动作、伏笔埋设、冲突事件\n" +
                "2. 次要提取：场景转换、物品获取、实力变化\n" +
                "3. 忽略：日常描写、环境渲染、无意义过渡\n" +
                "4. 每章提取3-8个事件\n\n" +
                "只返回JSON数组，不要其他内容。",
                knownCharacters.toString(), contentForExtraction
            );

            String response = llmService.generateWithUsage(systemPrompt, userMessage, "summary");
            String jsonStr = extractJson(response);
            List<Map<String, Object>> extracted = objectMapper.readValue(
                jsonStr, new TypeReference<>() {});

            for (Map<String, Object> data : extracted) {
                try {
                    EventNode node = new EventNode();
                    node.setEventId(UUID.randomUUID().toString().replace("-", "").substring(0, 32));
                    node.setNovelId(novelId);
                    node.setChapter(chapterNum);
                    node.setType(getStringOrDefault(data, "type", "action"));
                    node.setContent(truncateTo200(getStringOrDefault(data, "content", "")));
                    node.setLocation((String) data.get("location"));
                    node.setImportance(getIntOrDefault(data, "importance", 5));
                    node.setCreateTime(LocalDateTime.now());

                    // 解析角色列表
                    List<String> chars = (List<String>) data.getOrDefault("characters", Collections.emptyList());
                    node.setCharacters(chars != null ? chars : Collections.emptyList());

                    // 解析标签列表
                    List<String> tags = (List<String>) data.getOrDefault("tags", Collections.emptyList());
                    node.setTags(tags != null ? tags : Collections.emptyList());

                    node.setLinkedEvents(Collections.emptyList());

                    if (node.getContent() != null && !node.getContent().isBlank()) {
                        events.add(node);
                    }
                } catch (Exception e) {
                    log.warn("解析单个事件失败: {}", e.getMessage());
                }
            }

            log.info("LLM事件提取完成: novelId={}, chapter={}, 提取{}个事件", novelId, chapterNum, events.size());

        } catch (Exception e) {
            log.warn("LLM事件提取失败（不影响后处理）: {}", e.getMessage());
        }

        return events;
    }

    /**
     * 从LLM响应中提取JSON
     */
    private String extractJson(String response) {
        if (response == null) return "[]";
        String trimmed = response.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                trimmed = trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }
        int jsonStart = trimmed.indexOf('[');
        if (jsonStart < 0) jsonStart = trimmed.indexOf('{');
        if (jsonStart > 0) trimmed = trimmed.substring(jsonStart);
        int jsonEndArray = trimmed.lastIndexOf(']');
        int jsonEndObj = trimmed.lastIndexOf('}');
        int jsonEnd = Math.max(jsonEndArray, jsonEndObj);
        if (jsonEnd > 0) trimmed = trimmed.substring(0, jsonEnd + 1);
        return trimmed;
    }

    private String getStringOrDefault(Map<String, Object> map, String key, String defaultVal) {
        Object val = map.get(key);
        return val instanceof String s && !s.isBlank() ? s : defaultVal;
    }

    private int getIntOrDefault(Map<String, Object> map, String key, int defaultVal) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.intValue();
        return defaultVal;
    }

    private String truncateTo200(String text) {
        if (text == null) return null;
        return text.length() > 200 ? text.substring(0, 200) : text;
    }
}
