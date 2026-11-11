package com.novel.system.ai.graph;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * audit_router 节点的加权路由（use-graded-audit=true）决策测试。
 */
class AuditRouterNodeTest {

    private ValidationConfig config;
    private GradedAuditCalculator calculator;
    private AuditRouterNode node;

    @BeforeEach
    void setUp() {
        config = new ValidationConfig();
        config.setUseGradedAudit(true);
        config.setPassThreshold(60.0);
        config.setDimensions(Map.of(
                "ooc", new ValidationConfig.DimensionConfig(0.20, ValidationConfig.Severity.FATAL),
                "logic", new ValidationConfig.DimensionConfig(0.20, ValidationConfig.Severity.FATAL),
                "setting", new ValidationConfig.DimensionConfig(0.15, ValidationConfig.Severity.FATAL),
                "clue", new ValidationConfig.DimensionConfig(0.15, ValidationConfig.Severity.WARNING),
                "rhythm", new ValidationConfig.DimensionConfig(0.10, ValidationConfig.Severity.WARNING),
                "quality", new ValidationConfig.DimensionConfig(0.10, ValidationConfig.Severity.WARNING),
                "clueLanding", new ValidationConfig.DimensionConfig(0.10, ValidationConfig.Severity.WARNING)
        ));
        calculator = new GradedAuditCalculator(config);
        node = new AuditRouterNode(config, calculator);
    }

    private NovelAgentState.AuditResult.AuditResultBuilder passing() {
        return NovelAgentState.AuditResult.builder()
                .oocPassed(true).oocScore(90)
                .logicPassed(true)
                .settingPassed(true)
                .cluePassed(true)
                .rhythmPassed(true)
                .qualityPassed(true).qualityScore(90.0)
                .clueLandingPassed(true);
    }

    private NovelAgentState stateWith(NovelAgentState.AuditResult audit) {
        return NovelAgentState.builder()
                .auditResult(audit)
                .rewriteCount(0)
                .planRetryCount(0)
                .build();
    }

    @Test
    void nullAudit_passes() throws Exception {
        NovelAgentState state = NovelAgentState.builder().build();
        NovelAgentState out = node.apply(state);
        assertEquals("PASS", out.getAuditDecision());
    }

    @Test
    void allPass_passes() throws Exception {
        NovelAgentState out = node.apply(stateWith(passing().build()));
        assertEquals("PASS", out.getAuditDecision());
        assertNotNull(out.getAuditResult().getDimensionResults());
        assertEquals(7, out.getAuditResult().getDimensionResults().size());
        assertEquals(0.20, out.getAuditResult().getDimensionResults().stream()
                .filter(d -> "ooc".equals(d.getDimension()))
                .findFirst().orElseThrow().getWeight(), 1e-6);
    }

    @Test
    void fatalDimension_triggersReplan() throws Exception {
        NovelAgentState out = node.apply(stateWith(passing().oocPassed(false).oocScore(20).build()));
        assertEquals("REPLAN", out.getAuditDecision());
        assertEquals(1, out.getPlanRetryCount());
    }

    @Test
    void warningOnlyBelowThreshold_triggersRewrite() throws Exception {
        // 加权分 = 0.20*50 + 0.20*100 + 0.15*100 + 0.15*0 + 0.10*0 + 0.10*0 + 0.10*100 = 57 < 60
        NovelAgentState out = node.apply(stateWith(passing()
                .oocScore(50)
                .cluePassed(false)
                .rhythmPassed(false)
                .qualityPassed(false).qualityScore(0.0)
                .build()));
        assertEquals("REWRITE", out.getAuditDecision());
        assertEquals(1, out.getRewriteCount());
    }

    @Test
    void rewriteExhausted_fallsBackToManualReview() throws Exception {
        // 加权分 = 0.20*50 + 0.20*100 + 0.15*100 + 0.15*0 + 0.10*0 + 0.10*0 + 0.10*100 = 55 < 60
        NovelAgentState state = NovelAgentState.builder()
                .auditResult(passing().oocScore(50)
                        .cluePassed(false)
                        .rhythmPassed(false)
                        .qualityPassed(false).qualityScore(0.0).build())
                .rewriteCount(2)
                .planRetryCount(0)
                .build();
        NovelAgentState out = node.apply(state);
        assertEquals("MANUAL_REVIEW", out.getAuditDecision());
    }

    @Test
    void weightedScoreIsPersistedInState() throws Exception {
        NovelAgentState out = node.apply(stateWith(passing().build()));
        assertEquals(97.0, out.getAuditResult().getWeightedScore(), 1e-6);
    }
}
