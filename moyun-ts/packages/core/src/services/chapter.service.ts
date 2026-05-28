import { eq, and, asc, sql } from 'drizzle-orm';
import { nanoid } from 'nanoid';
import { chapters, chapterVersions } from '../db/schema.js';
import type { Db } from '../db/index.js';
import type { Chapter, ChapterListItem, ChapterVersion } from '../types/chapter.js';
import { countWords } from '../utils/text.js';
import { safeJsonParse } from '../utils/json.js';

export class ChapterService {
  constructor(private db: Db) {}

  create(input: { novelId: string; chapterNum: number; title?: string; content?: string }): Chapter {
    const now = new Date();
    const id = nanoid(12);
    const content = input.content ?? null;
    const row = {
      id,
      novelId: input.novelId,
      chapterNum: input.chapterNum,
      title: input.title ?? null,
      content,
      outline: null,
      plotFingerprint: null,
      strandType: null,
      wordCount: content ? countWords(content) : 0,
      isDeleted: false,
      createdAt: now,
      updatedAt: now,
    };
    this.db.insert(chapters).values(row).run();
    return this.rowToChapter(row);
  }

  upsert(input: { novelId: string; chapterNum: number; title?: string; content?: string; outline?: string }): Chapter {
    const existing = this.getByNum(input.novelId, input.chapterNum);
    if (existing) {
      return this.update(input.novelId, input.chapterNum, {
        title: input.title,
        content: input.content,
        outline: input.outline,
      });
    }
    return this.create(input);
  }

  list(novelId: string): ChapterListItem[] {
    const rows = this.db
      .select()
      .from(chapters)
      .where(and(eq(chapters.novelId, novelId), eq(chapters.isDeleted, false)))
      .orderBy(asc(chapters.chapterNum))
      .all();
    return rows.map(r => ({
      id: r.id,
      chapterNum: r.chapterNum,
      title: r.title,
      strandType: r.strandType as ChapterListItem['strandType'],
      wordCount: r.wordCount ?? 0,
      createdAt: r.createdAt,
    }));
  }

  listFull(novelId: string): Chapter[] {
    const rows = this.db
      .select()
      .from(chapters)
      .where(and(eq(chapters.novelId, novelId), eq(chapters.isDeleted, false)))
      .orderBy(asc(chapters.chapterNum))
      .all();
    return rows.map(r => this.rowToChapter(r));
  }

  getByNum(novelId: string, chapterNum: number): Chapter | null {
    const rows = this.db
      .select()
      .from(chapters)
      .where(and(
        eq(chapters.novelId, novelId),
        eq(chapters.chapterNum, chapterNum),
        eq(chapters.isDeleted, false)
      ))
      .all();
    if (rows.length === 0) return null;
    return this.rowToChapter(rows[0]);
  }

  getById(id: string): Chapter | null {
    const rows = this.db.select().from(chapters).where(eq(chapters.id, id)).all();
    if (rows.length === 0) return null;
    return this.rowToChapter(rows[0]);
  }

  update(
    novelId: string,
    chapterNum: number,
    input: { title?: string; content?: string; outline?: string; strandType?: string }
  ): Chapter {
    const existing = this.getByNum(novelId, chapterNum);
    if (!existing) throw new Error(`Chapter ${chapterNum} not found in novel ${novelId}`);

    // Save version snapshot before update
    if (existing.content) {
      const countResult = this.db
        .select({ count: sql<number>`count(*)` })
        .from(chapterVersions)
        .where(eq(chapterVersions.chapterId, existing.id))
        .get();
      this.db.insert(chapterVersions).values({
        id: nanoid(12),
        chapterId: existing.id,
        versionNum: (countResult?.count ?? 0) + 1,
        content: existing.content,
        createdAt: new Date(),
      }).run();
    }

    const updates: Partial<typeof chapters.$inferInsert> = { updatedAt: new Date() };
    if (input.title !== undefined) updates.title = input.title;
    if (input.content !== undefined) {
      updates.content = input.content;
      updates.wordCount = countWords(input.content);
    }
    if (input.outline !== undefined) {
      updates.outline = typeof input.outline === 'string' ? input.outline : JSON.stringify(input.outline);
    }
    if (input.strandType !== undefined) updates.strandType = input.strandType;

    this.db.update(chapters).set(updates).where(eq(chapters.id, existing.id)).run();
    const updated = this.db.select().from(chapters).where(eq(chapters.id, existing.id)).get();
    return this.rowToChapter(updated!);
  }

  softDelete(novelId: string, chapterNum: number): void {
    const existing = this.getByNum(novelId, chapterNum);
    if (!existing) throw new Error(`Chapter ${chapterNum} not found`);
    this.db.update(chapters)
      .set({ isDeleted: true, updatedAt: new Date() })
      .where(eq(chapters.id, existing.id))
      .run();
  }

  getVersions(novelId: string, chapterNum: number): ChapterVersion[] {
    const ch = this.getByNum(novelId, chapterNum);
    if (!ch) return [];
    const rows = this.db
      .select()
      .from(chapterVersions)
      .where(eq(chapterVersions.chapterId, ch.id))
      .all();
    return rows.map(r => ({
      id: r.id,
      chapterId: r.chapterId,
      versionNum: r.versionNum,
      content: r.content,
      createdAt: r.createdAt,
    }));
  }

  private rowToChapter(row: typeof chapters.$inferSelect): Chapter {
    return {
      id: row.id,
      novelId: row.novelId,
      chapterNum: row.chapterNum,
      title: row.title,
      content: row.content,
      outline: row.outline ? safeJsonParse(row.outline) as Chapter['outline'] : null,
      plotFingerprint: row.plotFingerprint,
      strandType: row.strandType as Chapter['strandType'],
      wordCount: row.wordCount ?? 0,
      isDeleted: row.isDeleted ?? false,
      createdAt: row.createdAt,
      updatedAt: row.updatedAt,
    };
  }
}
