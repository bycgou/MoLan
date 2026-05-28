import { describe, it, expect, beforeEach } from 'vitest';
import Database from 'better-sqlite3';
import { drizzle } from 'drizzle-orm/better-sqlite3';
import { NovelService } from './novel.service.js';
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
    CREATE TABLE chapters (
      id TEXT PRIMARY KEY, novel_id TEXT NOT NULL, chapter_num INTEGER NOT NULL,
      title TEXT, content TEXT, outline TEXT, plot_fingerprint TEXT, strand_type TEXT,
      word_count INTEGER DEFAULT 0, is_deleted INTEGER DEFAULT 0,
      created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL
    );
  `);
  return drizzle(sqlite, { schema });
}

describe('NovelService', () => {
  let service: NovelService;

  beforeEach(() => {
    const db = createTestDb();
    service = new NovelService(db);
  });

  it('should create a novel', () => {
    const novel = service.create({ title: '测试小说', genre: 'xuanhuan' });
    expect(novel.id).toBeDefined();
    expect(novel.title).toBe('测试小说');
    expect(novel.genre).toBe('xuanhuan');
    expect(novel.status).toBe('draft');
    expect(novel.createdAt).toBeInstanceOf(Date);
    expect(novel.updatedAt).toBeInstanceOf(Date);
  });

  it('should create a novel with optional fields', () => {
    const novel = service.create({
      title: '完整小说',
      genre: 'dog-blood-romance',
      totalOutline: '这是一个测试大纲',
      worldSetting: '玄幻世界',
      storyTags: ['热血', '升级'],
      toneTags: ['爽文'],
      tabooList: ['无女主'],
    });
    expect(novel.totalOutline).toBe('这是一个测试大纲');
    expect(novel.worldSetting).toBe('玄幻世界');
    expect(novel.storyTags).toEqual(['热血', '升级']);
    expect(novel.toneTags).toEqual(['爽文']);
    expect(novel.tabooList).toEqual(['无女主']);
  });

  it('should list novels', () => {
    service.create({ title: '小说1', genre: 'xuanhuan' });
    service.create({ title: '小说2', genre: 'dog-blood-romance' });
    const novels = service.list();
    expect(novels).toHaveLength(2);
    expect(novels[0].title).toBe('小说1');
    expect(novels[1].title).toBe('小说2');
  });

  it('should get novel by id', () => {
    const created = service.create({ title: '测试', genre: 'xuanhuan' });
    const found = service.getById(created.id);
    expect(found).not.toBeNull();
    expect(found!.title).toBe('测试');
    expect(found!.id).toBe(created.id);
  });

  it('should return null for non-existent novel', () => {
    expect(service.getById('nonexistent')).toBeNull();
  });

  it('should update a novel', () => {
    const novel = service.create({ title: '原标题', genre: 'xuanhuan' });
    const updated = service.update(novel.id, { title: '新标题' });
    expect(updated.title).toBe('新标题');
    expect(updated.genre).toBe('xuanhuan');
  });

  it('should throw when updating non-existent novel', () => {
    expect(() => service.update('nonexistent', { title: 'x' })).toThrow('Novel nonexistent not found');
  });

  it('should delete a novel', () => {
    const novel = service.create({ title: '要删除', genre: 'xuanhuan' });
    service.delete(novel.id);
    expect(service.getById(novel.id)).toBeNull();
  });

  it('should count words from chapters', () => {
    const novel = service.create({ title: '测试', genre: 'xuanhuan' });
    expect(service.getWordCount(novel.id)).toBe(0);
  });

  it('should default optional fields to null', () => {
    const novel = service.create({ title: '简约', genre: 'realistic' });
    expect(novel.totalOutline).toBeNull();
    expect(novel.worldSetting).toBeNull();
    expect(novel.storyTags).toBeNull();
    expect(novel.toneTags).toBeNull();
    expect(novel.tabooList).toBeNull();
    expect(novel.creationMode).toBe('classic');
  });
});
