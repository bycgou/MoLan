package com.novel.system.ai.graph;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class GradedAuditCalculator {

    private final ValidationConfig config;

    public GradedResult calculate(NovelAgentState.AuditResult rawAudit) {
        List<NovelAgentState.DimensionResult> dimensions = new ArrayList<>();
        List<String> fatalFailures = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        dimensions.add(evalDimension("ooc", rawAudit.isOocPassed(),
                rawAudit.getOocScore(), rawAudit.getOocDetail()));
        dimensions.add(evalDimension("logic", rawAudit.isLogicPassed(),
                rawAudit.isLogicPassed() ? 100 : 0, rawAudit.getLogicDetail()));
        dimensions.add(evalDimension("setting", rawAudit.isSettingPassed(),
                rawAudit.isSettingPassed() ? 100 : 0, rawAudit.getSettingDetail()));
        dimensions.add(evalDimension("clue", rawAudit.isCluePassed(),
                rawAudit.isCluePassed() ? 100 : 0, rawAudit.getClueDetail()));
        dimensions.add(evalDimension("rhythm", rawAudit.isRhythmPassed(),
                rawAudit.isRhythmPassed() ? 100 : 0, rawAudit.getRhythmDetail()));
        dimensions.add(evalDimension("quality", rawAudit.isQualityPassed(),
                rawAudit.getQualityScore(), rawAudit.getQualityAssessment()));
        dimensions.add(evalDimension("clueLanding", rawAudit.isClueLandingPassed(),
                rawAudit.isClueLandingPassed() ? 100 : 0,
                formatClueLandingIssues(rawAudit.getClueLandingIssues())));

        double weightedScore = 0;
        for (NovelAgentState.DimensionResult d : dimensions) {
            weightedScore += d.getScore() * d.getWeight();
            if (!d.isPassed()) {
                if ("FATAL".equals(d.getSeverity())) {
                    fatalFailures.add(d.getDimension());
                } else if ("WARNING".equals(d.getSeverity())) {
                    warnings.add(d.getDimension());
                }
            }
        }

        boolean allPassed = fatalFailures.isEmpty()
                && weightedScore >= config.getPassThreshold();

        return new GradedResult(weightedScore, dimensions, fatalFailures, warnings, allPassed);
    }

    private NovelAgentState.DimensionResult evalDimension(
            String name, boolean passed, double score, String detail) {
        ValidationConfig.DimensionConfig dc = config.getDimensions().get(name);
        double weight = dc != null ? dc.getWeight() : 0;
        String severity = dc != null ? dc.getSeverity().name() : "SKIP";
        return NovelAgentState.DimensionResult.builder()
                .dimension(name).passed(passed).score(score)
                .weight(weight).severity(severity).detail(detail)
                .build();
    }

    private String formatClueLandingIssues(List<String> issues) {
        if (issues == null || issues.isEmpty()) return "无";
        return String.join("；", issues);
    }

    @Data
    @AllArgsConstructor
    public static class GradedResult {
        private double weightedScore;
        private List<NovelAgentState.DimensionResult> dimensions;
        private List<String> fatalFailures;
        private List<String> warnings;
        private boolean allPassed;
    }
}
