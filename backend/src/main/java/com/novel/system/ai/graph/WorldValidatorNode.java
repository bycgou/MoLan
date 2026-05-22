package com.novel.system.ai.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.system.entity.mysql.NovelBase;
import com.novel.system.mapper.NovelBaseMapper;
import com.novel.system.util.JsonExtractor;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 写前校验节点 — 校验策划方案与世界观一致性
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorldValidatorNode implements NovelGraphNode {

    private final ChatModel validatorModel;
    private final NovelBaseMapper novelBaseMapper;
    private final ObjectMapper objectMapper;

    @Override
    public String nodeName() { return "world_validate"; }

    @Override
    public NovelAgentState apply(NovelAgentState state) throws Exception {
        log.info("[{}] 写前校验", nodeName());

        NovelAgentState.ChapterPlan plan = state.getChapterPlan();
        NovelBase novel = novelBaseMapper.selectById(state.getNovelId());

        String prompt = String.format("""
                你是严格的小说世界观架构师。校验以下章节策划。

                === 章节策划 ===
                标题：%s
                概要：%s
                角色动线：%s
                伏笔操作：%s

                === 世界观设定 ===
                %s

                === 小说总纲 ===
                %s

                输出JSON：
                {"passed":true/false,"violations":["违反项"],"warnings":["警告"],"suggestions":["建议"]}
                violations为空时passed才为true
                """,
                plan.getTitle(), plan.getSummary(),
                formatCharacterActions(plan.getCharacterActions()),
                formatClueActions(plan.getClueActions()),
                novel.getWorldSetting(), novel.getTotalOutline());

        String response = validatorModel.chat(prompt);
        NovelAgentState.PreWriteValidation validation = parseValidation(response);

        NovelAgentState updated = state.toBuilder()
                .preWriteValidation(validation)
                .currentPhase(NovelAgentState.GenerationPhase.PRE_WRITE_VALID.name())
                .build();

        return StateHelper.appendTrace(updated, nodeName());
    }

    private NovelAgentState.PreWriteValidation parseValidation(String json) {
        try {
            String cleaned = JsonExtractor.extractJson(json);
            JsonNode node = objectMapper.readTree(cleaned);
            List<String> violations = new ArrayList<>();
            if (node.has("violations") && node.get("violations").isArray())
                node.get("violations").forEach(v -> violations.add(v.asText()));
            List<String> warnings = new ArrayList<>();
            if (node.has("warnings") && node.get("warnings").isArray())
                node.get("warnings").forEach(w -> warnings.add(w.asText()));
            List<String> suggestions = new ArrayList<>();
            if (node.has("suggestions") && node.get("suggestions").isArray())
                node.get("suggestions").forEach(s -> suggestions.add(s.asText()));
            return NovelAgentState.PreWriteValidation.builder()
                    .passed(node.path("passed").asBoolean(violations.isEmpty()))
                    .violations(violations).warnings(warnings).suggestions(suggestions)
                    .build();
        } catch (Exception e) {
            log.error("解析校验结果失败: {}", e.getMessage());
            return NovelAgentState.PreWriteValidation.builder().passed(true).build();
        }
    }

    private String formatCharacterActions(List<NovelAgentState.CharacterAction> actions) {
        if (actions == null || actions.isEmpty()) return "无";
        return actions.stream()
                .map(a -> String.format("%s(%s): %s, 情绪:%s",
                        a.getCharacterName(), a.getRole(), a.getGoal(), a.getEmotionalState()))
                .collect(Collectors.joining("\n"));
    }

    private String formatClueActions(List<NovelAgentState.ClueAction> actions) {
        if (actions == null || actions.isEmpty()) return "无";
        return actions.stream()
                .map(a -> String.format("[%s] %s: %s", a.getAction(), a.getClueId(), a.getDescription()))
                .collect(Collectors.joining("\n"));
    }
}
