import { eq, and } from 'drizzle-orm';
import { chapters, arcs } from '../db/schema.js';
import type { Db } from '../db/index.js';
import type { StrandType, RhythmStatus } from '../types/rhythm.js';

export class RhythmService {
  constructor(private db: Db) {}

  getStatus(novelId: string): RhythmStatus {
    const rows = this.db
      .select({ chapterNum: chapters.chapterNum, strandType: chapters.strandType })
      .from(chapters)
      .where(and(eq(chapters.novelId, novelId), eq(chapters.isDeleted, false)))
      .orderBy(chapters.chapterNum)
      .all();

    if (rows.length === 0) {
      return { lastStrand: null, consecutive: 0, gap: 0, ratios: { quest: 0, fire: 0, constellation: 0 } };
    }

    const counts = { quest: 0, fire: 0, constellation: 0 };
    let lastStrand: StrandType | null = null;
    let consecutive = 0;

    for (const row of rows) {
      const st = row.strandType as StrandType | null;
      if (st && st in counts) {
        counts[st]++;
        if (st === lastStrand) {
          consecutive++;
        } else {
          lastStrand = st;
          consecutive = 1;
        }
      }
    }

    const total = counts.quest + counts.fire + counts.constellation;
    return {
      lastStrand,
      consecutive,
      gap: 0,
      ratios: {
        quest: total > 0 ? counts.quest / total : 0,
        fire: total > 0 ? counts.fire / total : 0,
        constellation: total > 0 ? counts.constellation / total : 0,
      },
    };
  }

  recommendNext(novelId: string): StrandType {
    const status = this.getStatus(novelId);
    if (!status.lastStrand) return 'quest';

    // If consecutive same type >= 2, switch
    if (status.consecutive >= 2) {
      return this.getAlternative(status.lastStrand);
    }

    // Recommend based on ratio balance
    const { ratios } = status;
    if (ratios.quest < ratios.fire && ratios.quest < ratios.constellation) return 'quest';
    if (ratios.fire < ratios.quest && ratios.fire < ratios.constellation) return 'fire';
    if (ratios.constellation < ratios.quest && ratios.constellation < ratios.fire) return 'constellation';

    // Default: alternate
    return this.getAlternative(status.lastStrand);
  }

  checkArcRhythm(novelId: string, arcIndex: number): { ok: boolean; issues: string[] } {
    const arcRows = this.db.select().from(arcs)
      .where(and(eq(arcs.novelId, novelId), eq(arcs.arcIndex, arcIndex)))
      .all();
    if (arcRows.length === 0) return { ok: true, issues: [] };

    const status = this.getStatus(novelId);
    const issues: string[] = [];

    if (status.consecutive >= 3) {
      issues.push(`连续${status.consecutive}章相同strand类型 (${status.lastStrand})`);
    }

    return { ok: issues.length === 0, issues };
  }

  private getAlternative(current: StrandType): StrandType {
    if (current === 'quest') return 'fire';
    if (current === 'fire') return 'constellation';
    return 'quest';
  }
}
