import type { ChatOpenAI } from '@langchain/openai';
import { HumanMessage, SystemMessage } from '@langchain/core/messages';
import { SUMMARY_SYSTEM_PROMPT, buildSummaryUserPrompt } from '../prompts/summary.js';

export interface SummaryResult {
  fingerprint: string;
  keywords: string[];
  characters: string[];
  locations: string[];
  relations: string[];
}

export class SummaryChain {
  constructor(private llm: ChatOpenAI) {}

  async execute(params: {
    chapterNum: number;
    content: string;
  }): Promise<SummaryResult> {
    const userPrompt = buildSummaryUserPrompt(params);

    const response = await this.llm.invoke([
      new SystemMessage(SUMMARY_SYSTEM_PROMPT),
      new HumanMessage(userPrompt),
    ]);

    const text = typeof response.content === 'string'
      ? response.content
      : JSON.stringify(response.content);

    return this.parseSummary(text);
  }

  private parseSummary(text: string): SummaryResult {
    const jsonMatch = text.match(/\{[\s\S]*\}/);
    if (!jsonMatch) {
      return {
        fingerprint: '',
        keywords: [],
        characters: [],
        locations: [],
        relations: [],
      };
    }

    try {
      const parsed = JSON.parse(jsonMatch[0]);
      return {
        fingerprint: parsed.fingerprint ?? '',
        keywords: Array.isArray(parsed.keywords) ? parsed.keywords : [],
        characters: Array.isArray(parsed.characters) ? parsed.characters : [],
        locations: Array.isArray(parsed.locations) ? parsed.locations : [],
        relations: Array.isArray(parsed.relations) ? parsed.relations : [],
      };
    } catch {
      return {
        fingerprint: '',
        keywords: [],
        characters: [],
        locations: [],
        relations: [],
      };
    }
  }
}
