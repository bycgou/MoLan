import type { FastifyInstance } from 'fastify';
import { getDb, CharacterService } from '@moyun/core';

export function registerCharacterRoutes(app: FastifyInstance) {
  app.get('/api/novels/:novelId/characters', async (req) => {
    const { novelId } = req.params as { novelId: string };
    return new CharacterService(getDb()).list(novelId);
  });

  app.get('/api/characters/:id', async (req, reply) => {
    const { id } = req.params as { id: string };
    const ch = new CharacterService(getDb()).getById(id);
    if (!ch) return reply.status(404).send({ error: 'Character not found' });
    return ch;
  });

  app.post('/api/novels/:novelId/characters', async (req, reply) => {
    const { novelId } = req.params as { novelId: string };
    const ch = new CharacterService(getDb()).create({ novelId, ...(req.body as any) });
    return reply.status(201).send(ch);
  });

  app.patch('/api/characters/:id', async (req) => {
    const { id } = req.params as { id: string };
    return new CharacterService(getDb()).update(id, req.body as any);
  });

  app.delete('/api/characters/:id', async (req) => {
    const { id } = req.params as { id: string };
    new CharacterService(getDb()).delete(id);
    return { ok: true };
  });

  app.post('/api/characters/:id/growth', async (req, reply) => {
    const { id } = req.params as { id: string };
    const node = new CharacterService(getDb()).addGrowthNode(id, req.body as any);
    return reply.status(201).send(node);
  });

  app.delete('/api/growth/:nodeId', async (req) => {
    const { nodeId } = req.params as { nodeId: string };
    new CharacterService(getDb()).removeGrowthNode(nodeId);
    return { ok: true };
  });
}
