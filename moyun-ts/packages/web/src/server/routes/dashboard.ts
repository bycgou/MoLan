import type { FastifyInstance } from 'fastify';
import { getDb, NovelService, ChapterService, CharacterService, HookAnalysisService } from '@moyun/core';

export function registerDashboardRoutes(app: FastifyInstance) {
  app.get('/api/novels/:novelId/dashboard', async (req, reply) => {
    const { novelId } = req.params as { novelId: string };
    const db = getDb();
    const novelService = new NovelService(db);
    const chapterService = new ChapterService(db);
    const characterService = new CharacterService(db);

    const novel = novelService.getById(novelId);
    if (!novel) return reply.status(404).send({ error: 'Novel not found' });

    const chapters = chapterService.listFull(novelId);
    const characters = characterService.list(novelId);
    const wordCount = novelService.getWordCount(novelId);

    // Hook analysis for recent chapters (last 10)
    const hookService = new HookAnalysisService();
    const recentChapters = chapters.slice(-10);
    const hookScores = recentChapters.map(ch => {
      const analysis = ch.content ? hookService.analyze(ch.content) : null;
      return {
        chapterNum: ch.chapterNum,
        title: ch.title,
        wordCount: ch.wordCount,
        hook: analysis,
      };
    });

    // Average pull score
    const scoresWithHook = hookScores.filter(h => h.hook);
    const avgPullScore = scoresWithHook.length > 0
      ? scoresWithHook.reduce((sum, h) => sum + (h.hook?.totalScore ?? 0), 0) / scoresWithHook.length
      : 0;

    return {
      novel,
      stats: {
        totalChapters: chapters.length,
        totalWordCount: wordCount,
        characterCount: characters.length,
        avgPullScore: Math.round(avgPullScore * 10) / 10,
      },
      recentChapters: hookScores,
    };
  });
}
