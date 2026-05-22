package com.novel.system.ai.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.system.entity.mongo.ArcOrchestration;
import com.novel.system.entity.mysql.ClueBook;
import com.novel.system.service.clue.ClueBookService;
import com.novel.system.service.orchestration.ArcOrchestrationService;
import com.novel.system.util.JsonExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 伏笔规划节点 — 读取卷级编排硬约束 + LLM 补充
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CluePlanningNode implements NovelGraphNode {

    private final ChatModelProvider chatModelProvider;
    private final ClueBookService clueBookService;
    private final ArcOrchestrationService arcOrchestrationService;
    private final ObjectMapper objectMapper;

    @Override
    public String nodeName() { return "clue_planning"; }

    @Override
    public NovelAgentState apply(NovelAgentState state) throws Exception {
        log.info("[{}] 伏笔规划", nodeName());

        NovelAgentState.ChapterPlan plan = state.getChapterPlan();

        // 1. 获取卷级编排硬约束
        List<NovelAgentState.ClueAction> hardConstraints = new ArrayList<>();
        ArcOrchestration.ChapterPlan chapterPlan = arcOrchestrationService
                .getChapterPlan(state.getNovelId(), state.getChapterNum());

        if (chapterPlan != null && chapterPlan.getCluePlan() != null) {
            ArcOrchestration.CluePlan cp = chapterPlan.getCluePlan();
            if (cp.getBury() != null) {
                for (String clue : cp.getBury()) {
                    hardConstraints.add(NovelAgentState.ClueAction.builder()
                            .clueId(clue).action("PLANT")
                            .description("卷级编排要求埋设: " + clue)
                            .fromOrchestration(true).build());
                }
            }
            if (cp.getRecycle() != null) {
                for (String clue : cp.getRecycle()) {
                    hardConstraints.add(NovelAgentState.ClueAction.builder()
                            .clueId(clue).action("RESOLVE")
                            .description("卷级编排要求回收: " + clue)
                            .fromOrchestration(true).build());
                }
            }
        }

        // 2. 获取未回收伏笔
        List<ClueBook> unresolved = clueBookService.getUnrecycledClues(state.getNovelId());
        String cluesText = unresolved.stream()
                .map(c -> String.format("- 第%d章埋：%s%s",
                        c.getBuryChapter(), c.getClueContent(),
                        c.getDeadline() != null ? "（建议" + c.getDeadline() + "章前回收）" : ""))
                .collect(Collectors.joining("\n"));

        // 3. LLM 补充
        String hardText = hardConstraints.stream()
                .map(c -> String.format("[%s] %s: %s", c.getAction(), c.getClueId(), c.getDescription()))
                .collect(Collectors.joining("\n"));

        String prompt = String.format("""
                你是伏笔管理专家。规划第%d章的伏笔操作。

                === 章节概要 ===
                %s

                === 未回收伏笔 ===
                %s

                === 卷级编排已指定（必须执行，不要重复） ===
                %s

                只补充卷级编排未覆盖的伏笔操作。输出JSON数组：
                [{"clueId":"描述","action":"ADVANCE","description":"操作"}]
                action: PLANT/ADVANCE/RESOLVE/SKIP
                规则：超过deadline必须RESOLVE，每章最多RESOLVE 2个，至少ADVANCE 1个
                """,
                state.getChapterNum(), plan.getSummary(), cluesText, hardText);

        String response = chatModelProvider.getStructuredModel(state.getLlmConfigId()).chat(prompt);
        List<NovelAgentState.ClueAction> supplement = parseClueActions(response);

        List<NovelAgentState.ClueAction> allActions = new ArrayList<>(hardConstraints);
        allActions.addAll(supplement);

        NovelAgentState updated = state.toBuilder()
                .chapterPlan(plan.toBuilder().clueActions(allActions).build())
                .currentPhase(NovelAgentState.GenerationPhase.CLUE_PLAN.name())
                .build();

        return StateHelper.appendTrace(updated, nodeName());
    }

    private List<NovelAgentState.ClueAction> parseClueActions(String json) {
        try {
            String cleaned = JsonExtractor.extractJson(json);
            JsonNode arr = objectMapper.readTree(cleaned);
            List<NovelAgentState.ClueAction> actions = new ArrayList<>();
            if (arr.isArray()) {
                for (JsonNode node : arr) {
                    actions.add(NovelAgentState.ClueAction.builder()
                            .clueId(node.path("clueId").asText(""))
                            .action(node.path("action").asText("SKIP"))
                            .description(node.path("description").asText(""))
                            .fromOrchestration(false).build());
                }
            }
            return actions;
        } catch (Exception e) {
            log.error("解析伏笔操作失败: {}", e.getMessage());
            return List.of();
        }
    }
}
