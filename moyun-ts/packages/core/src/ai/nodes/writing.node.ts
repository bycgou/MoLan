import type { ChatOpenAI } from '@langchain/openai';
import { WritingChain } from '../chains/writing.chain.js';
import type { NovelGraphNode, NovelAgentState, DraftContent } from '../../types/graph.js';

export class WritingNode implements NovelGraphNode {
  nodeName = 'writing';
  private chain: WritingChain;

  constructor(llm: ChatOpenAI) {
    this.chain = new WritingChain(llm);
  }

  shouldSkip(state: NovelAgentState): boolean {
    return !!state.draftContent && state.draftContent.severity !== 'HIGH';
  }

  async apply(state: NovelAgentState): Promise<NovelAgentState> {
    try {
      const plan = state.chapterPlan;
      if (!plan) {
        return {
          ...state,
          errors: [...state.errors, 'writing: 缺少chapterPlan'],
          currentPhase: 'WRITING',
        };
      }

      const result = await this.chain.execute({
        novelId: state.novelId,
        chapterNum: state.chapterNum,
        title: plan.title,
        outline: JSON.stringify(plan),
        context: state.outlineContext ?? '',
      });

      // Determine severity based on cliche count
      let severity: DraftContent['severity'] = 'LOW';
      if (result.clicheCount > 5) severity = 'HIGH';
      else if (result.clicheCount > 2) severity = 'MEDIUM';

      const draftContent: DraftContent = {
        content: result.content,
        wordCount: result.wordCount,
        severity,
        revisionNotes: result.cliches.length > 0
          ? `检测到套话: ${result.cliches.join('、')}`
          : undefined,
      };

      return {
        ...state,
        draftContent,
        writeRetryCount: state.writeRetryCount + 1,
        currentPhase: 'WRITING',
      };
    } catch (error) {
      return {
        ...state,
        writeRetryCount: state.writeRetryCount + 1,
        currentPhase: 'WRITING',
        errors: [...state.errors, `writing失败: ${error instanceof Error ? error.message : '未知错误'}`],
      };
    }
  }
}
