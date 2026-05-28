import type { ChatOpenAI } from '@langchain/openai';
import { HumanMessage, SystemMessage } from '@langchain/core/messages';
import { WRITING_SYSTEM_PROMPT, buildWritingUserPrompt } from '../prompts/writing.js';
import { findCliches } from '../anti-cliches.js';
import type { StyleMetrics } from '../../types/style-card.js';
import { countWords } from '../../utils/text.js';

export interface WritingResult {
  content: string;
  wordCount: number;
  clicheCount: number;
  cliches: string[];
}

export class WritingChain {
  constructor(private llm: ChatOpenAI) {}

  async execute(params: {
    novelId: string;
    chapterNum: number;
    title: string;
    outline: string;
    context: string;
    styleCard?: string;
    styleSystemRules?: string;
    styleUserGuidance?: string;
  }): Promise<WritingResult> {
    const userPrompt = buildWritingUserPrompt(params);

    const systemContent = params.styleSystemRules
      ? `${WRITING_SYSTEM_PROMPT}\n\n${params.styleSystemRules}`
      : WRITING_SYSTEM_PROMPT;
    const userContent = params.styleUserGuidance
      ? `${userPrompt}\n\n${params.styleUserGuidance}`
      : userPrompt;

    const response = await this.llm.invoke([
      new SystemMessage(systemContent),
      new HumanMessage(userContent),
    ]);

    const content = typeof response.content === 'string'
      ? response.content
      : JSON.stringify(response.content);

    const cliches = findCliches(content);
    const wordCount = countWords(content);

    return {
      content,
      wordCount,
      clicheCount: cliches.length,
      cliches,
    };
  }

  /** 流式写作 */
  async *executeStream(params: {
    novelId: string;
    chapterNum: number;
    title: string;
    outline: string;
    context: string;
    styleCard?: string;
    styleSystemRules?: string;
    styleUserGuidance?: string;
    signal?: AbortSignal;
  }): AsyncGenerator<string> {
    const userPrompt = buildWritingUserPrompt(params);

    const systemContent = params.styleSystemRules
      ? `${WRITING_SYSTEM_PROMPT}\n\n${params.styleSystemRules}`
      : WRITING_SYSTEM_PROMPT;
    const userContent = params.styleUserGuidance
      ? `${userPrompt}\n\n${params.styleUserGuidance}`
      : userPrompt;

    const stream = await this.llm.stream([
      new SystemMessage(systemContent),
      new HumanMessage(userContent),
    ]);

    for await (const chunk of stream) {
      if (params.signal?.aborted) break;
      const text = typeof chunk.content === 'string'
        ? chunk.content
        : JSON.stringify(chunk.content);
      if (text) yield text;
    }
  }
}
