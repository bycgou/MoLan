import { describe, it, expect, beforeEach } from 'vitest';
import Database from 'better-sqlite3';
import { drizzle } from 'drizzle-orm/better-sqlite3';
import * as schema from '../../db/schema.js';
import { VectorStore } from './vector-store.js';
import { EmbeddingService } from './embedding.service.js';

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
    CREATE TABLE llm_configs (
      id TEXT PRIMARY KEY, provider TEXT NOT NULL, model TEXT NOT NULL,
      api_key TEXT, base_url TEXT, auth_type TEXT DEFAULT 'api_key',
      usage_type TEXT DEFAULT 'general', temperature REAL DEFAULT 0.7,
      is_active INTEGER DEFAULT 1, config_name TEXT
    );
    CREATE TABLE vectors (
      id TEXT PRIMARY KEY, novel_id TEXT NOT NULL, chapter_num INTEGER NOT NULL,
      content TEXT, keywords TEXT, priority INTEGER DEFAULT 5,
      embedding TEXT NOT NULL, created_at INTEGER NOT NULL
    );
  `);
  return drizzle(sqlite, { schema });
}

// Helper to insert a test novel
function insertTestNovel(db: ReturnType<typeof createTestDb>) {
  const now = new Date();
  db.insert(schema.novels).values({
    id: 'novel-1',
    title: 'Test Novel',
    genre: 'xuanhuan',
    createdAt: now,
    updatedAt: now,
  }).run();
}

// Deterministic cosine similarity (same algorithm as EmbeddingService)
function cosineSimilarity(a: Float32Array, b: Float32Array): number {
  if (a.length !== b.length) return 0;
  let dot = 0, normA = 0, normB = 0;
  for (let i = 0; i < a.length; i++) {
    dot += a[i] * b[i];
    normA += a[i] * a[i];
    normB += b[i] * b[i];
  }
  const denom = Math.sqrt(normA) * Math.sqrt(normB);
  return denom === 0 ? 0 : dot / denom;
}

describe('VectorStore', () => {
  let store: VectorStore;
  let db: ReturnType<typeof createTestDb>;

  beforeEach(() => {
    db = createTestDb();
    insertTestNovel(db);
    store = new VectorStore(db, cosineSimilarity);
  });

  it('should save and search a vector', () => {
    const embedding = new Float32Array([1, 0, 0]);
    store.save('v1', 'novel-1', 1, embedding, { content: 'Chapter 1 content', keywords: 'test', priority: 8 });

    const results = store.search('novel-1', new Float32Array([1, 0, 0]));
    expect(results).toHaveLength(1);
    expect(results[0].id).toBe('v1');
    expect(results[0].score).toBeCloseTo(1.0, 5);
    expect(results[0].metadata.chapterNum).toBe(1);
    expect(results[0].metadata.content).toBe('Chapter 1 content');
    expect(results[0].metadata.keywords).toBe('test');
    expect(results[0].metadata.priority).toBe(8);
  });

  it('should rank results by similarity', () => {
    store.save('v1', 'novel-1', 1, new Float32Array([1, 0, 0]), { content: 'A' });
    store.save('v2', 'novel-1', 2, new Float32Array([0, 1, 0]), { content: 'B' });
    store.save('v3', 'novel-1', 3, new Float32Array([0.7, 0.7, 0]), { content: 'C' });

    const results = store.search('novel-1', new Float32Array([1, 0, 0]), 10);
    expect(results).toHaveLength(3);
    // v1 should be most similar (exact match)
    expect(results[0].id).toBe('v1');
    expect(results[0].score).toBeCloseTo(1.0, 5);
    // v3 should be second (partial match)
    expect(results[1].id).toBe('v3');
    // v2 should be last (orthogonal)
    expect(results[2].id).toBe('v2');
    expect(results[2].score).toBeCloseTo(0, 5);
  });

  it('should respect topK limit', () => {
    for (let i = 0; i < 10; i++) {
      store.save(`v${i}`, 'novel-1', i, new Float32Array([1, 0, 0]), {});
    }
    const results = store.search('novel-1', new Float32Array([1, 0, 0]), 3);
    expect(results).toHaveLength(3);
  });

  it('should delete a vector', () => {
    store.save('v1', 'novel-1', 1, new Float32Array([1, 0, 0]), {});
    expect(store.search('novel-1', new Float32Array([1, 0, 0]))).toHaveLength(1);

    store.delete('v1');
    expect(store.search('novel-1', new Float32Array([1, 0, 0]))).toHaveLength(0);
  });

  it('should return empty for non-existent novel', () => {
    store.save('v1', 'novel-1', 1, new Float32Array([1, 0, 0]), {});
    const results = store.search('novel-999', new Float32Array([1, 0, 0]));
    expect(results).toHaveLength(0);
  });

  it('should default metadata fields', () => {
    store.save('v1', 'novel-1', 1, new Float32Array([1, 0, 0]), {});
    const results = store.search('novel-1', new Float32Array([1, 0, 0]));
    expect(results[0].metadata.content).toBeNull();
    expect(results[0].metadata.keywords).toBeNull();
    expect(results[0].metadata.priority).toBe(5);
  });
});

describe('EmbeddingService', () => {
  it('cosineSimilarity returns 1 for identical vectors', () => {
    const db = createTestDb();
    const service = new EmbeddingService(db);
    const a = new Float32Array([1, 2, 3]);
    expect(service.cosineSimilarity(a, a)).toBeCloseTo(1.0, 5);
  });

  it('cosineSimilarity returns 0 for orthogonal vectors', () => {
    const db = createTestDb();
    const service = new EmbeddingService(db);
    expect(service.cosineSimilarity(new Float32Array([1, 0]), new Float32Array([0, 1]))).toBeCloseTo(0, 5);
  });

  it('cosineSimilarity returns 0 for different length vectors', () => {
    const db = createTestDb();
    const service = new EmbeddingService(db);
    expect(service.cosineSimilarity(new Float32Array([1, 0]), new Float32Array([1, 0, 0]))).toBe(0);
  });

  it('cosineSimilarity returns 0 for zero vectors', () => {
    const db = createTestDb();
    const service = new EmbeddingService(db);
    expect(service.cosineSimilarity(new Float32Array([0, 0]), new Float32Array([1, 0]))).toBe(0);
  });

  it('cosineSimilarity computes correct value', () => {
    const db = createTestDb();
    const service = new EmbeddingService(db);
    // [1,1] vs [1,0] => dot=1, |a|=sqrt(2), |b|=1 => 1/sqrt(2) ≈ 0.7071
    const result = service.cosineSimilarity(new Float32Array([1, 1]), new Float32Array([1, 0]));
    expect(result).toBeCloseTo(1 / Math.sqrt(2), 5);
  });

  it('throws when no LLM config exists for embed()', async () => {
    const db = createTestDb();
    const service = new EmbeddingService(db);
    await expect(service.embed('test')).rejects.toThrow('未配置LLM');
  });
});
