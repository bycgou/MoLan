import type { FastifyInstance } from 'fastify';
import { getDb, NovelService } from '@moyun/core';

export function registerNovelRoutes(app: FastifyInstance) {
  app.get('/api/novels', async () => {
    const service = new NovelService(getDb());
    return service.list();
  });

  app.get('/api/novels/:id', async (req, reply) => {
    const { id } = req.params as { id: string };
    const service = new NovelService(getDb());
    const novel = service.getById(id);
    if (!novel) return reply.status(404).send({ error: 'Novel not found' });
    return novel;
  });

  app.post('/api/novels', async (req, reply) => {
    const body = req.body as { title: string; genre: string; totalOutline?: string; worldSetting?: string };
    const service = new NovelService(getDb());
    const novel = service.create(body);
    return reply.status(201).send(novel);
  });

  app.patch('/api/novels/:id', async (req) => {
    const { id } = req.params as { id: string };
    const service = new NovelService(getDb());
    return service.update(id, req.body as any);
  });

  app.delete('/api/novels/:id', async (req) => {
    const { id } = req.params as { id: string };
    const service = new NovelService(getDb());
    service.delete(id);
    return { ok: true };
  });

  app.get('/api/novels/:id/word-count', async (req) => {
    const { id } = req.params as { id: string };
    const service = new NovelService(getDb());
    return { wordCount: service.getWordCount(id) };
  });

  app.get('/api/novels/:id/export', async (req, reply) => {
    const { id } = req.params as { id: string };
    const service = new NovelService(getDb());
    try {
      const txt = service.exportTxt(id);
      const novel = service.getById(id);
      const filename = novel ? `${novel.title}.txt` : 'novel.txt';
      return reply
        .header('Content-Type', 'text/plain; charset=utf-8')
        .header('Content-Disposition', `attachment; filename="${encodeURIComponent(filename)}"`)
        .send(txt);
    } catch (e: any) {
      return reply.status(404).send({ error: e.message });
    }
  });
}
