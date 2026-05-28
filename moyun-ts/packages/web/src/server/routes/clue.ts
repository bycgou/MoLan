import type { FastifyInstance } from 'fastify';
import { getDb, ClueService } from '@moyun/core';

export function registerClueRoutes(app: FastifyInstance) {
  app.get('/api/novels/:novelId/clues', async (req) => {
    const { novelId } = req.params as { novelId: string };
    const query = req.query as { active?: string };
    const service = new ClueService(getDb());
    return query.active === 'true' ? service.listActive(novelId) : service.list(novelId);
  });

  app.post('/api/novels/:novelId/clues', async (req, reply) => {
    const { novelId } = req.params as { novelId: string };
    const clue = new ClueService(getDb()).create({ novelId, ...(req.body as any) });
    return reply.status(201).send(clue);
  });

  app.post('/api/clues/:id/recycle', async (req) => {
    const { id } = req.params as { id: string };
    const { recycleChapter } = req.body as { recycleChapter: number };
    return new ClueService(getDb()).recycle(id, recycleChapter);
  });
}
