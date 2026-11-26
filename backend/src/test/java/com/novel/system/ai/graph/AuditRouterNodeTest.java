package com.novel.system.ai.graph;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    /**
     * 审校结论必须真的落到 pendingRevisionNotes，
     * 否则下一轮写作的 prompt 里没有「待修订问题」，最贵的一环在空转。
     */
    @Test
    void rewriteWritesAuditFeedbackIntoPendingRevisionNotes() throws Exception {
        AuditRouterNode warnNode = warningOnlyNode();

        NovelAgentState.AuditResult audit = NovelAgentState.AuditResult.builder()
                .oocPassed(true).oocScore(40)
                .logicPassed(false)
                .logicDetail("主角靠巧合拿到了钥匙，因果链断裂")
                .settingPassed(true)
                .cluePassed(true)
                .rhythmPassed(true)
                .qualityPassed(false).qualityScore(0.0)
                .clueLandingPassed(true)
                .clueLandingIssues(List.of("伏笔 c3 未在本章落地"))
                .suggestions(List.of("补一段主角自己推理的过程"))
                .overallAssessment("逻辑有硬伤，其余尚可")
                .build();

        NovelAgentState out = warnNode.apply(stateWith(audit));

        assertEquals("REWRITE", out.getAuditDecision());
        // 原有计数行为不变
        assertEquals(1, out.getRewriteCount());
        assertEquals(1, out.getIterationCount());

        List<String> notes = out.getPendingRevisionNotes();
        assertNotNull(notes);
        assertFalse(notes.isEmpty(), "REWRITE 必须写入待修订问题");
        assertTrue(notes.stream().anyMatch(n -> n.contains("主角靠巧合拿到了钥匙，因果链断裂")),
                "未通过维度的明细应进入待修订问题: " + notes);
        assertTrue(notes.stream().anyMatch(n -> n.contains("伏笔 c3 未在本章落地")), notes.toString());
        assertTrue(notes.stream().anyMatch(n -> n.contains("补一段主角自己推理的过程")), notes.toString());
        assertTrue(notes.stream().anyMatch(n -> n.contains("逻辑有硬伤")), notes.toString());
    }

    /**
     * REPLAN 路径同样必须写入审校意见。
     *
     * <p>默认 ValidationConfig 里 ooc/logic/setting 是 FATAL → 未通过走 REPLAN 而不是
     * REWRITE，这是最常见的审校失败路径。意见存在 state 里，WritingNode 直接从 state 读
     * 并注入 prompt，OutlineNode 只是路过不消费，所以 REPLAN 写不写意见，
     * 决定了反馈链在默认配置下通不通。
     */
    @Test
    void replanWritesAuditFeedbackIntoPendingRevisionNotes() throws Exception {
        NovelAgentState out = node.apply(stateWith(NovelAgentState.AuditResult.builder()
                .oocPassed(false).oocScore(20).oocDetail("主角性格与设定书不符")
                .logicPassed(true).settingPassed(true).cluePassed(true).rhythmPassed(true)
                .qualityPassed(true).qualityScore(90.0).clueLandingPassed(true)
                .suggestions(List.of("让主角在对话中体现一贯的谨慎"))
                .build()));

        assertEquals("REPLAN", out.getAuditDecision());
        assertEquals(1, out.getPlanRetryCount());

        List<String> notes = out.getPendingRevisionNotes();
        assertNotNull(notes);
        assertFalse(notes.isEmpty(), "REPLAN 必须写入待修订问题");
        assertTrue(notes.stream().anyMatch(n -> n.contains("主角性格与设定书不符")),
                "致命维度的明细应进入待修订问题: " + notes);
        assertTrue(notes.stream().anyMatch(n -> n.contains("让主角在对话中体现一贯的谨慎")),
                "审校建议应进入待修订问题: " + notes);
    }

    /**
     * 通过（PASS）时不得写入修订意见，否则会把已通过的内容当成待改问题。
     */
    @Test
    void passDoesNotWritePendingRevisionNotes() throws Exception {
        NovelAgentState out = node.apply(stateWith(passing()
                .overallAssessment("全部通过")
                .build()));
        assertEquals("PASS", out.getAuditDecision());
        assertTrue(out.getPendingRevisionNotes() == null || out.getPendingRevisionNotes().isEmpty());
    }

    /**
     * 已有意见不能被审校意见覆盖：门禁/自审的意见与审校意见要合并。
     */
    @Test
    void existingRevisionNotesAreMergedNotReplaced() throws Exception {
        AuditRouterNode warnNode = warningOnlyNode();
        NovelAgentState state = stateWith(NovelAgentState.AuditResult.builder()
                .oocPassed(true).oocScore(40)
                .logicPassed(false).logicDetail("时间线对不上")
                .settingPassed(true).cluePassed(true).rhythmPassed(true)
                .qualityPassed(false).qualityScore(0.0)
                .clueLandingPassed(true)
                .build()).toBuilder()
                .pendingRevisionNotes(List.of("- 「trailer-ending」章末总结体"))
                .build();

        NovelAgentState out = warnNode.apply(state);

        List<String> notes = out.getPendingRevisionNotes();
        assertNotNull(notes);
        assertTrue(notes.contains("- 「trailer-ending」章末总结体"), notes.toString());
        assertTrue(notes.stream().anyMatch(n -> n.contains("时间线对不上")), notes.toString());
    }

    /** 把所有维度降级为 WARNING：这样未通过只会触发 REWRITE 而不是 REPLAN。 */
    private AuditRouterNode warningOnlyNode() {
        ValidationConfig warnConfig = new ValidationConfig();
        warnConfig.setUseGradedAudit(true);
        warnConfig.setPassThreshold(60.0);
        warnConfig.setDimensions(Map.of(
                "ooc", new ValidationConfig.DimensionConfig(0.20, ValidationConfig.Severity.WARNING),
                "logic", new ValidationConfig.DimensionConfig(0.20, ValidationConfig.Severity.WARNING),
                "setting", new ValidationConfig.DimensionConfig(0.15, ValidationConfig.Severity.WARNING),
                "clue", new ValidationConfig.DimensionConfig(0.15, ValidationConfig.Severity.WARNING),
                "rhythm", new ValidationConfig.DimensionConfig(0.10, ValidationConfig.Severity.WARNING),
                "quality", new ValidationConfig.DimensionConfig(0.10, ValidationConfig.Severity.WARNING),
                "clueLanding", new ValidationConfig.DimensionConfig(0.10, ValidationConfig.Severity.WARNING)
        ));
        return new AuditRouterNode(warnConfig, new GradedAuditCalculator(warnConfig));
    }
}
