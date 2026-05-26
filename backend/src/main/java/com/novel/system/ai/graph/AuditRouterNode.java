package com.novel.system.ai.graph;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AuditRouterNode implements NovelGraphNode {

    private static final int MAX_REWRITES = 2;
    private static final int MAX_PLAN_RETRIES = 3;
    private static final double MIN_QUALITY_SCORE = 60.0;

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
            return state.toBuilder()
                    .auditDecision("PASS")
                    .currentPhase(NovelAgentState.GenerationPhase.AUDIT_ROUTER.name())
                    .build();
        }

        if (state.getRewriteCount() >= MAX_REWRITES && !audit.isAllPassed()) {
            log.warn("[audit_router] 重写次数达上限({})，标记MANUAL_REVIEW", state.getRewriteCount());
            return state.toBuilder()
                    .auditDecision("MANUAL_REVIEW")
                    .currentPhase(NovelAgentState.GenerationPhase.AUDIT_ROUTER.name())
                    .build();
        }

        if (state.getPreWriteValidation() != null
                && !state.getPreWriteValidation().isPassed()
                && state.getPlanRetryCount() < MAX_PLAN_RETRIES) {
            log.info("[audit_router] 写前校验未通过，决策: REPLAN");
            return state.toBuilder()
                    .auditDecision("REPLAN")
                    .planRetryCount(state.getPlanRetryCount() + 1)
                    .iterationCount(state.getIterationCount() + 1)
                    .currentPhase(NovelAgentState.GenerationPhase.AUDIT_ROUTER.name())
                    .build();
        }

        if (!audit.isAllPassed()) {
            if (!audit.isOocPassed() || !audit.isSettingPassed()) {
                if (state.getPlanRetryCount() < MAX_PLAN_RETRIES) {
                    log.info("[audit_router] OOC/设定校验未通过，决策: REPLAN");
                    return state.toBuilder()
                            .auditDecision("REPLAN")
                            .planRetryCount(state.getPlanRetryCount() + 1)
                            .iterationCount(state.getIterationCount() + 1)
                            .currentPhase(NovelAgentState.GenerationPhase.AUDIT_ROUTER.name())
                            .build();
                }
            }

            if (state.getRewriteCount() < MAX_REWRITES) {
                log.info("[audit_router] 审校未通过(质量{})，决策: REWRITE", audit.getQualityScore());
                return state.toBuilder()
                        .auditDecision("REWRITE")
                        .rewriteCount(state.getRewriteCount() + 1)
                        .iterationCount(state.getIterationCount() + 1)
                        .currentPhase(NovelAgentState.GenerationPhase.AUDIT_ROUTER.name())
                        .build();
            }
        }

        if (audit.getQualityScore() < MIN_QUALITY_SCORE && state.getRewriteCount() < MAX_REWRITES) {
            log.info("[audit_router] 质量分过低({})，决策: REWRITE", audit.getQualityScore());
            return state.toBuilder()
                    .auditDecision("REWRITE")
                    .rewriteCount(state.getRewriteCount() + 1)
                    .iterationCount(state.getIterationCount() + 1)
                    .currentPhase(NovelAgentState.GenerationPhase.AUDIT_ROUTER.name())
                    .build();
        }

        log.info("[audit_router] 全部通过，决策: PASS");
        return state.toBuilder()
                .auditDecision("PASS")
                .currentPhase(NovelAgentState.GenerationPhase.AUDIT_ROUTER.name())
                .build();
    }
}
