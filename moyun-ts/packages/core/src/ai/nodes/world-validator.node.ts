import type { ChatOpenAI } from '@langchain/openai';
import { HumanMessage, SystemMessage } from '@langchain/core/messages';
import type { NovelGraphNode, NovelAgentState, PreWriteValidation } from '../../types/graph.js';

const WORLD_VALIDATION_PROMPT = `你是一位世界观一致性审查员。检查以下章节大纲是否存在世界观设定冲突。

检查项：
1. 时间线是否合理
2. 地理/空间设定是否矛盾
3. 魔法/科技体系规则是否被违反
4. 社会结构/权力关系是否一致

输出纯JSON：
{"passed":true/false,"violations":["违规1"],"warnings":["警告1"],"suggestions":["建议1"]}`;

export class WorldValidatorNode implements NovelGraphNode {
  nodeName = 'world_validate';

  constructor(private llm: ChatOpenAI) {}

  shouldSkip(state: NovelAgentState): boolean {
    return state.preWriteValidation?.passed === true;
  }

  async apply(state: NovelAgentState): Promise<NovelAgentState> {
    try {
      const plan = state.chapterPlan;
      if (!plan) {
        return {
          ...state,
          preWriteValidation: { passed: true, violations: [], warnings: [], suggestions: [] },
          currentPhase: 'PRE_WRITE_VALID',
        };
      }

      const userPrompt = [
        `小说ID: ${state.novelId}`,
        `章节号: 第${state.chapterNum}章`,
        state.outlineContext ? `\n【世界观设定】\n${state.outlineContext}` : '',
        `\n【章节大纲】\n标题: ${plan.title}\n概要: ${plan.summary}\n要点: ${plan.keyPoints.join('；')}`,
      ].filter(Boolean).join('\n');

      const response = await this.llm.invoke([
        new SystemMessage(WORLD_VALIDATION_PROMPT),
        new HumanMessage(userPrompt),
      ]);

      const text = typeof response.content === 'string'
        ? response.content
        : JSON.stringify(response.content);

      const validation = this.parseValidation(text);

      return {
        ...state,
        preWriteValidation: validation,
        currentPhase: 'PRE_WRITE_VALID',
      };
    } catch (error) {
      return {
        ...state,
        preWriteValidation: { passed: true, violations: [], warnings: [], suggestions: [] },
        currentPhase: 'PRE_WRITE_VALID',
        errors: [...state.errors, `world_validate失败: ${error instanceof Error ? error.message : '未知错误'}`],
      };
    }
  }

  private parseValidation(text: string): PreWriteValidation {
    const jsonMatch = text.match(/\{[\s\S]*\}/);
    if (!jsonMatch) {
      return { passed: true, violations: [], warnings: [], suggestions: [] };
    }
    try {
      const parsed = JSON.parse(jsonMatch[0]);
      return {
        passed: parsed.passed ?? true,
        violations: Array.isArray(parsed.violations) ? parsed.violations : [],
        warnings: Array.isArray(parsed.warnings) ? parsed.warnings : [],
        suggestions: Array.isArray(parsed.suggestions) ? parsed.suggestions : [],
      };
    } catch {
      return { passed: true, violations: [], warnings: [], suggestions: [] };
    }
  }
}
