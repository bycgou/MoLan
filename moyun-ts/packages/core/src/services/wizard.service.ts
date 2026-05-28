import { eq, and, isNull } from 'drizzle-orm';
import { nanoid } from 'nanoid';
import type { Db } from '../db/index.js';
import { novels, arcs, chapters } from '../db/schema.js';
import { VolumeOutlineChain, type VolumeOutlineResult } from '../ai/chains/volume-outline.chain.js';
import { OutlineChain } from '../ai/chains/outline.chain.js';
import { getLLMByUsage } from '../ai/llm-factory.js';
import { ChapterGenerationService } from '../ai/chapter-generation.service.js';
import { safeJsonParse } from '../utils/json.js';

export interface WizardSettings {
  novelName: string;
  genre: string;
  totalOutline?: string;
  worldSetting?: string;
  novelType?: string;
  tone?: string;
  tabooList?: string[];
  styleCardId?: string;
}

export interface ArcChapterOutline {
  chapterNum: number;
  chapterTitle: string;
  outline: string;
  ideas: string[];
}

export type BatchStatus = 'generating' | 'completed' | 'failed' | 'cancelled';

export interface BatchProgress {
  taskId: string;
  status: BatchStatus;
  total: number;
  completed: number;
  failed: number;
  currentChapterNum?: number;
  errorMessage?: string;
}

const batchTasks = new Map<string, BatchProgress>();
const batchCancelled = new Set<string>();

export class WizardService {
  constructor(private db: Db) {}

  // Step 1: Generate volume outlines from settings
  async generateVolumeOutlines(settings: WizardSettings): Promise<VolumeOutlineResult[]> {
    const llm = getLLMByUsage(this.db, 'writing');
    const chain = new VolumeOutlineChain(llm);
    return chain.execute({
      totalOutline: settings.totalOutline,
      genre: settings.genre,
      worldSetting: settings.worldSetting,
      novelType: settings.novelType,
      tone: settings.tone,
      tabooList: settings.tabooList,
    });
  }

  // Step 3: Save novel with arcs
  saveNovelWithArcs(settings: WizardSettings, arcOutlines: VolumeOutlineResult[]): string {
    const now = new Date();
    const novelId = nanoid(12);

    // Create novel with creationMode='wizard'
    this.db.insert(novels).values({
      id: novelId,
      title: settings.novelName,
      genre: settings.genre,
      status: 'draft' as const,
      creationMode: 'wizard' as const,
      totalOutline: settings.totalOutline ?? null,
      worldSetting: settings.worldSetting ?? null,
      storyTags: settings.novelType ? JSON.stringify([settings.novelType]) : null,
      toneTags: settings.tone ? JSON.stringify([settings.tone]) : null,
      tabooList: settings.tabooList ? JSON.stringify(settings.tabooList) : null,
      createdAt: now,
      updatedAt: now,
    }).run();

    // Insert arcs
    for (let i = 0; i < arcOutlines.length; i++) {
      const arc = arcOutlines[i];
      this.db.insert(arcs).values({
        id: nanoid(12),
        novelId,
        arcIndex: i,
        name: arc.arcName,
        outline: JSON.stringify({
          text: arc.arcOutline,
          startChapter: arc.startChapter,
          endChapter: arc.endChapter,
          chapterCount: arc.chapterCount,
        }),
        strandType: arc.rhythmTag,
        rhythmIntensity: arc.rhythmIntensity,
      }).run();
    }

    return novelId;
  }

  // Step 4: Generate chapter outlines for an arc
  async generateArcChapterOutlines(novelId: string, arcIndex: number): Promise<ArcChapterOutline[]> {
    // Get the arc by index
    const arcRows = this.db.select().from(arcs)
      .where(and(eq(arcs.novelId, novelId), eq(arcs.arcIndex, arcIndex)))
      .all();
    if (arcRows.length === 0) {
      throw new Error(`Arc ${arcIndex} not found for novel ${novelId}`);
    }
    const arc = arcRows[0];

    // Get novel settings for context
    const novelRow = this.db.select().from(novels).where(eq(novels.id, novelId)).all();
    if (novelRow.length === 0) {
      throw new Error(`Novel ${novelId} not found`);
    }
    const novel = novelRow[0];

    // Get LLM for outline generation
    const llm = getLLMByUsage(this.db, 'outline');

    // Determine chapter range from arc outline metadata
    const arcData = safeJsonParse<{ text?: string; startChapter?: number; endChapter?: number }>(arc.outline);
    const startChapter = arcData?.startChapter ?? (arcIndex * 15 + 1);
    const endChapter = arcData?.endChapter ?? (startChapter + 14);
    const arcText = arcData?.text ?? arc.outline ?? '';

    // Get novel-level context
    const totalOutline = novel.totalOutline ?? '';
    const worldSetting = novel.worldSetting ?? '';

    // Generate outline for each chapter in the arc
    const outlineChain = new OutlineChain(llm);
    const results: ArcChapterOutline[] = [];

    for (let chNum = startChapter; chNum <= endChapter; chNum++) {
      // Check if chapter already exists with content
      const existingChapter = this.db.select().from(chapters)
        .where(and(
          eq(chapters.novelId, novelId),
          eq(chapters.chapterNum, chNum),
          eq(chapters.isDeleted, false),
        ))
        .all();

      if (existingChapter.length > 0 && existingChapter[0].content) {
        // Chapter already has content, skip outline generation but record it
        results.push({
          chapterNum: chNum,
          chapterTitle: existingChapter[0].title ?? `第${chNum}章`,
          outline: existingChapter[0].outline ?? '',
          ideas: [],
        });
        continue;
      }

      // Get recent chapters context
      const recentChapters = this.db.select().from(chapters)
        .where(and(
          eq(chapters.novelId, novelId),
          eq(chapters.isDeleted, false),
        ))
        .all()
        .sort((a, b) => a.chapterNum - b.chapterNum)
        .slice(-3)
        .map(ch => `第${ch.chapterNum}章 ${ch.title ?? ''}: ${ch.content?.substring(0, 200) ?? ''}`)
        .join('\n');

      const outlineResult = await outlineChain.execute({
        novelId,
        chapterNum: chNum,
        totalOutline: `【当前卷：${arc.name}】\n${arcText}\n\n【总大纲】\n${totalOutline}`,
        worldSetting,
        recentChapters: recentChapters || undefined,
      });

      // Save chapter with outline
      const now = new Date();
      if (existingChapter.length > 0) {
        // Update existing chapter with outline
        this.db.update(chapters)
          .set({
            title: outlineResult.title,
            outline: JSON.stringify(outlineResult),
            updatedAt: now,
          })
          .where(eq(chapters.id, existingChapter[0].id))
          .run();
      } else {
        // Create new chapter with outline
        this.db.insert(chapters).values({
          id: nanoid(12),
          novelId,
          chapterNum: chNum,
          title: outlineResult.title,
          outline: JSON.stringify(outlineResult),
          content: null,
          plotFingerprint: null,
          strandType: arc.strandType,
          wordCount: 0,
          isDeleted: false,
          createdAt: now,
          updatedAt: now,
        }).run();
      }

      results.push({
        chapterNum: chNum,
        chapterTitle: outlineResult.title,
        outline: outlineResult.summary,
        ideas: outlineResult.key_points,
      });
    }

    return results;
  }

  // Step 5: Start batch generation
  async startBatchGeneration(novelId: string): Promise<string> {
    const taskId = nanoid(12);

    // Find chapters with outline but no content
    const pendingChapters = this.db.select().from(chapters)
      .where(and(
        eq(chapters.novelId, novelId),
        isNull(chapters.content),
        eq(chapters.isDeleted, false),
      ))
      .all()
      .filter(ch => ch.outline) // Only chapters with outlines
      .sort((a, b) => a.chapterNum - b.chapterNum);

    if (pendingChapters.length === 0) {
      throw new Error('No chapters with outlines found for batch generation');
    }

    // Initialize progress
    const progress: BatchProgress = {
      taskId,
      status: 'generating',
      total: pendingChapters.length,
      completed: 0,
      failed: 0,
    };
    batchTasks.set(taskId, progress);

    // Run async (not blocking)
    this.runBatchGeneration(taskId, novelId, pendingChapters.map(ch => ch.chapterNum));

    return taskId;
  }

  getBatchProgress(taskId: string): BatchProgress | undefined {
    return batchTasks.get(taskId);
  }

  cancelBatchGeneration(taskId: string): void {
    batchCancelled.add(taskId);
    const progress = batchTasks.get(taskId);
    if (progress) {
      progress.status = 'cancelled';
    }
  }

  private async runBatchGeneration(taskId: string, novelId: string, chapterNums: number[]): Promise<void> {
    const progress = batchTasks.get(taskId)!;
    const generationService = new ChapterGenerationService(this.db);

    for (const chNum of chapterNums) {
      // Check if cancelled
      if (batchCancelled.has(taskId)) {
        progress.status = 'cancelled';
        batchCancelled.delete(taskId);
        return;
      }

      progress.currentChapterNum = chNum;

      try {
        await generationService.generate({ novelId, chapterNum: chNum });
        progress.completed++;
      } catch (err) {
        progress.failed++;
        console.error(`[WizardService] Failed to generate chapter ${chNum}:`, err);
      }
    }

    if (progress.status !== 'cancelled') {
      progress.status = progress.failed > 0 ? 'failed' : 'completed';
    }
    progress.currentChapterNum = undefined;
  }

}
