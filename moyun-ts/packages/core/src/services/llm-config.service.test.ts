import { describe, it, expect, beforeEach } from 'vitest';
import Database from 'better-sqlite3';
import { drizzle } from 'drizzle-orm/better-sqlite3';
import { LlmConfigService } from './llm-config.service.js';
import * as schema from '../db/schema.js';

function createTestDb() {
  const sqlite = new Database(':memory:');
  sqlite.exec(`
    CREATE TABLE llm_configs (
      id TEXT PRIMARY KEY, provider TEXT NOT NULL, model TEXT NOT NULL,
      api_key TEXT, base_url TEXT, auth_type TEXT DEFAULT 'api_key',
      usage_type TEXT DEFAULT 'general', temperature REAL DEFAULT 0.7,
      is_active INTEGER DEFAULT 1, config_name TEXT
    );
  `);
  return drizzle(sqlite, { schema });
}

describe('LlmConfigService', () => {
  let db: ReturnType<typeof createTestDb>;
  let service: LlmConfigService;

  beforeEach(() => {
    db = createTestDb();
    service = new LlmConfigService(db);
  });

  it('should create an LLM config', () => {
    const config = service.create({ provider: 'deepseek', model: 'deepseek-chat' });
    expect(config.id).toBeDefined();
    expect(config.provider).toBe('deepseek');
    expect(config.model).toBe('deepseek-chat');
    expect(config.isActive).toBe(true);
    expect(config.temperature).toBe(0.7);
  });

  it('should list configs', () => {
    service.create({ provider: 'deepseek', model: 'deepseek-chat' });
    service.create({ provider: 'openai', model: 'gpt-4' });
    expect(service.list()).toHaveLength(2);
  });

  it('should get active config by usage type', () => {
    service.create({ provider: 'deepseek', model: 'deepseek-chat', usageType: 'writing' });
    service.create({ provider: 'openai', model: 'gpt-4', usageType: 'general' });
    const writing = service.getActiveByUsage('writing');
    expect(writing?.provider).toBe('deepseek');
  });

  it('should update a config', () => {
    const config = service.create({ provider: 'deepseek', model: 'deepseek-chat' });
    const updated = service.update(config.id, { model: 'deepseek-v3', temperature: 0.5 });
    expect(updated.model).toBe('deepseek-v3');
    expect(updated.temperature).toBe(0.5);
  });

  it('should delete a config', () => {
    const config = service.create({ provider: 'deepseek', model: 'deepseek-chat' });
    service.delete(config.id);
    expect(service.getById(config.id)).toBeNull();
  });

  it('should throw on update of non-existent config', () => {
    expect(() => service.update('nonexistent', { model: 'test' })).toThrow();
  });
});
