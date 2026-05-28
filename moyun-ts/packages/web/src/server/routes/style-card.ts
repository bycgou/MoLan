import type { FastifyInstance } from 'fastify';
import { getDb, StyleCardService } from '@moyun/core';

export function registerStyleCardRoutes(app: FastifyInstance) {
  app.get('/api/style-cards', async () => new StyleCardService(getDb()).list());

  app.get('/api/style-cards/:id', async (req, reply) => {
    const card = new StyleCardService(getDb()).getById((req.params as any).id);
    if (!card) return reply.status(404).send({ error: 'Style card not found' });
    return card;
  });

  app.post('/api/style-cards', async (req, reply) => {
    const card = new StyleCardService(getDb()).create(req.body as any);
    return reply.status(201).send(card);
  });

  app.patch('/api/style-cards/:id', async (req) => {
    return new StyleCardService(getDb()).update((req.params as any).id, req.body as any);
  });

  app.delete('/api/style-cards/:id', async (req) => {
    new StyleCardService(getDb()).delete((req.params as any).id);
    return { ok: true };
  });

  app.post('/api/novels/:novelId/style-cards/:cardId', async (req) => {
    const { novelId, cardId } = req.params as { novelId: string; cardId: string };
    new StyleCardService(getDb()).bindToNovel(novelId, cardId);
    return { ok: true };
  });

  app.delete('/api/novels/:novelId/style-cards/:cardId', async (req) => {
    const { novelId, cardId } = req.params as { novelId: string; cardId: string };
    new StyleCardService(getDb()).unbindFromNovel(novelId, cardId);
    return { ok: true };
  });

  app.get('/api/novels/:novelId/style-cards', async (req) => {
    const { novelId } = req.params as { novelId: string };
    return new StyleCardService(getDb()).getBoundCards(novelId);
  });
}
