import type { FastifyInstance } from 'fastify';
import { getDb, RhythmService } from '@moyun/core';

export function registerRhythmRoutes(app: FastifyInstance) {
  app.get('/api/novels/:novelId/rhythm', async (req) => {
    const { novelId } = req.params as { novelId: string };
    return new RhythmService(getDb()).getStatus(novelId);
  });

  app.get('/api/novels/:novelId/rhythm/recommend', async (req) => {
    const { novelId } = req.params as { novelId: string };
    return { recommended: new RhythmService(getDb()).recommendNext(novelId) };
  });
}
