import type { ChatOpenAI } from '@langchain/openai';
import { HumanMessage, SystemMessage } from '@langchain/core/messages';
import type { Db } from '../../db/index.js';
import { chapterSummaries } from '../../db/schema.js';
import { EmbeddingService } from './embedding.service.js';
import { VectorStore } from './vector-store.js';
import { BM25Search } from './bm25-search.js';

export interface SearchResult {
  chapterNum: number;
  content: string;
  score: number;
  priority: number;
}

export class RagService {
  private embeddingService: EmbeddingService;
  private vectorStore: VectorStore;
  private bm25: BM25Search;
  private compressedMemoryCache = new Map<string, string>();

  constructor(private db: Db, private llm: ChatOpenAI) {
    this.embeddingService = new EmbeddingService(db);
    this.vectorStore = new VectorStore(db, (a, b) => this.embeddingService.cosineSimilarity(a, b));
    this.bm25 = new BM25Search();
  }

  async hybridSearch(novelId: string, query: string, topK: number = 5): Promise<SearchResult[]> {
    // 1. Vector search
    const embedding = await this.embeddingService.embed(query);
    const vectorResults = this.vectorStore.search(novelId, embedding, topK * 2);

    // 2. BM25 search
    const bm25Results = this.bm25.search(query, novelId, topK * 2);

    // 3. RRF fusion (k=60)
    const rrfScores = new Map<string, { score: number; chapterNum: number; content: string; priority: number }>();

    vectorResults.forEach((r, rank) => {
      const key = `${novelId}:${r.metadata.chapterNum}`;
      rrfScores.set(key, {
        score: 1 / (60 + rank),
        chapterNum: r.metadata.chapterNum,
        content: r.metadata.content ?? '',
        priority: r.metadata.priority,
      });
    });

    bm25Results.forEach((r, rank) => {
      const key = `${novelId}:${r.doc.chapterNum}`;
      const existing = rrfScores.get(key);
      if (existing) {
        existing.score += 1 / (60 + rank);
      } else {
        rrfScores.set(key, {
          score: 1 / (60 + rank),
          chapterNum: r.doc.chapterNum,
          content: r.doc.content,
          priority: 5,
        });
      }
    });

    return [...rrfScores.values()]
      .sort((a, b) => b.score - a.score)
      .slice(0, topK);
  }

  async saveSummary(novelId: string, chapterNum: number, content: string, keywords: string[]): Promise<void> {
    const embedding = await this.embeddingService.embed(content);
    this.vectorStore.save(`${novelId}:${chapterNum}`, novelId, chapterNum, embedding, {
      content, keywords: keywords.join(','), priority: 5,
    });
    this.bm25.addDocument({
      id: `${novelId}:${chapterNum}`, novelId, chapterNum, content, keywords: keywords.join(' '),
    });
  }

  async compressMemory(novelId: string, startChapter: number, endChapter: number): Promise<string> {
    const cacheKey = `${novelId}:${startChapter}:${endChapter}`;
    if (this.compressedMemoryCache.has(cacheKey)) {
      return this.compressedMemoryCache.get(cacheKey)!;
    }

    const summaries = this.db.select().from(chapterSummaries).all();
    const combined = summaries.map((s: any) => s.fingerprint ?? '').filter(Boolean).join('\n');
    if (combined.length < 2000) return combined;

    const response = await this.llm.invoke([
      new SystemMessage('你是一位小说编辑，擅长将章节摘要压缩为精炼的全局背景记忆。保留关键人物、主线剧情、重要伏笔。'),
      new HumanMessage(`请将以下 ${endChapter - startChapter + 1} 章的摘要压缩为800字以内的"全局背景记忆"：\n\n${combined}`),
    ]);

    const compressed = typeof response.content === 'string' ? response.content : JSON.stringify(response.content);
    this.compressedMemoryCache.set(cacheKey, compressed);
    return compressed;
  }

  async assembleContext(params: {
    novelId: string;
    chapterNum: number;
    totalOutline?: string;
    currentArcOutline?: string;
    clueBook?: string;
    characterStates?: string;
    query: string;
  }): Promise<string> {
    const parts: string[] = [];

    // L0: Global compressed memory
    if (params.chapterNum > 20) {
      try {
        const compressed = await this.compressMemory(params.novelId, 1, params.chapterNum - 10);
        if (compressed) parts.push(`【全局背景记忆】\n${compressed}`);
      } catch { /* optional */ }
    }

    // L1: Total outline
    if (params.totalOutline) parts.push(`【总大纲】\n${params.totalOutline.substring(0, 2000)}`);

    // L2: Current arc outline
    if (params.currentArcOutline) parts.push(`【当前卷纲】\n${params.currentArcOutline.substring(0, 1000)}`);

    // L3: Unresolved clues
    if (params.clueBook) parts.push(`【待回收伏笔】\n${params.clueBook}`);

    // L4: Character states
    if (params.characterStates) parts.push(`【人物状态】\n${params.characterStates.substring(0, 1000)}`);

    // L5: Hybrid search
    if (params.query) {
      const ragResults = await this.hybridSearch(params.novelId, params.query, 5);
      if (ragResults.length > 0) {
        parts.push(`【相关剧情回忆】\n${ragResults.map(r => `第${r.chapterNum}章: ${r.content.substring(0, 200)}`).join('\n')}`);
      }
    }

    return parts.join('\n\n');
  }
}
