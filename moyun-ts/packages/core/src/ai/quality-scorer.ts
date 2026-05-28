import type { ChatOpenAI } from '@langchain/openai';
import { HumanMessage, SystemMessage } from '@langchain/core/messages';
import { findCliches } from './anti-cliches.js';

export interface QualityScore {
  hookScore: number;        // 追读力 0-20
  characterScore: number;   // 角色一致性 0-20
  logicScore: number;       // 逻辑性 0-20
  styleScore: number;       // 文风 0-20
  pacingScore: number;      // 节奏 0-20
  totalScore: number;       // 0-100
  clichePenalty: number;    // 套话扣分
  clueLanding: boolean;     // 线索落地
  suggestion: string;
}

const SCORING_PROMPT = `你是一位小说质量评分专家。对以下章节进行5维度评分。

评分维度（每项0-20分）：
1. hookScore - 追读力：结尾是否有吸引力，读者是否想看下一章
2. characterScore - 角色一致性：人物言行是否符合设定
3. logicScore - 逻辑性：情节是否自洽，因果链是否清晰
4. styleScore - 文风：文笔质量，描写是否生动
5. pacingScore - 节奏：紧张舒缓交替是否合理

输出纯JSON：
{"hookScore":分数,"characterScore":分数,"logicScore":分数,"styleScore":分数,"pacingScore":分数,"suggestion":"改进建议"}`;

export class QualityScorer {
  constructor(private llm: ChatOpenAI) {}

  async score(params: {
    content: string;
    outline?: string;
    clueBook?: string;
  }): Promise<QualityScore> {
    // LLM评分
    const llmScore = await this.getLLMScore(params.content, params.outline);

    // 套话检测扣分
    const cliches = findCliches(params.content);
    const clichePenalty = Math.min(cliches.length * 2, 10);

    // 线索落地检查
    const clueLanding = this.checkClueLanding(params.content, params.clueBook);

    const totalScore = Math.max(0,
      llmScore.hookScore + llmScore.characterScore + llmScore.logicScore +
      llmScore.styleScore + llmScore.pacingScore - clichePenalty
    );

    return {
      ...llmScore,
      totalScore,
      clichePenalty,
      clueLanding,
    };
  }

  private async getLLMScore(content: string, outline?: string): Promise<Omit<QualityScore, 'totalScore' | 'clichePenalty' | 'clueLanding'>> {
    try {
      const userPrompt = outline
        ? `【章纲】\n${outline}\n\n【章节内容】\n${content}`
        : `【章节内容】\n${content}`;

      const response = await this.llm.invoke([
        new SystemMessage(SCORING_PROMPT),
        new HumanMessage(userPrompt),
      ]);

      const text = typeof response.content === 'string'
        ? response.content
        : JSON.stringify(response.content);

      const jsonMatch = text.match(/\{[\s\S]*\}/);
      if (jsonMatch) {
        const parsed = JSON.parse(jsonMatch[0]);
        return {
          hookScore: parsed.hookScore ?? 10,
          characterScore: parsed.characterScore ?? 10,
          logicScore: parsed.logicScore ?? 10,
          styleScore: parsed.styleScore ?? 10,
          pacingScore: parsed.pacingScore ?? 10,
          suggestion: parsed.suggestion ?? '无建议',
        };
      }
    } catch {
      // fallback
    }

    return {
      hookScore: 10, characterScore: 10, logicScore: 10,
      styleScore: 10, pacingScore: 10, suggestion: '评分失败，使用默认值',
    };
  }

  private checkClueLanding(content: string, clueBook?: string): boolean {
    if (!clueBook) return true;
    const clues = clueBook.split('\n').filter(l => l.trim());
    for (const clue of clues) {
      const keyword = clue.split(/[：:]/)[0]?.trim();
      if (keyword && keyword.length >= 2) {
        if (!content.includes(keyword)) return false;
      }
    }
    return true;
  }
}
