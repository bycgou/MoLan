import type { NovelGraphNode, NovelAgentState } from '../../types/graph.js';

const MAX_REWRITES = 2;
const MIN_QUALITY_SCORE = 60;

export class AuditRouterNode implements NovelGraphNode {
  nodeName = 'audit_router';

  async apply(state: NovelAgentState): Promise<NovelAgentState> {
    const validation = state.validationResults;
    const qualityScore = state.qualityScore ?? 0;

    // Manual review if too many rewrites
    if (state.rewriteCount >= MAX_REWRITES && !validation?.allPassed) {
      return {
        ...state,
        auditDecision: 'MANUAL_REVIEW',
        currentPhase: 'AUDIT_ROUTER',
      };
    }

    // Replan if plan is fundamentally flawed (pre-write validation failed)
    if (state.preWriteValidation && !state.preWriteValidation.passed && state.planRetryCount < 3) {
      return {
        ...state,
        auditDecision: 'REPLAN',
        currentPhase: 'AUDIT_ROUTER',
      };
    }

    // Rewrite if validation failed and we haven't exceeded retries
    if (validation && !validation.allPassed && state.rewriteCount < MAX_REWRITES) {
      return {
        ...state,
        auditDecision: 'REWRITE',
        rewriteCount: state.rewriteCount + 1,
        currentPhase: 'AUDIT_ROUTER',
      };
    }

    // Rewrite if quality is too low
    if (qualityScore < MIN_QUALITY_SCORE && state.rewriteCount < MAX_REWRITES) {
      return {
        ...state,
        auditDecision: 'REWRITE',
        rewriteCount: state.rewriteCount + 1,
        currentPhase: 'AUDIT_ROUTER',
      };
    }

    // All checks passed
    return {
      ...state,
      auditDecision: 'PASS',
      currentPhase: 'AUDIT_ROUTER',
    };
  }
}
