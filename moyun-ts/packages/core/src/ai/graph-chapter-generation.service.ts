import type { Db } from '../db/index.js';
import { getLLMByUsage } from './llm-factory.js';
import { GraphRunner } from './graph/graph-runner.js';
import type { NovelAgentState, AgentExecutionResult } from '../types/graph.js';
import { ArcCheckNode } from './nodes/arc-check.node.js';
import { OutlineNode } from './nodes/outline.node.js';
import { CluePlanningNode } from './nodes/clue-planning.node.js';
import { CharacterPlanningNode } from './nodes/character-planning.node.js';
import { WorldValidatorNode } from './nodes/world-validator.node.js';
import { IdeaSelectionNode } from './nodes/idea-selection.node.js';
import { WritingNode } from './nodes/writing.node.js';
import { SelfReviewNode } from './nodes/self-review.node.js';
import { ValidationNode } from './nodes/validation.node.js';
import { QualityScoringNode } from './nodes/quality-scoring.node.js';
import { AuditRouterNode } from './nodes/audit-router.node.js';
import { SummaryNode } from './nodes/summary.node.js';
import { PostProcessNode } from './nodes/post-process.node.js';

export class GraphChapterGenerationService {
  private runner: GraphRunner;

  constructor(private db: Db) {
    this.runner = new GraphRunner();
    const writingLLM = getLLMByUsage(db, 'writing');
    const outlineLLM = getLLMByUsage(db, 'outline');

    this.runner.register(new ArcCheckNode(db));
    this.runner.register(new OutlineNode(outlineLLM));
    this.runner.register(new CluePlanningNode(db));
    this.runner.register(new CharacterPlanningNode(db));
    this.runner.register(new WorldValidatorNode(writingLLM));
    this.runner.register(new IdeaSelectionNode());
    this.runner.register(new WritingNode(writingLLM));
    this.runner.register(new SelfReviewNode(writingLLM));
    this.runner.register(new ValidationNode(writingLLM));
    this.runner.register(new QualityScoringNode());
    this.runner.register(new AuditRouterNode());
    this.runner.register(new SummaryNode(writingLLM));
    this.runner.register(new PostProcessNode(db));
  }

  async generate(novelId: string, chapterNum: number): Promise<AgentExecutionResult> {
    const startTime = Date.now();

    const initialState: NovelAgentState = {
      novelId,
      chapterNum,
      iterationCount: 0,
      planRetryCount: 0,
      writeRetryCount: 0,
      rewriteCount: 0,
      currentPhase: 'INIT',
      executionTrace: [],
      errors: [],
    };

    const finalState = await this.runner.execute(initialState);

    return {
      content: finalState.finalResult?.finalContent ?? finalState.draftContent?.content ?? '',
      wordCount: finalState.finalResult?.wordCount ?? 0,
      qualityScore: finalState.finalResult?.qualityScore ?? finalState.qualityScore ?? 0,
      chapterId: finalState.finalResult?.chapterId,
      executionTrace: finalState.executionTrace,
      errors: finalState.errors,
      elapsedMs: Date.now() - startTime,
    };
  }
}
