import { describe, it, expect, beforeEach } from 'vitest';
import Database from 'better-sqlite3';
import { drizzle } from 'drizzle-orm/better-sqlite3';
import { StyleCardService } from './style-card.service.js';
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
    CREATE TABLE style_cards (
      id TEXT PRIMARY KEY, name TEXT NOT NULL,
      metrics TEXT, sample_text TEXT, is_public INTEGER DEFAULT 0,
      rhythm_rules TEXT, dialogue_rules TEXT, description_rules TEXT,
      emotion_rules TEXT, structure_rules TEXT, forbidden_rules TEXT,
      preferred_rules TEXT, style_description TEXT, generation_template TEXT,
      samples_json TEXT, stats_json TEXT, source_novel TEXT, author_name TEXT,
      tags TEXT, status INTEGER DEFAULT 1
    );
    CREATE TABLE novel_style_bindings (
      novel_id TEXT NOT NULL REFERENCES novels(id),
      style_card_id TEXT NOT NULL REFERENCES style_cards(id),
      PRIMARY KEY (novel_id, style_card_id)
    );
  `);
  const now = Date.now();
  sqlite.exec(`INSERT INTO novels (id, title, genre, created_at, updated_at) VALUES ('novel1', '测试', 'xuanhuan', ${now}, ${now})`);
  return drizzle(sqlite, { schema });
}

describe('StyleCardService', () => {
  let service: StyleCardService;

  beforeEach(() => {
    const db = createTestDb();
    service = new StyleCardService(db);
  });

  it('should create a style card', () => {
    const card = service.create({ name: '玄幻风格' });
    expect(card.id).toBeDefined();
    expect(card.name).toBe('玄幻风格');
    expect(card.isPublic).toBe(false);
    expect(card.metrics).toBeNull();
    expect(card.sampleText).toBeNull();
  });

  it('should create with metrics', () => {
    const metrics = { avgSentenceLength: 20, shortSentenceRatio: 0.3, dialogueRatio: 0.4, avgParagraphLength: 80 };
    const card = service.create({ name: '测试', metrics });
    expect(card.metrics).toEqual(metrics);
  });

  it('should list cards', () => {
    service.create({ name: '卡1' });
    service.create({ name: '卡2' });
    expect(service.list()).toHaveLength(2);
  });

  it('should update a card', () => {
    const card = service.create({ name: '原名' });
    const updated = service.update(card.id, { name: '新名' });
    expect(updated.name).toBe('新名');
  });

  it('should delete a card', () => {
    const card = service.create({ name: '要删' });
    service.delete(card.id);
    expect(service.getById(card.id)).toBeNull();
  });

  it('should bind and unbind style card to novel', () => {
    const card = service.create({ name: '绑定测试' });
    service.bindToNovel('novel1', card.id);
    const bound = service.getBoundCards('novel1');
    expect(bound).toHaveLength(1);
    expect(bound[0].name).toBe('绑定测试');

    service.unbindFromNovel('novel1', card.id);
    expect(service.getBoundCards('novel1')).toHaveLength(0);
  });

  it('should throw on update of non-existent card', () => {
    expect(() => service.update('nonexistent', { name: 'test' })).toThrow();
  });
});
