import { eq } from 'drizzle-orm';
import type { Db } from '../../db/index.js';
import { arcs } from '../../db/schema.js';
import type { NovelGraphNode, NovelAgentState } from '../../types/graph.js';

export class ArcCheckNode implements NovelGraphNode {
  nodeName = 'arc_check';

  constructor(private db: Db) {}

  shouldSkip(state: NovelAgentState): boolean {
    return !!state.outlineContext;
  }

  async apply(state: NovelAgentState): Promise<NovelAgentState> {
    try {
      const rows = this.db.select().from(arcs)
        .where(eq(arcs.novelId, state.novelId))
        .all();

      if (rows.length === 0) {
        return { ...state, outlineContext: '', currentPhase: 'ARC_CHECK' };
      }

      const context = rows
        .sort((a, b) => a.arcIndex - b.arcIndex)
        .map(a => {
          const parts = [`卷${a.arcIndex}: ${a.name}`];
          if (a.outline) parts.push(a.outline);
          if (a.strandType) parts.push(`线索类型: ${a.strandType}`);
          return parts.join('\n');
        })
        .join('\n\n');

      return { ...state, outlineContext: context, currentPhase: 'ARC_CHECK' };
    } catch (error) {
      return {
        ...state,
        outlineContext: '',
        currentPhase: 'ARC_CHECK',
        errors: [...state.errors, `arc_check失败: ${error instanceof Error ? error.message : '未知错误'}`],
      };
    }
  }
}
