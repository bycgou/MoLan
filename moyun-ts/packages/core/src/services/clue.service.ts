import { eq, and } from 'drizzle-orm';
import { nanoid } from 'nanoid';
import { clues } from '../db/schema.js';
import type { Db } from '../db/index.js';
import type { Clue, CreateClueInput } from '../types/clue.js';

export class ClueService {
  constructor(private db: Db) {}

  create(input: CreateClueInput): Clue {
    const id = nanoid(12);
    this.db.insert(clues).values({
      id,
      novelId: input.novelId,
      title: input.title,
      type: input.type,
      description: input.description ?? null,
      buryChapter: input.buryChapter ?? null,
      recycleChapter: null,
      isRecycled: false,
    }).run();
    return {
      id,
      novelId: input.novelId,
      title: input.title,
      type: input.type,
      description: input.description ?? null,
      buryChapter: input.buryChapter ?? null,
      recycleChapter: null,
      isRecycled: false,
    };
  }

  list(novelId: string): Clue[] {
    const rows = this.db.select().from(clues).where(eq(clues.novelId, novelId)).all();
    return rows.map(r => this.rowToClue(r));
  }

  listActive(novelId: string): Clue[] {
    const rows = this.db.select().from(clues)
      .where(and(eq(clues.novelId, novelId), eq(clues.isRecycled, false)))
      .all();
    return rows.map(r => this.rowToClue(r));
  }

  getById(id: string): Clue | null {
    const rows = this.db.select().from(clues).where(eq(clues.id, id)).all();
    if (rows.length === 0) return null;
    return this.rowToClue(rows[0]);
  }

  recycle(id: string, recycleChapter: number): Clue {
    const existing = this.getById(id);
    if (!existing) throw new Error(`Clue ${id} not found`);
    this.db.update(clues).set({
      isRecycled: true,
      recycleChapter,
    }).where(eq(clues.id, id)).run();
    return this.getById(id)!;
  }

  private rowToClue(row: typeof clues.$inferSelect): Clue {
    return {
      id: row.id,
      novelId: row.novelId,
      title: row.title,
      type: row.type as Clue['type'],
      description: row.description,
      buryChapter: row.buryChapter,
      recycleChapter: row.recycleChapter,
      isRecycled: row.isRecycled ?? false,
    };
  }
}
