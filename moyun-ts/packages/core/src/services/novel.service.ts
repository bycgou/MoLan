import { eq, sql } from 'drizzle-orm';
import { nanoid } from 'nanoid';
import { novels, chapters } from '../db/schema.js';
import type { Db } from '../db/index.js';
import type { Novel, CreateNovelInput, UpdateNovelInput } from '../types/novel.js';
import { safeJsonParse } from '../utils/json.js';

export class NovelService {
  constructor(private db: Db) {}

  create(input: CreateNovelInput): Novel {
    const now = new Date();
    const id = nanoid(12);
    const row = {
      id,
      title: input.title,
      genre: input.genre,
      status: 'draft' as const,
      creationMode: input.creationMode ?? 'classic',
      totalOutline: input.totalOutline ?? null,
      worldSetting: input.worldSetting ?? null,
      storyTags: input.storyTags ? JSON.stringify(input.storyTags) : null,
      toneTags: input.toneTags ? JSON.stringify(input.toneTags) : null,
      tabooList: input.tabooList ? JSON.stringify(input.tabooList) : null,
      createdAt: now,
      updatedAt: now,
    };
    this.db.insert(novels).values(row).run();
    return this.rowToNovel(row);
  }

  list(): Novel[] {
    const rows = this.db.select().from(novels).all();
    return rows.map(r => this.rowToNovel(r));
  }

  getById(id: string): Novel | null {
    const rows = this.db.select().from(novels).where(eq(novels.id, id)).all();
    if (rows.length === 0) return null;
    return this.rowToNovel(rows[0]);
  }

  update(id: string, input: UpdateNovelInput): Novel {
    const existing = this.getById(id);
    if (!existing) throw new Error(`Novel ${id} not found`);

    const updates: Partial<typeof novels.$inferInsert> = { updatedAt: new Date() };
    if (input.title !== undefined) updates.title = input.title;
    if (input.genre !== undefined) updates.genre = input.genre;
    if (input.status !== undefined) updates.status = input.status;
    if (input.totalOutline !== undefined) updates.totalOutline = input.totalOutline;
    if (input.worldSetting !== undefined) updates.worldSetting = input.worldSetting;
    if (input.storyTags !== undefined) updates.storyTags = JSON.stringify(input.storyTags);
    if (input.toneTags !== undefined) updates.toneTags = JSON.stringify(input.toneTags);
    if (input.tabooList !== undefined) updates.tabooList = JSON.stringify(input.tabooList);

    this.db.update(novels).set(updates).where(eq(novels.id, id)).run();
    return this.getById(id)!;
  }

  delete(id: string): void {
    this.db.delete(novels).where(eq(novels.id, id)).run();
  }

  exportTxt(novelId: string): string {
    const novel = this.getById(novelId);
    if (!novel) throw new Error(`Novel ${novelId} not found`);

    const chapterList = this.db.select().from(chapters)
      .where(eq(chapters.novelId, novelId))
      .orderBy(chapters.chapterNum)
      .all();

    const parts: string[] = [];
    parts.push(novel.title);
    parts.push('='.repeat(40));
    if (novel.totalOutline) {
      parts.push('');
      parts.push(novel.totalOutline);
      parts.push('');
    }
    parts.push('='.repeat(40));

    for (const ch of chapterList) {
      if (ch.isDeleted) continue;
      parts.push('');
      parts.push(`第${ch.chapterNum}章 ${ch.title ?? ''}`);
      parts.push('-'.repeat(20));
      if (ch.content) {
        // Strip any HTML tags if present
        const clean = ch.content.replace(/<[^>]*>/g, '');
        parts.push(clean);
      }
    }

    return parts.join('\n');
  }

  getWordCount(novelId: string): number {
    const result = this.db
      .select({ total: sql<number>`coalesce(sum(${chapters.wordCount}), 0)` })
      .from(chapters)
      .where(eq(chapters.novelId, novelId))
      .get();
    return result?.total ?? 0;
  }

  private rowToNovel(row: typeof novels.$inferSelect): Novel {
    return {
      id: row.id,
      title: row.title,
      genre: row.genre as Novel['genre'],
      status: row.status as Novel['status'],
      creationMode: (row.creationMode ?? 'classic') as Novel['creationMode'],
      totalOutline: row.totalOutline,
      worldSetting: row.worldSetting,
      storyTags: safeJsonParse(row.storyTags) as string[] | null,
      toneTags: safeJsonParse(row.toneTags) as string[] | null,
      tabooList: safeJsonParse(row.tabooList) as string[] | null,
      createdAt: row.createdAt,
      updatedAt: row.updatedAt,
    };
  }
}
