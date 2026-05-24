package com.novel.system.ai.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.system.util.JsonExtractor;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 质量评分节点 — 含伏笔落地 LLM 检查
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QualityScoringNode implements NovelGraphNode {

    private final ChatModelProvider chatModelProvider;
    private final ObjectMapper objectMapper;

    @Override
    public String nodeName() { return "quality_scoring"; }

    @Override
    public NovelAgentState apply(NovelAgentState state) throws Exception {
        log.info("[{}] 质量评分 + 伏笔落地检查", nodeName());

        NovelAgentState.AuditResult audit = state.getAuditResult();
        NovelAgentState.DraftContent draft = state.getDraftContent();
        NovelAgentState.ChapterPlan plan = state.getChapterPlan();

        String cluePlanText = formatCluePlan(plan.getClueActions());

        String prompt = String.format("""
                你是网文平台资深编辑。对以下章节评分并检查伏笔落地。

                === 策划 ===
                高潮点：%s
                情绪弧线：%s

                === 伏笔计划 ===
                %s

                === 章节内容 ===
                %s

                任务：
                1. 质量评分（每项0-20，总分100）：情节推进、角色塑造、情感表达、文笔质量、可读性
                2. 伏笔落地检查：每个RESOLVE/ADVANCE的伏笔是否在正文中体现

                输出JSON：
                {"totalScore":80,"qualityPassed":true,"overallAssessment":"评价",
                 "clueLandingPassed":true,"clueLandingIssues":["问题"],"suggestions":["建议"]}
                """,
                plan.getClimaxPoint(), plan.getEmotionalArc(),
                cluePlanText, truncate(draft.getContent(), 6000));

        String response = chatModelProvider.getStructuredModel(state.getLlmConfigId()).chat(prompt);
        QualityResult quality = parseQuality(response);

        // 全部使用 builder，不混用 setter
        boolean allPassed = audit.isOocPassed()
                && audit.isLogicPassed()
                && audit.isCluePassed()
                && audit.isSettingPassed()
                && audit.isRhythmPassed()
                && quality.qualityPassed
                && quality.clueLandingPassed;

        NovelAgentState.AuditResult updatedAudit = audit.toBuilder()
                .qualityScore(quality.totalScore)
                .qualityPassed(quality.qualityPassed)
                .qualityAssessment(quality.assessment)
                .clueLandingPassed(quality.clueLandingPassed)
                .clueLandingIssues(quality.clueLandingIssues)
                .suggestions(mergeSuggestions(audit.getSuggestions(), quality.suggestions))
                .allPassed(allPassed)
                .build();

        NovelAgentState updated = state.toBuilder()
                .auditResult(updatedAudit)
                .currentPhase(NovelAgentState.GenerationPhase.QUALITY_CHECK.name())
                .build();

        return updated;
    }

    private String formatCluePlan(List<NovelAgentState.ClueAction> actions) {
        if (actions == null || actions.isEmpty()) return "无伏笔计划";
        return actions.stream()
                .map(a -> String.format("[%s] %s: %s%s",
                        a.getAction(), a.getClueId(), a.getDescription(),
                        a.isFromOrchestration() ? " (卷级编排)" : ""))
                .collect(Collectors.joining("\n"));
    }

    private List<String> mergeSuggestions(List<String> existing, List<String> newS) {
        List<String> merged = existing != null ? new ArrayList<>(existing) : new ArrayList<>();
        if (newS != null) merged.addAll(newS);
        return merged;
    }

    private QualityResult parseQuality(String json) {
        try {
            String cleaned = JsonExtractor.extractJson(json);
            JsonNode node = objectMapper.readTree(cleaned);
            List<String> suggestions = new ArrayList<>();
            if (node.has("suggestions") && node.get("suggestions").isArray())
                node.get("suggestions").forEach(s -> suggestions.add(s.asText()));
            List<String> clueIssues = new ArrayList<>();
            if (node.has("clueLandingIssues") && node.get("clueLandingIssues").isArray())
                node.get("clueLandingIssues").forEach(i -> clueIssues.add(i.asText()));
            return new QualityResult(
                    node.path("totalScore").asDouble(70),
                    node.path("qualityPassed").asBoolean(true),
                    node.path("overallAssessment").asText(""),
                    node.path("clueLandingPassed").asBoolean(true),
                    clueIssues, suggestions);
        } catch (Exception e) {
            log.error("解析质量评分失败，标记为未通过: {}", e.getMessage());
            return new QualityResult(0, false, "评分解析失败: " + e.getMessage(), false,
                    List.of("评分解析失败，建议重写"), List.of());
        }
    }

    private String truncate(String text, int maxLen) {
        return text != null && text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }

    @Data
    @AllArgsConstructor
    private static class QualityResult {
        double totalScore;
        boolean qualityPassed;
        String assessment;
        boolean clueLandingPassed;
        List<String> clueLandingIssues;
        List<String> suggestions;
    }
}
