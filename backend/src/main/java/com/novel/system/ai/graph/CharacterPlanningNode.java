package com.novel.system.ai.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.system.service.character.CharacterStateService;
import com.novel.system.util.JsonExtractor;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 角色动线规划节点 — 先程序化校验再 LLM 规划
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CharacterPlanningNode implements NovelGraphNode {

    private final ChatModel plannerModel;
    private final CharacterStateService characterStateService;
    private final ObjectMapper objectMapper;

    @Override
    public String nodeName() { return "character_planning"; }

    @Override
    public NovelAgentState apply(NovelAgentState state) throws Exception {
        log.info("[{}] 角色动线规划", nodeName());

        NovelAgentState.ChapterPlan plan = state.getChapterPlan();
        String charStates = characterStateService.formatForPrompt(state.getNovelId());

        String prompt = String.format("""
                你是角色设计专家。为第%d章设计角色动线。

                === 章节概要 ===
                %s

                === 角色当前状态 ===
                %s

                输出JSON数组：
                [{"characterName":"名","characterId":"id","role":"PROTAGONIST",
                  "goal":"目标","emotionalState":"情绪","keyAction":"行为",
                  "interactions":["互动"]}]
                规则：行为必须符合当前状态，角色间要有互动和冲突
                """,
                state.getChapterNum(), plan.getSummary(), charStates);

        String response = plannerModel.chat(prompt);
        List<NovelAgentState.CharacterAction> actions = parseCharacterActions(response);

        // 程序化校验
        List<NovelAgentState.CharacterAction> validated = new ArrayList<>();
        for (NovelAgentState.CharacterAction action : actions) {
            try {
                boolean allowed = characterStateService.isBehaviorAllowed(
                        state.getNovelId(), action.getCharacterId(),
                        action.getKeyAction(), state.getChapterNum());
                if (!allowed) {
                    log.warn("角色行为被校验拒绝: {} -> {}", action.getCharacterName(), action.getKeyAction());
                    validated.add(action.toBuilder()
                            .keyAction("[需审查] " + action.getKeyAction())
                            .build());
                } else {
                    validated.add(action);
                }
            } catch (Exception e) {
                log.warn("角色行为校验异常（跳过）: {}", e.getMessage());
                validated.add(action);
            }
        }

        NovelAgentState updated = state.toBuilder()
                .chapterPlan(plan.toBuilder().characterActions(validated).build())
                .currentPhase(NovelAgentState.GenerationPhase.CHAR_PLAN.name())
                .build();

        return StateHelper.appendTrace(updated, nodeName());
    }

    private List<NovelAgentState.CharacterAction> parseCharacterActions(String json) {
        try {
            String cleaned = JsonExtractor.extractJson(json);
            JsonNode arr = objectMapper.readTree(cleaned);
            List<NovelAgentState.CharacterAction> actions = new ArrayList<>();
            if (arr.isArray()) {
                for (JsonNode node : arr) {
                    List<String> interactions = new ArrayList<>();
                    if (node.has("interactions") && node.get("interactions").isArray()) {
                        node.get("interactions").forEach(i -> interactions.add(i.asText()));
                    }
                    actions.add(NovelAgentState.CharacterAction.builder()
                            .characterName(node.path("characterName").asText(""))
                            .characterId(node.path("characterId").asText(""))
                            .role(node.path("role").asText("SUPPORTING"))
                            .goal(node.path("goal").asText(""))
                            .emotionalState(node.path("emotionalState").asText(""))
                            .keyAction(node.path("keyAction").asText(""))
                            .interactions(interactions).build());
                }
            }
            return actions;
        } catch (Exception e) {
            log.error("解析角色动线失败: {}", e.getMessage());
            return List.of();
        }
    }
}
