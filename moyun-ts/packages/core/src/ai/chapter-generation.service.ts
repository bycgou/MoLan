import type { Db } from '../db/index.js';
import { chapterSummaries } from '../db/schema.js';
import { nanoid } from 'nanoid';

import { getLLMByUsage } from './llm-factory.js';
import { ContextBuilder } from './context-builder.js';
import { OutlineChain, type OutlineResult } from './chains/outline.chain.js';
import { WritingChain, type WritingResult } from './chains/writing.chain.js';
import { SummaryChain, type SummaryResult } from './chains/summary.chain.js';
import { ValidationService, type ValidationResults } from './validation.service.js';
import { QualityScorer, type QualityScore } from './quality-scorer.js';
import { ChapterService } from '../services/chapter.service.js';
import { countWords } from '../utils/text.js';

export interface GenerationResult {
  chapterNum: number;
  title: string;
  content: string;
  wordCount: number;
  outline: OutlineResult;
  summary: SummaryResult;
  validation: ValidationResults;
  quality: QualityScore;
  retries: number;
}

export interface GenerationCallbacks {
  onOutline?: (outline: OutlineResult) => void;
  onWritingStart?: () => void;
  onWritingToken?: (token: string) => void;
  onWritingDone?: (result: WritingResult) => void;
  onValidation?: (results: ValidationResults) => void;
  onQuality?: (score: QualityScore) => void;
  onSummary?: (summary: SummaryResult) => void;
}

export class ChapterGenerationService {
  private contextBuilder: ContextBuilder;
  private chapterService: ChapterService;

  constructor(private db: Db) {
    this.contextBuilder = new ContextBuilder(db);
    this.chapterService = new ChapterService(db);
  }

  async generate(params: {
    novelId: string;
    chapterNum: number;
    callbacks?: GenerationCallbacks;
  }): Promise<GenerationResult> {
    const { novelId, chapterNum, callbacks } = params;

    // 获取LLM配置
    const llm = getLLMByUsage(this.db, 'writing');
    const outlineLLM = getLLMByUsage(this.db, 'outline');

    // 1. 上下文组装
    const context = this.contextBuilder.build(novelId, chapterNum);

    // 2. 大纲生成
    const outlineChain = new OutlineChain(outlineLLM);
    const outline = await outlineChain.execute({
      novelId,
      chapterNum,
      totalOutline: context.storyState,
      worldSetting: context.storyState,
      recentChapters: context.recentChapters,
      lastHook: context.lastHook,
      characterStates: context.characterStates,
      clueBook: context.clueBook,
      storyLineReview: context.storyLineReview,
    });
    callbacks?.onOutline?.(outline);

    // 3. 写作
    const writingChain = new WritingChain(llm);
    callbacks?.onWritingStart?.();

    let writingResult: WritingResult;
    if (callbacks?.onWritingToken) {
      // 流式模式
      let fullContent = '';
      for await (const token of writingChain.executeStream({
        novelId,
        chapterNum,
        title: outline.title,
        outline: JSON.stringify(outline),
        context: this.buildWritingContext(context),
      })) {
        fullContent += token;
        callbacks.onWritingToken(token);
      }
      writingResult = {
        content: fullContent,
        wordCount: countWords(fullContent),
        clicheCount: 0,
        cliches: [],
      };
    } else {
      writingResult = await writingChain.execute({
        novelId,
        chapterNum,
        title: outline.title,
        outline: JSON.stringify(outline),
        context: this.buildWritingContext(context),
      });
    }
    callbacks?.onWritingDone?.(writingResult);

    // 4. 五维验证 + 重试
    let validation: ValidationResults | null = null;
    let retries = 0;
    const maxRetries = 2;

    const validationService = new ValidationService(llm);
    validation = await validationService.validateAll({
      chapterNum,
      content: writingResult.content,
      outline: JSON.stringify(outline),
      characterStates: context.characterStates,
      clueBook: context.clueBook,
      worldSetting: context.storyState,
    });
    callbacks?.onValidation?.(validation);

    // 如果验证失败，带反馈重写
    while (!validation.allPassed && retries < maxRetries) {
      retries++;
      const feedback = this.buildValidationFeedback(validation);
      const rewriteResult = await writingChain.execute({
        novelId,
        chapterNum,
        title: outline.title,
        outline: JSON.stringify(outline),
        context: this.buildWritingContext(context) + `\n\n【修改要求】\n${feedback}`,
      });
      writingResult = rewriteResult;

      validation = await validationService.validateAll({
        chapterNum,
        content: writingResult.content,
        outline: JSON.stringify(outline),
        characterStates: context.characterStates,
        clueBook: context.clueBook,
        worldSetting: context.storyState,
      });
      callbacks?.onValidation?.(validation);
    }

    // 5. 质量评分
    const qualityScorer = new QualityScorer(llm);
    const quality = await qualityScorer.score({
      content: writingResult.content,
      outline: JSON.stringify(outline),
      clueBook: context.clueBook,
    });
    callbacks?.onQuality?.(quality);

    // 6. 摘要生成
    const summaryChain = new SummaryChain(llm);
    const summary = await summaryChain.execute({
      chapterNum,
      content: writingResult.content,
    });
    callbacks?.onSummary?.(summary);

    // 7. 保存
    this.chapterService.upsert({
      novelId,
      chapterNum,
      title: outline.title,
      content: writingResult.content,
    });

    // 保存摘要
    const chapter = this.chapterService.getByNum(novelId, chapterNum);
    if (chapter) {
      this.db.insert(chapterSummaries).values({
        chapterId: chapter.id,
        fingerprint: summary.fingerprint,
        keywords: JSON.stringify(summary.keywords),
        characters: JSON.stringify(summary.characters),
        locations: JSON.stringify(summary.locations),
        relations: JSON.stringify(summary.relations),
      }).run();
    }

    return {
      chapterNum,
      title: outline.title,
      content: writingResult.content,
      wordCount: writingResult.wordCount,
      outline,
      summary,
      validation,
      quality,
      retries,
    };
  }

  private buildWritingContext(context: ReturnType<ContextBuilder['build']>): string {
    const parts: string[] = [];
    if (context.storyState && context.storyState !== '暂无故事状态') {
      parts.push(`【故事状态】\n${context.storyState}`);
    }
    if (context.characterStates && context.characterStates !== '暂无角色') {
      parts.push(`【人物当前状态】\n${context.characterStates}`);
    }
    if (context.clueBook && context.clueBook !== '暂无未回收伏笔') {
      parts.push(`【待回收伏笔】\n${context.clueBook}`);
    }
    if (context.recentChapters && context.recentChapters !== '暂无前期章节') {
      parts.push(`【近期章节】\n${context.recentChapters}`);
    }
    if (context.lastHook && context.lastHook !== '无（第一章或无大纲）') {
      parts.push(`【上一章Hook】\n${context.lastHook}`);
    }
    return parts.join('\n\n');
  }

  private buildValidationFeedback(validation: ValidationResults): string {
    const issues: string[] = [];
    if (!validation.ooc.passed) issues.push(`角色一致性问题: ${validation.ooc.issues.join('；')}`);
    if (!validation.logic.passed) issues.push(`逻辑问题: ${validation.logic.issues.join('；')}`);
    if (!validation.clue.passed) issues.push(`伏笔问题: ${validation.clue.issues.join('；')}`);
    if (!validation.setting.passed) issues.push(`设定问题: ${validation.setting.issues.join('；')}`);
    if (!validation.rhythm.passed) issues.push(`节奏问题: ${validation.rhythm.issues.join('；')}`);
    return issues.join('\n');
  }
}
