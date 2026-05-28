import type { NovelGraphNode, NovelAgentState } from '../../types/graph.js';

export class IdeaSelectionNode implements NovelGraphNode {
  nodeName = 'idea_select';

  async apply(state: NovelAgentState): Promise<NovelAgentState> {
    const ideas = state.chapterPlan?.implementationIdeas ?? [];
    const selectedIdea = ideas.length > 0 ? ideas[0] : state.chapterPlan?.summary ?? '';

    return {
      ...state,
      selectedIdea,
      currentPhase: 'IDEA_SELECT',
    };
  }
}
