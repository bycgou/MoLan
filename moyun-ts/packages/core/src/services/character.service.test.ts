import { describe, it, expect, beforeEach } from 'vitest';
import Database from 'better-sqlite3';
import { drizzle } from 'drizzle-orm/better-sqlite3';
import { CharacterService } from './character.service.js';
import * as schema from '../db/schema.js';

function createTestDb() {
  const sqlite = new Database(':memory:');
  sqlite.pragma('journal_mode = WAL');
  sqlite.exec(`
    CREATE TABLE novels (
      id TEXT PRIMARY KEY, title TEXT NOT NULL, genre TEXT NOT NULL,
      status TEXT NOT NULL DEFAULT 'draft', creation_mode TEXT DEFAULT 'classic',
      total_outline TEXT, world_setting TEXT, story_tags TEXT, tone_tags TEXT,
      taboo_list TEXT, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL
    );
    CREATE TABLE characters (
      id TEXT PRIMARY KEY, novel_id TEXT NOT NULL REFERENCES novels(id),
      name TEXT NOT NULL, role TEXT NOT NULL, description TEXT, personality TEXT, background TEXT
    );
    CREATE TABLE character_growth (
      id TEXT PRIMARY KEY, character_id TEXT NOT NULL REFERENCES characters(id),
      chapter_num INTEGER NOT NULL, title TEXT NOT NULL, type TEXT NOT NULL,
      description TEXT
    );
  `);
  const now = Date.now();
  sqlite.exec(`INSERT INTO novels (id, title, genre, created_at, updated_at) VALUES ('novel1', '测试', 'xuanhuan', ${now}, ${now})`);
  return drizzle(sqlite, { schema });
}

describe('CharacterService', () => {
  let service: CharacterService;

  beforeEach(() => {
    const db = createTestDb();
    service = new CharacterService(db);
  });

  it('should create a character', () => {
    const ch = service.create({ novelId: 'novel1', name: '李云', role: 'protagonist' });
    expect(ch.id).toBeDefined();
    expect(ch.name).toBe('李云');
    expect(ch.role).toBe('protagonist');
    expect(ch.description).toBeNull();
    expect(ch.personality).toBeNull();
    expect(ch.background).toBeNull();
    expect(ch.growthNodes).toEqual([]);
  });

  it('should create a character with optional fields', () => {
    const ch = service.create({
      novelId: 'novel1',
      name: '李云',
      role: 'protagonist',
      description: '少年英雄',
      personality: '坚毅',
      background: '孤儿出身',
    });
    expect(ch.description).toBe('少年英雄');
    expect(ch.personality).toBe('坚毅');
    expect(ch.background).toBe('孤儿出身');
  });

  it('should list characters by novel', () => {
    service.create({ novelId: 'novel1', name: '李云', role: 'protagonist' });
    service.create({ novelId: 'novel1', name: '张三', role: 'supporting' });
    const list = service.list('novel1');
    expect(list).toHaveLength(2);
    expect(list[0].name).toBe('李云');
    expect(list[1].name).toBe('张三');
  });

  it('should get character by id with growth nodes', () => {
    const ch = service.create({ novelId: 'novel1', name: '李云', role: 'protagonist' });
    service.addGrowthNode(ch.id, { chapterNum: 1, title: '觉醒', type: 'ability' });
    const found = service.getById(ch.id);
    expect(found).not.toBeNull();
    expect(found!.growthNodes).toHaveLength(1);
    expect(found!.growthNodes[0].title).toBe('觉醒');
    expect(found!.growthNodes[0].type).toBe('ability');
  });

  it('should return null for non-existent character', () => {
    expect(service.getById('nonexistent')).toBeNull();
  });

  it('should update a character', () => {
    const ch = service.create({ novelId: 'novel1', name: '李云', role: 'protagonist' });
    const updated = service.update(ch.id, { name: '李云飞', role: 'supporting' });
    expect(updated.name).toBe('李云飞');
    expect(updated.role).toBe('supporting');
    expect(updated.novelId).toBe('novel1');
  });

  it('should throw on update of non-existent character', () => {
    expect(() => service.update('nonexistent', { name: 'test' })).toThrow('Character nonexistent not found');
  });

  it('should delete a character', () => {
    const ch = service.create({ novelId: 'novel1', name: '李云', role: 'protagonist' });
    service.delete(ch.id);
    expect(service.getById(ch.id)).toBeNull();
  });

  it('should add and remove growth nodes', () => {
    const ch = service.create({ novelId: 'novel1', name: '李云', role: 'protagonist' });
    const node = service.addGrowthNode(ch.id, { chapterNum: 5, title: '突破', type: 'ability' });
    expect(node.characterId).toBe(ch.id);
    expect(node.chapterNum).toBe(5);
    expect(service.getById(ch.id)?.growthNodes).toHaveLength(1);

    service.removeGrowthNode(node.id);
    expect(service.getById(ch.id)?.growthNodes).toHaveLength(0);
  });

  it('should support multiple growth types', () => {
    const ch = service.create({ novelId: 'novel1', name: '李云', role: 'protagonist' });
    service.addGrowthNode(ch.id, { chapterNum: 1, title: '觉醒', type: 'ability' });
    service.addGrowthNode(ch.id, { chapterNum: 3, title: '成熟', type: 'personality' });
    service.addGrowthNode(ch.id, { chapterNum: 5, title: '结盟', type: 'relationship' });
    const found = service.getById(ch.id);
    expect(found?.growthNodes).toHaveLength(3);
    expect(found?.growthNodes.map(g => g.type)).toEqual(['ability', 'personality', 'relationship']);
  });
});
