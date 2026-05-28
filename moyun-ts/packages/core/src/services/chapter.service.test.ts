import { describe, it, expect, beforeEach } from 'vitest';
import Database from 'better-sqlite3';
import { drizzle } from 'drizzle-orm/better-sqlite3';
import { ChapterService } from './chapter.service.js';
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
    CREATE TABLE chapter_versions (
      id TEXT PRIMARY KEY, chapter_id TEXT NOT NULL, version_num INTEGER NOT NULL,
      content TEXT NOT NULL, created_at INTEGER NOT NULL
    );
    CREATE TABLE chapter_summaries (
      chapter_id TEXT PRIMARY KEY, fingerprint TEXT, keywords TEXT,
      characters TEXT, locations TEXT, relations TEXT
    );
  `);
  const now = Date.now();
  sqlite.exec(`INSERT INTO novels (id, title, genre, created_at, updated_at) VALUES ('novel1', '测试小说', 'xuanhuan', ${now}, ${now})`);
  return drizzle(sqlite, { schema });
}

describe('ChapterService', () => {
  let service: ChapterService;

  beforeEach(() => {
    const db = createTestDb();
    service = new ChapterService(db);
  });

  it('should create a chapter', () => {
    const ch = service.create({ novelId: 'novel1', chapterNum: 1, title: '第一章 开端' });
    expect(ch.id).toBeDefined();
    expect(ch.chapterNum).toBe(1);
    expect(ch.title).toBe('第一章 开端');
    expect(ch.wordCount).toBe(0);
    expect(ch.isDeleted).toBe(false);
    expect(ch.createdAt).toBeInstanceOf(Date);
  });

  it('should create a chapter with content and count words', () => {
    const content = '这是第一章的内容，讲述了一个勇敢的少年踏上旅途。Hello world';
    const ch = service.create({ novelId: 'novel1', chapterNum: 1, content });
    expect(ch.content).toBe(content);
    expect(ch.wordCount).toBe(34); // all non-whitespace chars
  });

  it('should list chapters for a novel', () => {
    service.create({ novelId: 'novel1', chapterNum: 1 });
    service.create({ novelId: 'novel1', chapterNum: 2 });
    service.create({ novelId: 'novel1', chapterNum: 3 });
    const list = service.list('novel1');
    expect(list).toHaveLength(3);
    expect(list[0].chapterNum).toBe(1);
    expect(list[1].chapterNum).toBe(2);
    expect(list[2].chapterNum).toBe(3);
  });

  it('should return empty list for novel with no chapters', () => {
    expect(service.list('novel1')).toEqual([]);
  });

  it('should get chapter by number', () => {
    service.create({ novelId: 'novel1', chapterNum: 1, title: '第一章' });
    const ch = service.getByNum('novel1', 1);
    expect(ch).not.toBeNull();
    expect(ch!.title).toBe('第一章');
    expect(ch!.chapterNum).toBe(1);
  });

  it('should return null for non-existent chapter number', () => {
    expect(service.getByNum('novel1', 999)).toBeNull();
  });

  it('should get chapter by id', () => {
    const created = service.create({ novelId: 'novel1', chapterNum: 1, title: '第一章' });
    const found = service.getById(created.id);
    expect(found).not.toBeNull();
    expect(found!.id).toBe(created.id);
    expect(found!.title).toBe('第一章');
  });

  it('should return null for non-existent id', () => {
    expect(service.getById('nonexistent')).toBeNull();
  });

  it('should update chapter title', () => {
    service.create({ novelId: 'novel1', chapterNum: 1, title: '原标题' });
    const updated = service.update('novel1', 1, { title: '新标题' });
    expect(updated.title).toBe('新标题');
  });

  it('should update chapter content and word count', () => {
    service.create({ novelId: 'novel1', chapterNum: 1 });
    const content = '这是第一章的内容，讲述了一个勇敢的少年踏上旅途的故事。';
    const updated = service.update('novel1', 1, { content });
    expect(updated.content).toBe(content);
    expect(updated.wordCount).toBe(27); // all non-whitespace chars
  });

  it('should update outline and strandType', () => {
    service.create({ novelId: 'novel1', chapterNum: 1 });
    const outline = JSON.stringify({ summary: '概述', keyPoints: ['要点1'], hook: '钩子' });
    const updated = service.update('novel1', 1, { outline, strandType: 'quest' });
    expect(updated.outline).toEqual({ summary: '概述', keyPoints: ['要点1'], hook: '钩子' });
    expect(updated.strandType).toBe('quest');
  });

  it('should throw when updating non-existent chapter', () => {
    expect(() => service.update('novel1', 999, { title: 'x' }))
      .toThrow('Chapter 999 not found in novel novel1');
  });

  it('should create version snapshot on update', () => {
    service.create({ novelId: 'novel1', chapterNum: 1, content: '初稿内容' });
    service.update('novel1', 1, { content: '修改稿内容' });
    const versions = service.getVersions('novel1', 1);
    expect(versions.length).toBe(1);
    expect(versions[0].content).toBe('初稿内容');
    expect(versions[0].versionNum).toBe(1);
  });

  it('should create multiple version snapshots', () => {
    service.create({ novelId: 'novel1', chapterNum: 1, content: '第一稿' });
    service.update('novel1', 1, { content: '第二稿' });
    service.update('novel1', 1, { content: '第三稿' });
    const versions = service.getVersions('novel1', 1);
    expect(versions.length).toBe(2);
    expect(versions[0].content).toBe('第一稿');
    expect(versions[1].content).toBe('第二稿');
  });

  it('should not create version snapshot when original has no content', () => {
    service.create({ novelId: 'novel1', chapterNum: 1 });
    service.update('novel1', 1, { content: '新内容' });
    const versions = service.getVersions('novel1', 1);
    expect(versions.length).toBe(0);
  });

  it('should soft delete a chapter', () => {
    service.create({ novelId: 'novel1', chapterNum: 1 });
    service.softDelete('novel1', 1);
    const ch = service.getByNum('novel1', 1);
    expect(ch).toBeNull();
  });

  it('should not list deleted chapters', () => {
    service.create({ novelId: 'novel1', chapterNum: 1 });
    service.create({ novelId: 'novel1', chapterNum: 2 });
    service.softDelete('novel1', 1);
    const list = service.list('novel1');
    expect(list).toHaveLength(1);
    expect(list[0].chapterNum).toBe(2);
  });

  it('should throw when soft-deleting non-existent chapter', () => {
    expect(() => service.softDelete('novel1', 999)).toThrow('Chapter 999 not found');
  });

  it('should still get deleted chapter by id', () => {
    const created = service.create({ novelId: 'novel1', chapterNum: 1 });
    service.softDelete('novel1', 1);
    // getById does not filter by isDeleted, so deleted chapters are still accessible
    const ch = service.getById(created.id);
    expect(ch).not.toBeNull();
    expect(ch!.isDeleted).toBe(true);
  });

  it('should get versions for non-existent chapter', () => {
    expect(service.getVersions('novel1', 999)).toEqual([]);
  });

  it('should count Chinese and English words correctly', () => {
    const ch = service.create({ novelId: 'novel1', chapterNum: 1, content: '你好World世界Test' });
    expect(ch.wordCount).toBe(13); // all non-whitespace chars
  });

  it('should handle empty content word count', () => {
    const ch = service.create({ novelId: 'novel1', chapterNum: 1, content: '' });
    expect(ch.wordCount).toBe(0);
  });

  it('should default optional fields to null', () => {
    const ch = service.create({ novelId: 'novel1', chapterNum: 1 });
    expect(ch.title).toBeNull();
    expect(ch.content).toBeNull();
    expect(ch.outline).toBeNull();
    expect(ch.plotFingerprint).toBeNull();
    expect(ch.strandType).toBeNull();
  });

  it('upsert creates new chapter if not exists', () => {
    const ch = service.upsert({ novelId: 'novel1', chapterNum: 5, title: '第五章', content: '内容' });
    expect(ch.chapterNum).toBe(5);
    expect(ch.title).toBe('第五章');
  });

  it('upsert updates existing chapter', () => {
    service.create({ novelId: 'novel1', chapterNum: 1, title: '旧标题' });
    const ch = service.upsert({ novelId: 'novel1', chapterNum: 1, title: '新标题', content: '新内容' });
    expect(ch.title).toBe('新标题');
    expect(ch.content).toBe('新内容');
  });
});
