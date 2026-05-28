import { describe, it, expect, beforeEach } from 'vitest';
import Database from 'better-sqlite3';
import { drizzle } from 'drizzle-orm/better-sqlite3';
import { ClueService } from './clue.service.js';
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
    CREATE TABLE clues (
      id TEXT PRIMARY KEY, novel_id TEXT NOT NULL REFERENCES novels(id),
      title TEXT NOT NULL, type TEXT NOT NULL, description TEXT,
      bury_chapter INTEGER, recycle_chapter INTEGER, is_recycled INTEGER DEFAULT 0
    );
  `);
  const now = Date.now();
  sqlite.exec(`INSERT INTO novels (id, title, genre, created_at, updated_at) VALUES ('novel1', '测试', 'xuanhuan', ${now}, ${now})`);
  return drizzle(sqlite, { schema });
}

describe('ClueService', () => {
  let service: ClueService;

  beforeEach(() => {
    const db = createTestDb();
    service = new ClueService(db);
  });

  it('should create a clue', () => {
    const clue = service.create({ novelId: 'novel1', title: '神秘玉佩', type: 'main_plot' });
    expect(clue.id).toBeDefined();
    expect(clue.title).toBe('神秘玉佩');
    expect(clue.isRecycled).toBe(false);
    expect(clue.description).toBeNull();
    expect(clue.buryChapter).toBeNull();
    expect(clue.recycleChapter).toBeNull();
  });

  it('should create a clue with optional fields', () => {
    const clue = service.create({
      novelId: 'novel1',
      title: '伏笔线索',
      type: 'dark_line',
      description: '主角身世之谜',
      buryChapter: 3,
    });
    expect(clue.description).toBe('主角身世之谜');
    expect(clue.buryChapter).toBe(3);
  });

  it('should list all clues', () => {
    service.create({ novelId: 'novel1', title: '线索1', type: 'main_plot' });
    service.create({ novelId: 'novel1', title: '线索2', type: 'subplot' });
    expect(service.list('novel1')).toHaveLength(2);
  });

  it('should list only active clues', () => {
    const c1 = service.create({ novelId: 'novel1', title: '线索1', type: 'main_plot' });
    service.create({ novelId: 'novel1', title: '线索2', type: 'subplot' });
    service.recycle(c1.id, 5);
    const active = service.listActive('novel1');
    expect(active).toHaveLength(1);
    expect(active[0].title).toBe('线索2');
  });

  it('should get clue by id', () => {
    const clue = service.create({ novelId: 'novel1', title: '伏笔', type: 'dark_line' });
    const found = service.getById(clue.id);
    expect(found).not.toBeNull();
    expect(found!.title).toBe('伏笔');
    expect(found!.type).toBe('dark_line');
  });

  it('should return null for non-existent clue', () => {
    expect(service.getById('nonexistent')).toBeNull();
  });

  it('should recycle a clue', () => {
    const clue = service.create({ novelId: 'novel1', title: '伏笔', type: 'dark_line', buryChapter: 1 });
    const recycled = service.recycle(clue.id, 10);
    expect(recycled.isRecycled).toBe(true);
    expect(recycled.recycleChapter).toBe(10);
  });

  it('should throw on recycle of non-existent clue', () => {
    expect(() => service.recycle('nonexistent', 1)).toThrow('Clue nonexistent not found');
  });
});
