import type { FastifyInstance } from 'fastify';
import { getDb, ChapterService, WritingChain, ValidationService, SummaryChain, countWords, getLLMByUsage } from '@moyun/core';

const cancelSignals = new Map<string, AbortController>();

export function registerStreamingRoutes(app: FastifyInstance) {
  // SSE streaming write
  app.post('/api/chapters/stream-write', async (req, reply) => {
    const { novelId, chapterNum, outline, context, styleCard } = req.body as {
      novelId: string; chapterNum: number; outline: string; context: string; styleCard?: string;
    };

    reply.raw.writeHead(200, {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache',
      'Connection': 'keep-alive',
    });

    const abortController = new AbortController();
    const signalKey = `${novelId}:${chapterNum}`;
    cancelSignals.set(signalKey, abortController);

    try {
      const db = getDb();
      const chapterService = new ChapterService(db);

      // Save version snapshot before rewrite
      const existing = chapterService.getByNum(novelId, chapterNum);
      if (existing?.content) {
        chapterService.update(novelId, chapterNum, { content: existing.content });
      }

      // Stream generation
      const llm = getLLMByUsage(db, 'writing');
      const writingChain = new WritingChain(llm);
      let fullContent = '';

      for await (const token of writingChain.executeStream({
        novelId, chapterNum, title: '', outline, context, styleCard,
        signal: abortController.signal,
      })) {
        if (abortController.signal.aborted) break;
        fullContent += token;
        reply.raw.write(`data: ${JSON.stringify({ token })}\n\n`);
      }

      // Post-completion: validation + summary + save
      if (!abortController.signal.aborted) {
        const validationLLM = getLLMByUsage(db, 'writing');
        const validationService = new ValidationService(validationLLM);
        const validation = await validationService.validateAll({
          chapterNum, content: fullContent, outline,
        });
        reply.raw.write(`event: validation\ndata: ${JSON.stringify(validation)}\n\n`);

        const summaryLLM = getLLMByUsage(db, 'writing');
        const summaryChain = new SummaryChain(summaryLLM);
        const summary = await summaryChain.execute({ chapterNum, content: fullContent });

        chapterService.upsert({ novelId, chapterNum, content: fullContent });

        reply.raw.write(`event: complete\ndata: ${JSON.stringify({ wordCount: countWords(fullContent), summary })}\n\n`);
      }
    } catch (err: any) {
      reply.raw.write(`event: error\ndata: ${JSON.stringify({ message: err.message })}\n\n`);
    } finally {
      cancelSignals.delete(signalKey);
      reply.raw.end();
    }
  });

  // Cancel endpoint
  app.post('/api/chapters/cancel', async (req) => {
    const { novelId, chapterNum } = req.body as { novelId: string; chapterNum: number };
    cancelSignals.get(`${novelId}:${chapterNum}`)?.abort();
    return { ok: true };
  });

  // Manual save endpoint
  app.post('/api/chapters/stream-save', async (req) => {
    const { novelId, chapterNum, content, outline } = req.body as {
      novelId: string; chapterNum: number; content: string; outline?: string;
    };
    const db = getDb();
    const chapterService = new ChapterService(db);
    chapterService.upsert({ novelId, chapterNum, content, outline });
    return { success: true, wordCount: countWords(content) };
  });
}
