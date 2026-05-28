import type { ChatOpenAI } from '@langchain/openai';
import { HumanMessage, SystemMessage } from '@langchain/core/messages';
import { OUTLINE_SYSTEM_PROMPT, buildOutlineUserPrompt } from '../prompts/outline.js';
import type { OutlineResult } from '../prompts/outline.js';

export type { OutlineResult };

export class OutlineChain {
  constructor(private llm: ChatOpenAI) {}

  async execute(params: {
    novelId: string;
    chapterNum: number;
    totalOutline?: string;
    worldSetting?: string;
    recentChapters?: string;
    lastHook?: string;
    characterStates?: string;
    clueBook?: string;
    storyLineReview?: string;
  }): Promise<OutlineResult> {
    const userPrompt = buildOutlineUserPrompt(params);

    const response = await this.llm.invoke([
      new SystemMessage(OUTLINE_SYSTEM_PROMPT),
      new HumanMessage(userPrompt),
    ]);

    const text = typeof response.content === 'string'
      ? response.content
      : JSON.stringify(response.content);

    return this.parseOutline(text, params.chapterNum);
  }

  private parseOutline(text: string, chapterNum: number): OutlineResult {
    const jsonMatch = text.match(/\{[\s\S]*\}/);
    if (!jsonMatch) {
      return {
        chapter_number: chapterNum,
        title: '未命名章节',
        summary: '大纲解析失败',
        key_points: [],
        hook: '',
      };
    }

    try {
      const parsed = JSON.parse(jsonMatch[0]);
      return {
        chapter_number: parsed.chapter_number ?? chapterNum,
        title: parsed.title ?? '未命名章节',
        summary: parsed.summary ?? '',
        key_points: Array.isArray(parsed.key_points) ? parsed.key_points : [],
        hook: parsed.hook ?? '',
      };
    } catch {
      return {
        chapter_number: chapterNum,
        title: '未命名章节',
        summary: '大纲JSON解析失败',
        key_points: [],
        hook: '',
      };
    }
  }
}
