import type { ChatOpenAI } from '@langchain/openai';
import { HumanMessage, SystemMessage } from '@langchain/core/messages';
import type { NovelGraphNode, NovelAgentState, DraftContent } from '../../types/graph.js';

const SELF_REVIEW_PROMPT = `你是一位资深小说编辑。对以下章节进行自审，指出问题并评估严重程度。

检查维度：
1. 情节是否连贯，有无逻辑漏洞
2. 人物言行是否符合设定（OOC检测）
3. 文笔质量，有无套话或空洞描写
4. 节奏是否合理，有无拖沓或跳跃
5. 钩子是否有效

输出纯JSON：
{"severity":"LOW|MEDIUM|HIGH","notes":"修改建议","issues":["问题1","问题2"]}`;

export class SelfReviewNode implements NovelGraphNode {
  nodeName = 'self_review';

  constructor(private llm: ChatOpenAI) {}

  async apply(state: NovelAgentState): Promise<NovelAgentState> {
    const content = state.draftContent?.content;
    if (!content) {
      return {
        ...state,
        selfReviewNotes: '无内容可审',
        currentPhase: 'SELF_REVIEW',
      };
    }

    try {
      const userPrompt = [
        `小说ID: ${state.novelId}`,
        `章节号: 第${state.chapterNum}章`,
        state.chapterPlan ? `\n【章节大纲】\n标题: ${state.chapterPlan.title}\n概要: ${state.chapterPlan.summary}` : '',
        `\n【章节内容】\n${content}`,
      ].filter(Boolean).join('\n');

      const response = await this.llm.invoke([
        new SystemMessage(SELF_REVIEW_PROMPT),
        new HumanMessage(userPrompt),
      ]);

      const text = typeof response.content === 'string'
        ? response.content
        : JSON.stringify(response.content);

      const review = this.parseReview(text);

      const draft = state.draftContent!;
      const updatedDraft: DraftContent = {
        content: draft.content,
        wordCount: draft.wordCount,
        severity: review.severity,
        revisionNotes: review.notes,
      };

      return {
        ...state,
        draftContent: updatedDraft,
        selfReviewNotes: review.notes,
        currentPhase: 'SELF_REVIEW',
      };
    } catch (error) {
      return {
        ...state,
        selfReviewNotes: `自审失败: ${error instanceof Error ? error.message : '未知错误'}`,
        currentPhase: 'SELF_REVIEW',
        errors: [...state.errors, `self_review失败: ${error instanceof Error ? error.message : '未知错误'}`],
      };
    }
  }

  private parseReview(text: string): { severity: DraftContent['severity']; notes: string } {
    const jsonMatch = text.match(/\{[\s\S]*\}/);
    if (!jsonMatch) {
      return { severity: 'LOW', notes: '自审解析失败' };
    }
    try {
      const parsed = JSON.parse(jsonMatch[0]);
      const severity = ['LOW', 'MEDIUM', 'HIGH'].includes(parsed.severity) ? parsed.severity : 'LOW';
      const issues = Array.isArray(parsed.issues) ? parsed.issues.join('；') : '';
      return {
        severity: severity as DraftContent['severity'],
        notes: parsed.notes ?? issues ?? '无修改建议',
      };
    } catch {
      return { severity: 'LOW', notes: '自审JSON解析失败' };
    }
  }
}
