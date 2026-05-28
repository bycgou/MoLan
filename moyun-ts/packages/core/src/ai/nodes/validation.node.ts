import type { ChatOpenAI } from '@langchain/openai';
import { ValidationService } from '../validation.service.js';
import type { NovelGraphNode, NovelAgentState } from '../../types/graph.js';

export class ValidationNode implements NovelGraphNode {
  nodeName = 'validation';
  private service: ValidationService;

  constructor(llm: ChatOpenAI) {
    this.service = new ValidationService(llm);
  }

  async apply(state: NovelAgentState): Promise<NovelAgentState> {
    const content = state.draftContent?.content;
    if (!content) {
      return {
        ...state,
        validationResults: {
          ooc: { passed: true, score: 0, issues: ['无内容'] },
          logic: { passed: true, score: 0, issues: ['无内容'] },
          clue: { passed: true, score: 0, issues: ['无内容'] },
          setting: { passed: true, score: 0, issues: ['无内容'] },
          rhythm: { passed: true, score: 0, issues: ['无内容'] },
          allPassed: true,
        },
        currentPhase: 'VALIDATION',
      };
    }

    try {
      const results = await this.service.validateAll({
        chapterNum: state.chapterNum,
        content,
        outline: state.chapterPlan ? JSON.stringify(state.chapterPlan) : undefined,
      });

      return {
        ...state,
        validationResults: results,
        currentPhase: 'VALIDATION',
      };
    } catch (error) {
      return {
        ...state,
        errors: [...state.errors, `validation失败: ${error instanceof Error ? error.message : '未知错误'}`],
        currentPhase: 'VALIDATION',
      };
    }
  }
}
