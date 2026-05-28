import { eq } from 'drizzle-orm';
import type { Db } from '../../db/index.js';
import { characters } from '../../db/schema.js';
import type { NovelGraphNode, NovelAgentState, CharacterAction } from '../../types/graph.js';

export class CharacterPlanningNode implements NovelGraphNode {
  nodeName = 'character_planning';

  constructor(private db: Db) {}

  shouldSkip(state: NovelAgentState): boolean {
    return !!(state.chapterPlan?.characterActions && state.chapterPlan.characterActions.length > 0);
  }

  async apply(state: NovelAgentState): Promise<NovelAgentState> {
    try {
      const chars = this.db.select().from(characters)
        .where(eq(characters.novelId, state.novelId))
        .all();

      const characterActions: CharacterAction[] = chars.map(c => ({
        characterId: c.id,
        characterName: c.name,
        role: c.role,
        goal: '',
        emotionalState: '',
        keyAction: '',
      }));

      return {
        ...state,
        chapterPlan: state.chapterPlan
          ? { ...state.chapterPlan, characterActions }
          : undefined,
        currentPhase: 'CHAR_PLAN',
      };
    } catch (error) {
      return {
        ...state,
        currentPhase: 'CHAR_PLAN',
        errors: [...state.errors, `character_planning失败: ${error instanceof Error ? error.message : '未知错误'}`],
      };
    }
  }
}
