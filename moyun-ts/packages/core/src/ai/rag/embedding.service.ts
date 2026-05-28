import type { Db } from '../../db/index.js';
import { llmConfigs } from '../../db/schema.js';
import { eq } from 'drizzle-orm';

export class EmbeddingService {
  constructor(private db: Db) {}

  async embed(text: string): Promise<Float32Array> {
    const config = this.getConfig();
    const response = await fetch(config.baseUrl + '/embeddings', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${config.apiKey}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        model: config.model,
        input: text.substring(0, 2000),
      }),
    });

    if (!response.ok) {
      throw new Error(`Embedding API failed: ${response.status} ${response.statusText}`);
    }

    const data = await response.json() as { data: Array<{ embedding: number[] }> };
    return new Float32Array(data.data[0].embedding);
  }

  cosineSimilarity(a: Float32Array, b: Float32Array): number {
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

  private getConfig(): { baseUrl: string; apiKey: string; model: string } {
    const rows = this.db.select().from(llmConfigs)
      .where(eq(llmConfigs.usageType, 'embedding'))
      .all();
    const config = rows.find((r: any) => r.isActive) ?? rows[0];
    if (!config) {
      const generalRows = this.db.select().from(llmConfigs)
        .where(eq(llmConfigs.usageType, 'general'))
        .all();
      const general = generalRows.find((r: any) => r.isActive) ?? generalRows[0];
      if (!general) throw new Error('未配置LLM，无法生成embedding');
      return { baseUrl: general.baseUrl ?? 'https://api.openai.com/v1', apiKey: general.apiKey ?? '', model: general.model };
    }
    return { baseUrl: config.baseUrl ?? 'https://api.openai.com/v1', apiKey: config.apiKey ?? '', model: config.model };
  }
}
