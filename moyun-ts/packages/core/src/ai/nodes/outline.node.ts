import type { ChatOpenAI } from '@langchain/openai';
import { OutlineChain } from '../chains/outline.chain.js';
import type { NovelGraphNode, NovelAgentState, ChapterPlan } from '../../types/graph.js';

export class OutlineNode implements NovelGraphNode {
  nodeName = 'outline';
  private chain: OutlineChain;

  constructor(llm: ChatOpenAI) {
    this.chain = new OutlineChain(llm);
  }

  shouldSkip(state: NovelAgentState): boolean {
    return !!state.chapterPlan;
  }

  async apply(state: NovelAgentState): Promise<NovelAgentState> {
    try {
      const result = await this.chain.execute({
        novelId: state.novelId,
        chapterNum: state.chapterNum,
        totalOutline: state.outlineContext,
      });

      const plan: ChapterPlan = {
        title: result.title,
        summary: result.summary,
        keyPoints: result.key_points,
        hook: result.hook,
        implementationIdeas: result.key_points.length > 0
          ? result.key_points
          : [result.summary],
        clueActions: [],
        characterActions: [],
      };

      return {
        ...state,
        chapterPlan: plan,
        currentPhase: 'OUTLINE',
        planRetryCount: state.planRetryCount + 1,
      };
    } catch (error) {
      return {
        ...state,
        currentPhase: 'OUTLINE',
        errors: [...state.errors, `outline失败: ${error instanceof Error ? error.message : '未知错误'}`],
      };
    }
  }
}
