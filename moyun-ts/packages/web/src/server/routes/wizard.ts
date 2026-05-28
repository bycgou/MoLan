import type { FastifyInstance } from 'fastify';
import { getDb, WizardService } from '@moyun/core';

export function registerWizardRoutes(app: FastifyInstance) {
  // POST /api/wizard/volume-outlines - generate arc outlines
  app.post('/api/wizard/volume-outlines', async (req, reply) => {
    const body = req.body as {
      novelName: string;
      genre: string;
      totalOutline?: string;
      worldSetting?: string;
      novelType?: string;
      tone?: string;
      tabooList?: string[];
    };

    const db = getDb();
    const service = new WizardService(db);

    try {
      const outlines = await service.generateVolumeOutlines(body);
      return { outlines };
    } catch (err: any) {
      return reply.status(500).send({ error: err.message });
    }
  });

  // POST /api/wizard/save - save novel with arcs
  app.post('/api/wizard/save', async (req, reply) => {
    const body = req.body as {
      settings: {
        novelName: string;
        genre: string;
        totalOutline?: string;
        worldSetting?: string;
        novelType?: string;
        tone?: string;
        tabooList?: string[];
        styleCardId?: string;
      };
      arcOutlines: Array<{
        arcName: string;
        arcOutline: string;
        startChapter: number;
        endChapter: number;
        chapterCount: number;
        rhythmTag: string;
        rhythmIntensity: number;
      }>;
    };

    const db = getDb();
    const service = new WizardService(db);

    try {
      const novelId = service.saveNovelWithArcs(body.settings, body.arcOutlines);
      return reply.status(201).send({ novelId });
    } catch (err: any) {
      return reply.status(500).send({ error: err.message });
    }
  });

  // POST /api/wizard/novels/:novelId/arcs/:arcIndex/chapter-outlines - generate chapter outlines
  app.post('/api/wizard/novels/:novelId/arcs/:arcIndex/chapter-outlines', async (req, reply) => {
    const { novelId, arcIndex } = req.params as { novelId: string; arcIndex: string };

    const db = getDb();
    const service = new WizardService(db);

    try {
      const outlines = await service.generateArcChapterOutlines(novelId, parseInt(arcIndex, 10));
      return { outlines };
    } catch (err: any) {
      return reply.status(500).send({ error: err.message });
    }
  });

  // POST /api/wizard/novels/:novelId/batch-generate - start batch generation
  app.post('/api/wizard/novels/:novelId/batch-generate', async (req, reply) => {
    const { novelId } = req.params as { novelId: string };

    const db = getDb();
    const service = new WizardService(db);

    try {
      const taskId = await service.startBatchGeneration(novelId);
      return reply.status(202).send({ taskId });
    } catch (err: any) {
      return reply.status(500).send({ error: err.message });
    }
  });

  // GET /api/wizard/novels/:novelId/batch-status/:taskId - poll progress
  app.get('/api/wizard/novels/:novelId/batch-status/:taskId', async (req, reply) => {
    const { taskId } = req.params as { novelId: string; taskId: string };

    const db = getDb();
    const service = new WizardService(db);

    const progress = service.getBatchProgress(taskId);
    if (!progress) {
      return reply.status(404).send({ error: 'Task not found' });
    }
    return progress;
  });

  // POST /api/wizard/novels/:novelId/batch-cancel/:taskId - cancel batch
  app.post('/api/wizard/novels/:novelId/batch-cancel/:taskId', async (req, reply) => {
    const { taskId } = req.params as { novelId: string; taskId: string };

    const db = getDb();
    const service = new WizardService(db);

    service.cancelBatchGeneration(taskId);
    return { ok: true };
  });
}
