import { eq } from 'drizzle-orm';
import { nanoid } from 'nanoid';
import { characters, characterGrowth } from '../db/schema.js';
import type { Db } from '../db/index.js';
import type { Character, GrowthNode, CreateCharacterInput, UpdateCharacterInput } from '../types/character.js';

export class CharacterService {
  constructor(private db: Db) {}

  create(input: CreateCharacterInput): Character {
    const id = nanoid(12);
    this.db.insert(characters).values({
      id,
      novelId: input.novelId,
      name: input.name,
      role: input.role,
      description: input.description ?? null,
      personality: input.personality ?? null,
      background: input.background ?? null,
    }).run();
    return {
      id,
      novelId: input.novelId,
      name: input.name,
      role: input.role,
      description: input.description ?? null,
      personality: input.personality ?? null,
      background: input.background ?? null,
      growthNodes: [],
    };
  }

  list(novelId: string): Character[] {
    const rows = this.db.select().from(characters).where(eq(characters.novelId, novelId)).all();
    return rows.map(r => this.rowToCharacter(r, []));
  }

  getById(id: string): Character | null {
    const rows = this.db.select().from(characters).where(eq(characters.id, id)).all();
    if (rows.length === 0) return null;
    const growth = this.db.select().from(characterGrowth)
      .where(eq(characterGrowth.characterId, id)).all();
    return this.rowToCharacter(rows[0], growth);
  }

  update(id: string, input: UpdateCharacterInput): Character {
    const existing = this.getById(id);
    if (!existing) throw new Error(`Character ${id} not found`);

    const updates: Partial<typeof characters.$inferInsert> = {};
    if (input.name !== undefined) updates.name = input.name;
    if (input.role !== undefined) updates.role = input.role;
    if (input.description !== undefined) updates.description = input.description;
    if (input.personality !== undefined) updates.personality = input.personality;
    if (input.background !== undefined) updates.background = input.background;

    this.db.update(characters).set(updates).where(eq(characters.id, id)).run();
    return this.getById(id)!;
  }

  delete(id: string): void {
    this.db.delete(characters).where(eq(characters.id, id)).run();
  }

  addGrowthNode(characterId: string, node: { chapterNum: number; title: string; type: GrowthNode['type'] }): GrowthNode {
    const id = nanoid(12);
    this.db.insert(characterGrowth).values({
      id,
      characterId,
      chapterNum: node.chapterNum,
      title: node.title,
      type: node.type,
    }).run();
    return { id, characterId, ...node };
  }

  removeGrowthNode(nodeId: string): void {
    this.db.delete(characterGrowth).where(eq(characterGrowth.id, nodeId)).run();
  }

  private rowToCharacter(row: typeof characters.$inferSelect, growth: (typeof characterGrowth.$inferSelect)[]): Character {
    return {
      id: row.id,
      novelId: row.novelId,
      name: row.name,
      role: row.role as Character['role'],
      description: row.description,
      personality: row.personality,
      background: row.background,
      growthNodes: growth.map(g => ({
        id: g.id,
        characterId: g.characterId,
        chapterNum: g.chapterNum,
        title: g.title,
        type: g.type as GrowthNode['type'],
      })),
    };
  }
}
