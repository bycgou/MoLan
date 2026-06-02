package com.novel.system.ai.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.system.ai.chain.ChapterGenerationChain;
import com.novel.system.util.JsonExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 摘要生成节点 — 复用 SummaryAgent
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SummaryNode implements NovelGraphNode {

    private final ChapterGenerationChain chain;
    private final ObjectMapper objectMapper;

    @Override
    public String nodeName() { return "summary"; }

    @Override
    public boolean shouldSkip(NovelAgentState state) {
        return state.getFinalResult() != null && state.getFinalResult().getSummary() != null;
    }

    @Override
    public NovelAgentState apply(NovelAgentState state) throws Exception {
        log.info("[{}] 生成摘要", nodeName());

        NovelAgentState.DraftContent draft = state.getDraftContent();
        if (draft == null || draft.getContent() == null) {
            log.warn("[{}] draft 为空，跳过摘要生成", nodeName());
            return state.toBuilder()
                    .currentPhase(NovelAgentState.GenerationPhase.SUMMARY.name())
                    .build();
        }
        NovelAgentState.ChapterPlan plan = state.getChapterPlan();

        String summaryJson = chain.executeSummaryChain(
                state.getNovelId(), state.getChapterNum(),
                draft.getContent(), plan.getSummary(), state.getLlmConfigId());

        String fingerprint = parseFingerprint(summaryJson);

        double qualityScore = 0;
        if (state.getAuditResult() != null) {
            qualityScore = state.getAuditResult().getQualityScore();
        }

        NovelAgentState.FinalResult finalResult = NovelAgentState.FinalResult.builder()
                .finalContent(draft.getContent())
                .wordCount(draft.getWordCount())
                .qualityScore(qualityScore)
                .summary(fingerprint)
                .structuredSummaryJson(summaryJson)
                .build();

        NovelAgentState updated = state.toBuilder()
                .finalResult(finalResult)
                .currentPhase(NovelAgentState.GenerationPhase.SUMMARY.name())
                .build();

        return StateHelper.appendTrace(updated, nodeName());
    }

    private JsonNode parseJsonRoot(String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            String cleaned = JsonExtractor.extractJson(json);
            return objectMapper.readTree(cleaned);
        } catch (Exception e) {
            return null;
        }
    }

    private String parseFingerprint(String json) {
        JsonNode node = parseJsonRoot(json);
        if (node == null) {
            return json == null ? "摘要生成异常" : json.substring(0, Math.min(200, json.length()));
        }
        return node.path("fingerprint").asText("");
    }

    public Map<String, Object> parseStructuredSummary(String json) {
        Map<String, Object> result = new HashMap<>();
        JsonNode node = parseJsonRoot(json);
        if (node == null) return result;
        result.put("fingerprint", node.path("fingerprint").asText(""));
        result.put("strand_type", node.path("strand_type").asText(""));
        result.put("hook_level", node.path("hook_level").asText(""));
        result.put("core_events", parseStringArray(node.path("core_events")));
        result.put("characters", parseStringArray(node.path("characters")));
        result.put("locations", parseStringArray(node.path("locations")));
        result.put("keywords", parseStringArray(node.path("keywords")));
        return result;
    }

    private List<String> parseStringArray(JsonNode arrayNode) {
        List<String> list = new ArrayList<>();
        if (arrayNode != null && arrayNode.isArray()) {
            arrayNode.forEach(item -> list.add(item.asText("")));
        }
        return list;
    }
}
