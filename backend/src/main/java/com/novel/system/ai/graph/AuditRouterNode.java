package com.novel.system.ai.graph;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditRouterNode implements NovelGraphNode {

    private static final int MAX_REWRITES = 2;
    private static final int MAX_PLAN_RETRIES = 3;
    private static final double MIN_QUALITY_SCORE = 60.0;

    private final ValidationConfig validationConfig;
    private final GradedAuditCalculator gradedAuditCalculator;

    @Override
    public String nodeName() {
        return "audit_router";
    }

    @Override
    public ErrorStrategy errorStrategy() { return ErrorStrategy.FAIL; }

    @Override
    public NovelAgentState apply(NovelAgentState state) throws Exception {
        NovelAgentState.AuditResult audit = state.getAuditResult();

        if (audit == null) {
            return pass(state);
        }

        if (validationConfig.isUseGradedAudit()) {
            return gradedRoute(state, audit);
        } else {
            return legacyRoute(state, audit);
        }
    }

    private NovelAgentState gradedRoute(NovelAgentState state, NovelAgentState.AuditResult audit) {
        GradedAuditCalculator.GradedResult graded = gradedAuditCalculator.calculate(audit);

        NovelAgentState.AuditResult updatedAudit = audit.toBuilder()
                .weightedScore(graded.getWeightedScore())
                .dimensionResults(graded.getDimensions())
                .fatalFailures(graded.getFatalFailures())
                .warnings(graded.getWarnings())
                .allPassed(graded.isAllPassed())
                .build();
        state = state.toBuilder().auditResult(updatedAudit).build();

        if (!graded.getFatalFailures().isEmpty()) {
            if (state.getPlanRetryCount() < MAX_PLAN_RETRIES) {
                log.info("[audit_router] FATAL 失败: {}，决策: REPLAN", graded.getFatalFailures());
                return replan(state);
            }
            log.warn("[audit_router] FATAL 失败且重规划次数达上限，决策: MANUAL_REVIEW");
            return manualReview(state);
        }

        if (!graded.isAllPassed() && state.getRewriteCount() < MAX_REWRITES) {
            log.info("[audit_router] 加权分 {} < {}，决策: REWRITE",
                    graded.getWeightedScore(), validationConfig.getPassThreshold());
            return rewrite(state);
        }

        log.info("[audit_router] 通过（加权分={}），决策: PASS", graded.getWeightedScore());
        return pass(state);
    }

    private NovelAgentState legacyRoute(NovelAgentState state, NovelAgentState.AuditResult audit) {
        if (state.getRewriteCount() >= MAX_REWRITES && !audit.isAllPassed()) {
            log.warn("[audit_router] 重写次数达上限({})，标记MANUAL_REVIEW", state.getRewriteCount());
            return manualReview(state);
        }

        if (state.getPreWriteValidation() != null
                && !state.getPreWriteValidation().isPassed()
                && state.getPlanRetryCount() < MAX_PLAN_RETRIES) {
            log.info("[audit_router] 写前校验未通过，决策: REPLAN");
            return replan(state);
        }

        if (!audit.isAllPassed()) {
            if (!audit.isOocPassed() || !audit.isSettingPassed()) {
                if (state.getPlanRetryCount() < MAX_PLAN_RETRIES) {
                    log.info("[audit_router] OOC/设定校验未通过，决策: REPLAN");
                    return replan(state);
                }
            }

            if (state.getRewriteCount() < MAX_REWRITES) {
                log.info("[audit_router] 审校未通过(质量{})，决策: REWRITE", audit.getQualityScore());
                return rewrite(state);
            }
        }

        if (audit.getQualityScore() < MIN_QUALITY_SCORE && state.getRewriteCount() < MAX_REWRITES) {
            log.info("[audit_router] 质量分过低({})，决策: REWRITE", audit.getQualityScore());
            return rewrite(state);
        }

        log.info("[audit_router] 全部通过，决策: PASS");
        return pass(state);
    }

    private NovelAgentState pass(NovelAgentState state) {
        return state.toBuilder()
                .auditDecision("PASS")
                .currentPhase(NovelAgentState.GenerationPhase.AUDIT_ROUTER.name())
                .build();
    }

    private NovelAgentState replan(NovelAgentState state) {
        return state.toBuilder()
                .auditDecision("REPLAN")
                .planRetryCount(state.getPlanRetryCount() + 1)
                .iterationCount(state.getIterationCount() + 1)
                .currentPhase(NovelAgentState.GenerationPhase.AUDIT_ROUTER.name())
                .build();
    }

    private NovelAgentState rewrite(NovelAgentState state) {
        return state.toBuilder()
                .auditDecision("REWRITE")
                .rewriteCount(state.getRewriteCount() + 1)
                .iterationCount(state.getIterationCount() + 1)
                .currentPhase(NovelAgentState.GenerationPhase.AUDIT_ROUTER.name())
                .build();
    }

    private NovelAgentState manualReview(NovelAgentState state) {
        return state.toBuilder()
                .auditDecision("MANUAL_REVIEW")
                .currentPhase(NovelAgentState.GenerationPhase.AUDIT_ROUTER.name())
                .build();
    }
}
