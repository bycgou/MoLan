import type { FastifyInstance } from 'fastify';
import { HookAnalysisService } from '@moyun/core';

export function registerHookRoutes(app: FastifyInstance) {
  app.post('/api/hook/analyze', async (req) => {
    const { content } = req.body as { content: string };
    return new HookAnalysisService().analyze(content);
  });
}
