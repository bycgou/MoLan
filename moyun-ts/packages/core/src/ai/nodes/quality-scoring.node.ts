import type { NovelGraphNode, NovelAgentState } from '../../types/graph.js';

export class QualityScoringNode implements NovelGraphNode {
  nodeName = 'quality_check';

  async apply(state: NovelAgentState): Promise<NovelAgentState> {
    // QualityScorer is typically LLM-based; this node delegates to the validation
    // results and draft content metrics for a lightweight score.
    // The actual QualityScorer with LLM is used in the legacy ChapterGenerationService.
    // For the graph pipeline, we derive a score from validation results.
    const validation = state.validationResults;
    if (!validation) {
      return {
        ...state,
        qualityScore: 0,
        currentPhase: 'QUALITY_CHECK',
      };
    }

    const scores = [
      validation.ooc.score,
      validation.logic.score,
      validation.clue.score,
      validation.setting.score,
      validation.rhythm.score,
    ];
    const avgScore = scores.reduce((a, b) => a + b, 0) / scores.length;

    return {
      ...state,
      qualityScore: Math.round(avgScore),
      currentPhase: 'QUALITY_CHECK',
    };
  }
}
