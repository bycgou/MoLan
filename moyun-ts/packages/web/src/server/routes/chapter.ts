import type { FastifyInstance } from 'fastify';
import { getDb, ChapterService } from '@moyun/core';

export function registerChapterRoutes(app: FastifyInstance) {
  app.get('/api/novels/:novelId/chapters', async (req) => {
    const { novelId } = req.params as { novelId: string };
    const service = new ChapterService(getDb());
    return service.list(novelId);
  });

  app.get('/api/chapters/:id', async (req, reply) => {
    const { id } = req.params as { id: string };
    const service = new ChapterService(getDb());
    const ch = service.getById(id);
    if (!ch) return reply.status(404).send({ error: 'Chapter not found' });
    return ch;
  });

  app.get('/api/novels/:novelId/chapters/:num', async (req, reply) => {
    const { novelId, num } = req.params as { novelId: string; num: string };
    const service = new ChapterService(getDb());
    const ch = service.getByNum(novelId, parseInt(num));
    if (!ch) return reply.status(404).send({ error: 'Chapter not found' });
    return ch;
  });

  app.post('/api/novels/:novelId/chapters', async (req, reply) => {
    const { novelId } = req.params as { novelId: string };
    const body = req.body as { chapterNum: number; title?: string; content?: string };
    const service = new ChapterService(getDb());
    const ch = service.create({ novelId, ...body });
    return reply.status(201).send(ch);
  });

  app.patch('/api/novels/:novelId/chapters/:num', async (req) => {
    const { novelId, num } = req.params as { novelId: string; num: string };
    const service = new ChapterService(getDb());
    return service.update(novelId, parseInt(num), req.body as any);
  });

  app.delete('/api/novels/:novelId/chapters/:num', async (req) => {
    const { novelId, num } = req.params as { novelId: string; num: string };
    const service = new ChapterService(getDb());
    service.softDelete(novelId, parseInt(num));
    return { ok: true };
  });

  app.get('/api/novels/:novelId/chapters/:num/versions', async (req) => {
    const { novelId, num } = req.params as { novelId: string; num: string };
    const service = new ChapterService(getDb());
    return service.getVersions(novelId, parseInt(num));
  });
}
