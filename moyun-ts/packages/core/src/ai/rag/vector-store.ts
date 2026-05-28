import { eq } from 'drizzle-orm';
import type { Db } from '../../db/index.js';
import { vectors } from '../../db/schema.js';

export interface VectorSearchResult {
  id: string;
  score: number;
  metadata: { chapterNum: number; content: string | null; keywords: string | null; priority: number };
}

export class VectorStore {
  constructor(private db: Db, private cosineSimilarity: (a: Float32Array, b: Float32Array) => number) {}

  save(key: string, novelId: string, chapterNum: number, embedding: Float32Array, metadata: { content?: string; keywords?: string; priority?: number }): void {
    this.db.insert(vectors).values({
      id: key,
      novelId,
      chapterNum,
      content: metadata.content ?? null,
      keywords: metadata.keywords ?? null,
      priority: metadata.priority ?? 5,
      embedding: Buffer.from(embedding.buffer, embedding.byteOffset, embedding.byteLength).toString('base64'),
      createdAt: new Date(),
    }).run();
  }

  search(novelId: string, queryEmbedding: Float32Array, topK: number = 5): VectorSearchResult[] {
    const rows = this.db.select().from(vectors).where(eq(vectors.novelId, novelId)).all();
    return rows
      .map(row => {
        const buf = Buffer.from(row.embedding, 'base64');
        const storedEmbedding = new Float32Array(buf.buffer.slice(buf.byteOffset, buf.byteOffset + buf.byteLength));
        return {
          id: row.id,
          score: this.cosineSimilarity(queryEmbedding, storedEmbedding),
          metadata: {
            chapterNum: row.chapterNum,
            content: row.content,
            keywords: row.keywords,
            priority: row.priority ?? 5,
          },
        };
      })
      .sort((a, b) => b.score - a.score)
      .slice(0, topK);
  }

  delete(key: string): void {
    this.db.delete(vectors).where(eq(vectors.id, key)).run();
  }
}
