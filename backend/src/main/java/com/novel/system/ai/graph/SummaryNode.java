package com.novel.system.ai.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.system.ai.chain.ChapterGenerationChain;
import com.novel.system.util.JsonExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
    public NovelAgentState apply(NovelAgentState state) throws Exception {
        log.info("[{}] 生成摘要", nodeName());

        NovelAgentState.DraftContent draft = state.getDraftContent();
        NovelAgentState.ChapterPlan plan = state.getChapterPlan();

        String summaryJson = chain.executeSummaryChain(
                state.getNovelId(), state.getChapterNum(),
                draft.getContent(), plan.getSummary(), state.getLlmConfigId());

        String fingerprint = parseFingerprint(summaryJson);

        NovelAgentState.FinalResult finalResult = NovelAgentState.FinalResult.builder()
                .finalContent(draft.getContent())
                .wordCount(draft.getWordCount())
                .qualityScore(state.getAuditResult().getQualityScore())
                .summary(fingerprint)
                .build();

        NovelAgentState updated = state.toBuilder()
                .finalResult(finalResult)
                .currentPhase(NovelAgentState.GenerationPhase.SUMMARY.name())
                .build();

        return StateHelper.appendTrace(updated, nodeName());
    }

    private String parseFingerprint(String json) {
        try {
            String cleaned = JsonExtractor.extractJson(json);
            JsonNode node = objectMapper.readTree(cleaned);
            return node.path("fingerprint").asText("");
        } catch (Exception e) {
            return json.substring(0, Math.min(200, json.length()));
        }
    }
}
