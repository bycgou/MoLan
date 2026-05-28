import Fastify from 'fastify';
import cors from '@fastify/cors';
import { registerNovelRoutes } from './routes/novel.js';
import { registerChapterRoutes } from './routes/chapter.js';
import { registerCharacterRoutes } from './routes/character.js';
import { registerClueRoutes } from './routes/clue.js';
import { registerRhythmRoutes } from './routes/rhythm.js';
import { registerHookRoutes } from './routes/hook.js';
import { registerLlmConfigRoutes } from './routes/llm-config.js';
import { registerStyleCardRoutes } from './routes/style-card.js';
import { registerDashboardRoutes } from './routes/dashboard.js';
import { registerWizardRoutes } from './routes/wizard.js';
import { registerStreamingRoutes } from './routes/streaming.js';

const PORT = parseInt(process.env.PORT ?? '3456');

async function main() {
  const app = Fastify({ logger: true });
  await app.register(cors, { origin: true });

  registerNovelRoutes(app);
  registerChapterRoutes(app);
  registerCharacterRoutes(app);
  registerClueRoutes(app);
  registerRhythmRoutes(app);
  registerHookRoutes(app);
  registerLlmConfigRoutes(app);
  registerStyleCardRoutes(app);
  registerDashboardRoutes(app);
  registerWizardRoutes(app);
  registerStreamingRoutes(app);

  // Health check
  app.get('/api/health', async () => ({ status: 'ok', version: '0.1.0' }));

  await app.listen({ port: PORT, host: '0.0.0.0' });
  console.log(`MoYun API server running at http://localhost:${PORT}`);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
