package com.novel.system.ai.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.system.service.memory.ConvergenceService;
import com.novel.system.util.JsonExtractor;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 自审节点 — 输出 severity 枚举 + revisionNotes
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SelfReviewNode implements NovelGraphNode {

    private final ChatModelProvider chatModelProvider;
    private final ObjectMapper objectMapper;
    private final ConvergenceService convergenceService;

    @Override
    public String nodeName() { return "self_review"; }

    @Override
    public NovelAgentState apply(NovelAgentState state) throws Exception {
        log.info("[{}] 自审修订", nodeName());

        NovelAgentState.DraftContent draft = state.getDraftContent();
        if (draft == null || draft.getContent() == null) {
            log.warn("[{}] draft 为空，跳过自审", nodeName());
            return state.toBuilder()
                    .currentPhase(NovelAgentState.GenerationPhase.SELF_REVIEW.name())
                    .build();
        }
        NovelAgentState.ChapterPlan plan = state.getChapterPlan();

        // Build convergence-specific review hint
        String phase = convergenceService.getPhase(state.getNovelId());
        String convergenceHint = "";
        if ("CONVERGENCE".equals(phase)) {
            convergenceHint = "\n\n【收束期特别检查】当前处于收束期，请特别注意：\n"
                    + "1. 是否有支线可以合并或闭合？\n"
                    + "2. 是否减少了新支线的引入？\n"
                    + "3. 节奏是否在加速？";
        } else if ("FINALE".equals(phase)) {
            convergenceHint = "\n\n【终章期特别检查】当前处于终章期，请特别注意：\n"
                    + "1. 所有伏笔是否已回收？\n"
                    + "2. 角色弧线是否完整闭合？\n"
                    + "3. 是否还有未解决的冲突？";
        }

        String prompt = String.format("""
                你是严格的文学编辑。审查以下章节初稿。

                === 策划方案 ===
                概要：%s
                情绪弧线：%s
                高潮点：%s

                === 初稿内容 ===
                %s

                检查：情节推进、角色行为、情绪弧线、伏笔体现。%s

                输出JSON：
                {"severity":"LOW/MEDIUM/HIGH","overallScore":8,"passed":true,"notes":["意见"]}
                severity: LOW=轻微可忽略, MEDIUM=建议修改, HIGH=必须重写
                """,
                plan.getSummary(), plan.getEmotionalArc(), plan.getClimaxPoint(),
                StateHelper.truncate(draft.getContent(), 6000),
                convergenceHint);

        String response = chatModelProvider.getStructuredModel(state.getLlmConfigId()).chat(prompt);
        SelfReviewResult result = parseResult(response);

        NovelAgentState updated = state.toBuilder()
                .draftContent(draft.toBuilder()
                        .severity(result.severity)
                        .revisionNotes(result.notes)
                        .build())
                .pendingRevisionNotes(StateHelper.mergeRevisionNotes(
                        state.getPendingRevisionNotes(), result.notes))
                .currentPhase(NovelAgentState.GenerationPhase.SELF_REVIEW.name())
                .build();

        if (result.severity == NovelAgentState.Severity.HIGH) {
            // 自审判定重写时计数：writeRetryCount 此前从未自增，
            // 导致 self_review → writing 的条件恒真、重写次数不可控。
            updated = StateHelper.incrementIteration(updated, "write");
        }

        return updated;
    }

    private SelfReviewResult parseResult(String json) {
        try {
            String cleaned = JsonExtractor.extractJson(json);
            JsonNode node = objectMapper.readTree(cleaned);
            List<String> notes = new ArrayList<>();
            if (node.has("notes") && node.get("notes").isArray())
                node.get("notes").forEach(n -> notes.add(n.asText()));
            NovelAgentState.Severity severity;
            try {
                severity = NovelAgentState.Severity.valueOf(node.path("severity").asText("LOW"));
            } catch (IllegalArgumentException e) {
                severity = NovelAgentState.Severity.LOW;
            }
            return new SelfReviewResult(severity, notes);
        } catch (Exception e) {
            log.error("解析自审结果失败，标记为HIGH: {}", e.getMessage());
            return new SelfReviewResult(NovelAgentState.Severity.HIGH,
                    List.of("自审结果解析失败，建议重写: " + e.getMessage()));
        }
    }

    @Data
    @AllArgsConstructor
    private static class SelfReviewResult {
        NovelAgentState.Severity severity;
        List<String> notes;
    }
}
