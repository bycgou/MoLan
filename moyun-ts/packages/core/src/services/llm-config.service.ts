import { eq } from 'drizzle-orm';
import { nanoid } from 'nanoid';
import { llmConfigs } from '../db/schema.js';
import type { Db } from '../db/index.js';
import type { LlmConfig, CreateLlmConfigInput } from '../types/llm-config.js';

export class LlmConfigService {
  constructor(private db: Db) {}

  create(input: CreateLlmConfigInput): LlmConfig {
    const id = nanoid(12);
    this.db.insert(llmConfigs).values({
      id,
      provider: input.provider,
      model: input.model,
      apiKey: input.apiKey ?? null,
      baseUrl: input.baseUrl ?? null,
      authType: 'api_key',
      usageType: input.usageType ?? 'general',
      temperature: input.temperature ?? 0.7,
      isActive: true,
    }).run();
    return this.getById(id)!;
  }

  list(): LlmConfig[] {
    const rows = this.db.select().from(llmConfigs).all();
    return rows.map(r => this.rowToConfig(r));
  }

  getById(id: string): LlmConfig | null {
    const rows = this.db.select().from(llmConfigs).where(eq(llmConfigs.id, id)).all();
    if (rows.length === 0) return null;
    return this.rowToConfig(rows[0]);
  }

  getActiveByUsage(usageType: string): LlmConfig | null {
    const rows = this.db.select().from(llmConfigs)
      .where(eq(llmConfigs.usageType, usageType))
      .all();
    const active = rows.find(r => r.isActive);
    return active ? this.rowToConfig(active) : null;
  }

  update(id: string, input: Partial<CreateLlmConfigInput & { isActive: boolean }>): LlmConfig {
    const existing = this.getById(id);
    if (!existing) throw new Error(`LLM config ${id} not found`);

    const updates: Partial<typeof llmConfigs.$inferInsert> = {};
    if (input.provider !== undefined) updates.provider = input.provider;
    if (input.model !== undefined) updates.model = input.model;
    if (input.apiKey !== undefined) updates.apiKey = input.apiKey;
    if (input.baseUrl !== undefined) updates.baseUrl = input.baseUrl;
    if (input.usageType !== undefined) updates.usageType = input.usageType;
    if (input.temperature !== undefined) updates.temperature = input.temperature;
    if (input.isActive !== undefined) updates.isActive = input.isActive;

    this.db.update(llmConfigs).set(updates).where(eq(llmConfigs.id, id)).run();
    return this.getById(id)!;
  }

  delete(id: string): void {
    this.db.delete(llmConfigs).where(eq(llmConfigs.id, id)).run();
  }

  private rowToConfig(row: typeof llmConfigs.$inferSelect): LlmConfig {
    return {
      id: row.id,
      provider: row.provider as LlmConfig['provider'],
      model: row.model,
      apiKey: row.apiKey,
      baseUrl: row.baseUrl,
      authType: row.authType ?? 'api_key',
      usageType: (row.usageType ?? 'general') as LlmConfig['usageType'],
      temperature: row.temperature ?? 0.7,
      isActive: row.isActive ?? true,
    };
  }
}
