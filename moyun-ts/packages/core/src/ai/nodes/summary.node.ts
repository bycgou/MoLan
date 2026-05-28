import type { ChatOpenAI } from '@langchain/openai';
import { SummaryChain } from '../chains/summary.chain.js';
import type { NovelGraphNode, NovelAgentState } from '../../types/graph.js';

export class SummaryNode implements NovelGraphNode {
  nodeName = 'summary';
  private chain: SummaryChain;

  constructor(llm: ChatOpenAI) {
    this.chain = new SummaryChain(llm);
  }

  shouldSkip(state: NovelAgentState): boolean {
    return !!state.finalResult?.summary;
  }

  async apply(state: NovelAgentState): Promise<NovelAgentState> {
    const content = state.draftContent?.content ?? state.finalResult?.finalContent;
    if (!content) {
      return {
        ...state,
        finalResult: {
          finalContent: '',
          wordCount: 0,
          qualityScore: state.qualityScore ?? 0,
          summary: '',
        },
        currentPhase: 'SUMMARY',
      };
    }

    try {
      const result = await this.chain.execute({
        chapterNum: state.chapterNum,
        content,
      });

      const summaryText = [
        result.fingerprint,
        `关键词: ${result.keywords.join('、')}`,
        `角色: ${result.characters.join('、')}`,
      ].filter(Boolean).join(' | ');

      return {
        ...state,
        finalResult: {
          finalContent: content,
          wordCount: state.draftContent?.wordCount ?? content.length,
          qualityScore: state.qualityScore ?? 0,
          summary: summaryText,
        },
        currentPhase: 'SUMMARY',
      };
    } catch (error) {
      return {
        ...state,
        finalResult: {
          finalContent: content,
          wordCount: state.draftContent?.wordCount ?? content.length,
          qualityScore: state.qualityScore ?? 0,
          summary: '',
        },
        currentPhase: 'SUMMARY',
        errors: [...state.errors, `summary失败: ${error instanceof Error ? error.message : '未知错误'}`],
      };
    }
  }
}
