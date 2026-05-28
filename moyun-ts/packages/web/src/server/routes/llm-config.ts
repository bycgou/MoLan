import type { FastifyInstance } from 'fastify';
import { getDb, LlmConfigService } from '@moyun/core';

export function registerLlmConfigRoutes(app: FastifyInstance) {
  app.get('/api/llm-configs', async () => new LlmConfigService(getDb()).list());

  app.get('/api/llm-configs/:id', async (req, reply) => {
    const config = new LlmConfigService(getDb()).getById((req.params as any).id);
    if (!config) return reply.status(404).send({ error: 'Config not found' });
    return config;
  });

  app.post('/api/llm-configs', async (req, reply) => {
    const config = new LlmConfigService(getDb()).create(req.body as any);
    return reply.status(201).send(config);
  });

  app.patch('/api/llm-configs/:id', async (req) => {
    return new LlmConfigService(getDb()).update((req.params as any).id, req.body as any);
  });

  app.delete('/api/llm-configs/:id', async (req) => {
    new LlmConfigService(getDb()).delete((req.params as any).id);
    return { ok: true };
  });
}
