import type { ChatOpenAI } from '@langchain/openai';
import { HumanMessage, SystemMessage } from '@langchain/core/messages';
import { StyleTextAnalyzer, type StyleMetrics } from './style-text-analyzer.js';
import { scanAllCliches, findTransitionCliches } from '../anti-cliches.js';
import type { StyleCard } from '../../types/style-card.js';

export interface StyleCheckResult {
  passed: boolean;
  score: number;
  issues: string[];
  metrics?: StyleMetrics;
}

export class StyleCheckerService {
  private analyzer = new StyleTextAnalyzer();

  quickCheck(text: string, card?: StyleCard): StyleCheckResult {
    const metrics = this.analyzer.analyze(text);
    let score = 100;
    const issues: string[] = [];

    // 1. AI cliches (all three categories merged)
    const allCliches = scanAllCliches(text);
    const clicheList = [...allCliches.cliches, ...allCliches.transitions, ...allCliches.upgrades];
    if (clicheList.length > 0) {
      score -= clicheList.length * 8;
      issues.push(`AI陈词滥调: ${clicheList.join('、')}`);
    }

    // 2. Transition cliches
    const transitionCliches = findTransitionCliches(text);
    if (transitionCliches.length > 0) {
      score -= transitionCliches.length * 5;
    }

    // 3. Transition cliche ratio
    if (metrics.transitionClicheRatio > 0.15) {
      score -= 10;
      issues.push('转场陈词滥调比例过高');
    }

    // 4. Sentence diversity
    if (metrics.sentenceDiversityIndex < 0.3) {
      score -= 12;
      issues.push('句式过于单一，AI味重');
    }

    // 5. Consecutive dialogue
    if (metrics.maxConsecutiveDialogue > 8) {
      score -= 10;
      issues.push('连续对话过长');
    }

    // 6. Short sentence ratio
    if (metrics.shortSentenceRatio < 0.15) {
      score -= 10;
      issues.push('短句比例过低');
    }

    // 7. Dialogue ratio
    if (metrics.dialogueRatio < 0.05) {
      score -= 10;
      issues.push('对话比例过低');
    }

    // 8. Short paragraph ratio
    if (metrics.shortParagraphRatio < 0.1) {
      score -= 10;
      issues.push('段落过长');
    }

    // 9. Empty scene ratio
    if (metrics.emptySceneRatio > 0.5) {
      score -= 8;
      issues.push('空场景比例过高');
    }

    return { passed: score >= 70, score: Math.max(0, score), issues, metrics };
  }

  async deepCheck(text: string, card: StyleCard, llm: ChatOpenAI): Promise<StyleCheckResult> {
    const quickResult = this.quickCheck(text, card);
    if (quickResult.passed) return quickResult;

    try {
      const truncated = text.length > 3000
        ? text.substring(0, 1500) + '\n...\n' + text.substring(text.length - 1500)
        : text;

      const response = await llm.invoke([
        new SystemMessage('你是文风检查专家。请对比生成文本与目标文风，返回JSON格式：{"score":0-100,"issues":["问题1"],"suggestions":["建议1"]}'),
        new HumanMessage(`【目标文风】\n${card.sampleText ?? card.name}\n\n【生成文本】\n${truncated}`),
      ]);

      const respText = typeof response.content === 'string' ? response.content : JSON.stringify(response.content);
      const jsonMatch = respText.match(/\{[\s\S]*\}/);
      if (jsonMatch) {
        const parsed = JSON.parse(jsonMatch[0]);
        return {
          passed: (parsed.score ?? 0) >= 70,
          score: parsed.score ?? quickResult.score,
          issues: [...quickResult.issues, ...(parsed.issues ?? [])],
          metrics: quickResult.metrics,
        };
      }
    } catch {
      /* fallback to quickCheck */
    }

    return quickResult;
  }
}
