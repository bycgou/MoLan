import { eq, and } from 'drizzle-orm';
import { nanoid } from 'nanoid';
import { styleCards, novelStyleBindings } from '../db/schema.js';
import type { Db } from '../db/index.js';
import type { StyleCard, StyleMetrics } from '../types/style-card.js';
import { safeJsonParse } from '../utils/json.js';

export class StyleCardService {
  constructor(private db: Db) {}

  create(input: { name: string; metrics?: StyleMetrics; sampleText?: string; isPublic?: boolean }): StyleCard {
    const id = nanoid(12);
    this.db.insert(styleCards).values({
      id,
      name: input.name,
      metrics: input.metrics ? JSON.stringify(input.metrics) : null,
      sampleText: input.sampleText ?? null,
      isPublic: input.isPublic ?? false,
    }).run();
    return this.getById(id)!;
  }

  list(): StyleCard[] {
    const rows = this.db.select().from(styleCards).all();
    return rows.map(r => this.rowToCard(r));
  }

  getById(id: string): StyleCard | null {
    const rows = this.db.select().from(styleCards).where(eq(styleCards.id, id)).all();
    if (rows.length === 0) return null;
    return this.rowToCard(rows[0]);
  }

  update(id: string, input: { name?: string; metrics?: StyleMetrics; sampleText?: string }): StyleCard {
    const existing = this.getById(id);
    if (!existing) throw new Error(`Style card ${id} not found`);

    const updates: Partial<typeof styleCards.$inferInsert> = {};
    if (input.name !== undefined) updates.name = input.name;
    if (input.metrics !== undefined) updates.metrics = JSON.stringify(input.metrics);
    if (input.sampleText !== undefined) updates.sampleText = input.sampleText;

    this.db.update(styleCards).set(updates).where(eq(styleCards.id, id)).run();
    return this.getById(id)!;
  }

  delete(id: string): void {
    this.db.delete(styleCards).where(eq(styleCards.id, id)).run();
  }

  bindToNovel(novelId: string, styleCardId: string): void {
    this.db.insert(novelStyleBindings).values({ novelId, styleCardId }).run();
  }

  unbindFromNovel(novelId: string, styleCardId: string): void {
    this.db.delete(novelStyleBindings)
      .where(and(
        eq(novelStyleBindings.novelId, novelId),
        eq(novelStyleBindings.styleCardId, styleCardId),
      ))
      .run();
  }

  getBoundCards(novelId: string): StyleCard[] {
    const rows = this.db
      .select()
      .from(novelStyleBindings)
      .innerJoin(styleCards, eq(novelStyleBindings.styleCardId, styleCards.id))
      .where(eq(novelStyleBindings.novelId, novelId))
      .all();
    return rows.map(r => this.rowToCard(r.style_cards));
  }

  buildSystemStyleRules(card: { forbiddenRules?: string; dialogueRules?: string }): string {
    const parts: string[] = [];
    const forbidden = safeJsonParse<{ words?: string[]; structures?: string[] }>(card.forbiddenRules ?? null);
    if (forbidden?.words?.length) {
      parts.push(`【绝对禁止出现的词语】\n${forbidden.words.join('、')}`);
    }
    if (forbidden?.structures?.length) {
      parts.push(`【绝对禁止的句式】\n${forbidden.structures.join('\n')}`);
    }
    const dialogue = safeJsonParse<{ style?: string; quirks?: string[] }>(card.dialogueRules ?? null);
    if (dialogue?.style) {
      parts.push(`【对话风格规则】\n${dialogue.style}`);
      if (dialogue.quirks?.length) parts.push(`对话特色: ${dialogue.quirks.join('、')}`);
    }
    return parts.join('\n\n');
  }

  buildUserStyleSamples(card: {
    name: string; styleDescription?: string; generationTemplate?: string;
    preferredRules?: string; samplesJson?: string;
  }): string {
    const parts: string[] = [];
    parts.push(`【文风：${card.name}】`);
    parts.push('文风规则与系统指令冲突时，以系统指令为准');

    if (card.styleDescription) parts.push(`\n${card.styleDescription}`);
    if (card.generationTemplate) parts.push(`\n${card.generationTemplate}`);

    const preferred = safeJsonParse<{ words?: string[]; structures?: string[] }>(card.preferredRules ?? null);
    if (preferred?.words?.length) parts.push(`\n【偏好用词】\n${preferred.words.join('、')}`);
    if (preferred?.structures?.length) parts.push(`\n【偏好句式】\n${preferred.structures.join('\n')}`);

    const samples = safeJsonParse<Array<{ type: string; text: string; annotation: string }>>(card.samplesJson ?? null);
    if (samples?.length) {
      parts.push('\n【参考片段】');
      for (const s of samples.slice(0, 5)) {
        parts.push(`(${s.type}) "${s.text.substring(0, 300)}"\n  → ${s.annotation}`);
      }
    }

    return parts.join('\n').substring(0, 3000);
  }

  private rowToCard(row: typeof styleCards.$inferSelect): StyleCard {
    return {
      id: row.id,
      name: row.name,
      metrics: row.metrics ? safeJsonParse(row.metrics) as StyleMetrics : null,
      sampleText: row.sampleText,
      isPublic: row.isPublic ?? false,
    };
  }
}
