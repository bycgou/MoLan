import { and, eq } from 'drizzle-orm';
import type { Db } from '../../db/index.js';
import { clues } from '../../db/schema.js';
import type { NovelGraphNode, NovelAgentState, ClueAction } from '../../types/graph.js';

export class CluePlanningNode implements NovelGraphNode {
  nodeName = 'clue_planning';

  constructor(private db: Db) {}

  shouldSkip(state: NovelAgentState): boolean {
    return !!(state.chapterPlan?.clueActions && state.chapterPlan.clueActions.length > 0);
  }

  async apply(state: NovelAgentState): Promise<NovelAgentState> {
    try {
      const activeClues = this.db.select().from(clues)
        .where(and(eq(clues.novelId, state.novelId), eq(clues.isRecycled, false)))
        .all();

      const clueActions: ClueAction[] = activeClues.map(c => ({
        clueId: c.id,
        clueTitle: c.title,
        action: this.decideAction(c, state.chapterNum),
        description: c.description ?? '',
      }));

      return {
        ...state,
        chapterPlan: state.chapterPlan
          ? { ...state.chapterPlan, clueActions }
          : undefined,
        currentPhase: 'CLUE_PLAN',
      };
    } catch (error) {
      return {
        ...state,
        currentPhase: 'CLUE_PLAN',
        errors: [...state.errors, `clue_planning失败: ${error instanceof Error ? error.message : '未知错误'}`],
      };
    }
  }

  private decideAction(clue: typeof clues.$inferSelect, chapterNum: number): 'PLANT' | 'RESOLVE' | 'ADVANCE' {
    if (clue.recycleChapter && clue.recycleChapter <= chapterNum) return 'RESOLVE';
    if (clue.buryChapter && clue.buryChapter >= chapterNum) return 'PLANT';
    return 'ADVANCE';
  }
}
