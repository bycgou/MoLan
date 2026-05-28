import type { NovelGraphNode, NovelAgentState } from '../../types/graph.js';

const NODE_ORDER = [
  'arc_check', 'outline', 'clue_planning', 'character_planning',
  'world_validate', 'idea_select', 'writing', 'self_review',
  'validation', 'quality_check', 'audit_router', 'summary', 'post_process',
];

export class GraphRunner {
  private nodes = new Map<string, NovelGraphNode>();

  register(node: NovelGraphNode): void {
    this.nodes.set(node.nodeName, node);
  }

  async execute(initialState: NovelAgentState): Promise<NovelAgentState> {
    let state = initialState;
    let currentIndex = 0;
    const maxIterations = 20;

    for (let iter = 0; iter < maxIterations && currentIndex < NODE_ORDER.length; iter++) {
      const nodeName = NODE_ORDER[currentIndex];
      const node = this.nodes.get(nodeName);

      if (!node) {
        state = this.appendTrace(state, `${nodeName}(missing)`);
        currentIndex++;
        continue;
      }

      // Conditional skip
      if (node.shouldSkip?.(state)) {
        state = this.appendTrace(state, `${nodeName}(skipped)`);
        currentIndex++;
        continue;
      }

      // Execute node
      state = await node.apply(state);
      state = this.appendTrace(state, nodeName);

      // Routing decisions
      const nextNode = this.resolveNextNode(nodeName, state);
      if (nextNode !== null) {
        const targetIndex = NODE_ORDER.indexOf(nextNode);
        if (targetIndex >= 0) {
          currentIndex = targetIndex;
          continue;
        }
      }
      currentIndex++;
    }

    return state;
  }

  private resolveNextNode(currentNode: string, state: NovelAgentState): string | null {
    if (currentNode === 'audit_router') {
      if (state.auditDecision === 'PASS') return 'summary';
      if (state.auditDecision === 'REPLAN') return 'outline';
      if (state.auditDecision === 'REWRITE') return 'writing';
      return null; // MANUAL_REVIEW or undefined → stop
    }

    if (currentNode === 'self_review' && state.draftContent?.severity === 'HIGH') {
      if (state.rewriteCount >= 2) return null;
      return 'writing';
    }

    return null;
  }

  private appendTrace(state: NovelAgentState, nodeName: string): NovelAgentState {
    return {
      ...state,
      executionTrace: [...state.executionTrace, nodeName],
    };
  }
}
