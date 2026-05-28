import { describe, it, expect, beforeEach } from 'vitest';
import Database from 'better-sqlite3';
import { drizzle } from 'drizzle-orm/better-sqlite3';
import { RhythmService } from './rhythm.service.js';
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
      id TEXT PRIMARY KEY, novel_id TEXT NOT NULL,
      chapter_num INTEGER NOT NULL, title TEXT, content TEXT, outline TEXT,
      plot_fingerprint TEXT, strand_type TEXT, word_count INTEGER DEFAULT 0,
      is_deleted INTEGER DEFAULT 0, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL
    );
    CREATE TABLE arcs (
      id TEXT PRIMARY KEY, novel_id TEXT NOT NULL,
      arc_index INTEGER NOT NULL, name TEXT NOT NULL, outline TEXT,
      strand_type TEXT, rhythm_intensity INTEGER DEFAULT 5
    );
  `);
  const now = Date.now();
  sqlite.exec(`INSERT INTO novels (id, title, genre, created_at, updated_at) VALUES ('novel1', '测试', 'xuanhuan', ${now}, ${now})`);
  return { db: drizzle(sqlite, { schema }), sqlite };
}

function insertChapter(sqlite: Database.Database, num: number, strandType: string) {
  const now = Date.now();
  sqlite.exec(`INSERT INTO chapters (id, novel_id, chapter_num, strand_type, created_at, updated_at) VALUES ('ch${num}', 'novel1', ${num}, '${strandType}', ${now}, ${now})`);
}

function insertArc(sqlite: Database.Database, arcIndex: number, name: string) {
  sqlite.exec(`INSERT INTO arcs (id, novel_id, arc_index, name) VALUES ('arc${arcIndex}', 'novel1', ${arcIndex}, '${name}')`);
}

describe('RhythmService', () => {
  let sqlite: Database.Database;
  let service: RhythmService;

  beforeEach(() => {
    const created = createTestDb();
    sqlite = created.sqlite;
    service = new RhythmService(created.db);
  });

  it('should return empty status for no chapters', () => {
    const status = service.getStatus('novel1');
    expect(status.lastStrand).toBeNull();
    expect(status.consecutive).toBe(0);
    expect(status.gap).toBe(0);
    expect(status.ratios).toEqual({ quest: 0, fire: 0, constellation: 0 });
  });

  it('should compute ratios correctly', () => {
    insertChapter(sqlite, 1, 'quest');
    insertChapter(sqlite, 2, 'fire');
    insertChapter(sqlite, 3, 'quest');
    const status = service.getStatus('novel1');
    expect(status.ratios.quest).toBeCloseTo(2 / 3);
    expect(status.ratios.fire).toBeCloseTo(1 / 3);
    expect(status.ratios.constellation).toBeCloseTo(0);
  });

  it('should detect consecutive same type', () => {
    insertChapter(sqlite, 1, 'quest');
    insertChapter(sqlite, 2, 'quest');
    insertChapter(sqlite, 3, 'quest');
    const status = service.getStatus('novel1');
    expect(status.lastStrand).toBe('quest');
    expect(status.consecutive).toBe(3);
  });

  it('should reset consecutive count on strand change', () => {
    insertChapter(sqlite, 1, 'quest');
    insertChapter(sqlite, 2, 'quest');
    insertChapter(sqlite, 3, 'fire');
    const status = service.getStatus('novel1');
    expect(status.lastStrand).toBe('fire');
    expect(status.consecutive).toBe(1);
  });

  it('should ignore deleted chapters', () => {
    insertChapter(sqlite, 1, 'quest');
    insertChapter(sqlite, 2, 'quest');
    // Soft-delete chapter 2
    sqlite.exec(`UPDATE chapters SET is_deleted = 1 WHERE id = 'ch2'`);
    const status = service.getStatus('novel1');
    expect(status.lastStrand).toBe('quest');
    expect(status.consecutive).toBe(1);
    expect(status.ratios.quest).toBeCloseTo(1);
  });

  it('should recommend quest for empty novel', () => {
    expect(service.recommendNext('novel1')).toBe('quest');
  });

  it('should recommend alternative after consecutive', () => {
    insertChapter(sqlite, 1, 'quest');
    insertChapter(sqlite, 2, 'quest');
    const rec = service.recommendNext('novel1');
    expect(rec).not.toBe('quest');
    expect(rec).toBe('fire');
  });

  it('should recommend based on ratio balance', () => {
    insertChapter(sqlite, 1, 'quest');
    insertChapter(sqlite, 2, 'fire');
    insertChapter(sqlite, 3, 'fire');
    // ratios: quest=1/3, fire=2/3, constellation=0
    const rec = service.recommendNext('novel1');
    expect(rec).toBe('constellation');
  });

  it('should cycle through strand types in getAlternative', () => {
    // quest -> fire -> constellation -> quest
    insertChapter(sqlite, 1, 'quest');
    insertChapter(sqlite, 2, 'quest');
    expect(service.recommendNext('novel1')).toBe('fire');

    // Reset: fire -> constellation
    sqlite.exec(`DELETE FROM chapters`);
    insertChapter(sqlite, 1, 'fire');
    insertChapter(sqlite, 2, 'fire');
    expect(service.recommendNext('novel1')).toBe('constellation');

    // Reset: constellation -> quest
    sqlite.exec(`DELETE FROM chapters`);
    insertChapter(sqlite, 1, 'constellation');
    insertChapter(sqlite, 2, 'constellation');
    expect(service.recommendNext('novel1')).toBe('quest');
  });

  it('should check arc rhythm and report consecutive issues', () => {
    insertArc(sqlite, 0, '第一卷');
    insertChapter(sqlite, 1, 'quest');
    insertChapter(sqlite, 2, 'quest');
    insertChapter(sqlite, 3, 'quest');
    const result = service.checkArcRhythm('novel1', 0);
    expect(result.ok).toBe(false);
    expect(result.issues).toHaveLength(1);
    expect(result.issues[0]).toContain('quest');
  });

  it('should return ok for balanced arc rhythm', () => {
    insertArc(sqlite, 0, '第一卷');
    insertChapter(sqlite, 1, 'quest');
    insertChapter(sqlite, 2, 'fire');
    insertChapter(sqlite, 3, 'constellation');
    const result = service.checkArcRhythm('novel1', 0);
    expect(result.ok).toBe(true);
    expect(result.issues).toHaveLength(0);
  });

  it('should return ok for non-existent arc', () => {
    const result = service.checkArcRhythm('novel1', 999);
    expect(result.ok).toBe(true);
    expect(result.issues).toHaveLength(0);
  });
});
