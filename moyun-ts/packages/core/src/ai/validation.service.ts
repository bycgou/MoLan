import type { ChatOpenAI } from '@langchain/openai';
import { HumanMessage, SystemMessage } from '@langchain/core/messages';
import {
  OOC_VALIDATION_PROMPT,
  LOGIC_VALIDATION_PROMPT,
  CLUE_VALIDATION_PROMPT,
  SETTING_VALIDATION_PROMPT,
  RHYTHM_VALIDATION_PROMPT,
  buildValidationUserPrompt,
} from './prompts/validation.js';

export interface ValidationResult {
  passed: boolean;
  score: number;
  issues: string[];
  details?: Record<string, unknown>;
}

export interface ValidationResults {
  ooc: ValidationResult;
  logic: ValidationResult;
  clue: ValidationResult;
  setting: ValidationResult;
  rhythm: ValidationResult;
  allPassed: boolean;
}

export class ValidationService {
  constructor(private llm: ChatOpenAI) {}

  async validateAll(params: {
    chapterNum: number;
    content: string;
    outline?: string;
    characterStates?: string;
    clueBook?: string;
    worldSetting?: string;
    expectedStrand?: string;
  }): Promise<ValidationResults> {
    const [ooc, logic, clue, setting, rhythm] = await Promise.all([
      this.validateOOC(params),
      this.validateLogic(params),
      this.validateClue(params),
      this.validateSetting(params),
      this.validateRhythm(params),
    ]);

    return {
      ooc,
      logic,
      clue,
      setting,
      rhythm,
      allPassed: ooc.passed && logic.passed && clue.passed && setting.passed && rhythm.passed,
    };
  }

  async validateOOC(params: {
    chapterNum: number;
    content: string;
    characterStates?: string;
    outline?: string;
  }): Promise<ValidationResult> {
    const userPrompt = buildValidationUserPrompt({
      chapterNum: params.chapterNum,
      content: params.content,
      outline: params.outline,
      characterStates: params.characterStates,
    });
    return this.runValidation(OOC_VALIDATION_PROMPT, userPrompt);
  }

  async validateLogic(params: {
    chapterNum: number;
    content: string;
    outline?: string;
  }): Promise<ValidationResult> {
    const userPrompt = buildValidationUserPrompt({
      chapterNum: params.chapterNum,
      content: params.content,
      outline: params.outline,
    });
    return this.runValidation(LOGIC_VALIDATION_PROMPT, userPrompt);
  }

  async validateClue(params: {
    chapterNum: number;
    content: string;
    clueBook?: string;
    outline?: string;
  }): Promise<ValidationResult> {
    const userPrompt = buildValidationUserPrompt({
      chapterNum: params.chapterNum,
      content: params.content,
      outline: params.outline,
      clueBook: params.clueBook,
    });
    return this.runValidation(CLUE_VALIDATION_PROMPT, userPrompt);
  }

  async validateSetting(params: {
    chapterNum: number;
    content: string;
    worldSetting?: string;
    outline?: string;
  }): Promise<ValidationResult> {
    const userPrompt = buildValidationUserPrompt({
      chapterNum: params.chapterNum,
      content: params.content,
      outline: params.outline,
      worldSetting: params.worldSetting,
    });
    return this.runValidation(SETTING_VALIDATION_PROMPT, userPrompt);
  }

  async validateRhythm(params: {
    chapterNum: number;
    content: string;
    expectedStrand?: string;
    outline?: string;
  }): Promise<ValidationResult> {
    const userPrompt = buildValidationUserPrompt({
      chapterNum: params.chapterNum,
      content: params.content,
      outline: params.outline,
      expectedStrand: params.expectedStrand,
    });
    return this.runValidation(RHYTHM_VALIDATION_PROMPT, userPrompt);
  }

  private async runValidation(systemPrompt: string, userPrompt: string): Promise<ValidationResult> {
    try {
      const response = await this.llm.invoke([
        new SystemMessage(systemPrompt),
        new HumanMessage(userPrompt),
      ]);

      const text = typeof response.content === 'string'
        ? response.content
        : JSON.stringify(response.content);

      return this.parseValidationResult(text);
    } catch (error) {
      return {
        passed: false,
        score: 0,
        issues: [`验证调用失败: ${error instanceof Error ? error.message : '未知错误'}`],
      };
    }
  }

  private parseValidationResult(text: string): ValidationResult {
    try {
      const jsonMatch = text.match(/\{[\s\S]*\}/);
      if (!jsonMatch) {
        return { passed: true, score: 70, issues: ['无法解析验证结果'] };
      }
      const parsed = JSON.parse(jsonMatch[0]);
      return {
        passed: parsed.passed ?? true,
        score: parsed.score ?? parsed.totalScore ?? 70,
        issues: parsed.issues ?? [],
        details: parsed,
      };
    } catch {
      return { passed: true, score: 70, issues: ['JSON解析失败'] };
    }
  }
}
