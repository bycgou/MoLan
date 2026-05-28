import type { Db } from '../db/index.js';
import { eq, and, desc, sql } from 'drizzle-orm';
import {
  chapters, chapterSummaries, characters, characterStates,
  clues, storyStates, eventNodes, arcs,
} from '../db/schema.js';

/**
 * 六层记忆架构上下文组装器
 *
 * L1: StoryState（故事状态，全量注入）
 * L2: ClueBook（线索，条件注入）
 * L3: CharacterState（角色状态，按需注入）
 * L4: 滑动窗口（弧摘要 + 近期章节大纲 + 上章Hook）
 * L5: 事件图 + 规则检索
 * L6: RAG向量检索（Agent Tool按需调用，此处留接口）
 */
export class ContextBuilder {
  constructor(private db: Db) {}

  build(novelId: string, chapterNum: number): {
    storyState: string;
    clueBook: string;
    characterStates: string;
    recentChapters: string;
    lastHook: string;
    eventGraph: string;
    storyLineReview: string;
  } {
    return {
      storyState: this.buildStoryState(novelId),
      clueBook: this.buildClueBook(novelId),
      characterStates: this.buildCharacterStates(novelId),
      recentChapters: this.buildRecentChapters(novelId, chapterNum),
      lastHook: this.buildLastHook(novelId, chapterNum),
      eventGraph: this.buildEventGraph(novelId),
      storyLineReview: this.buildStoryLineReview(novelId),
    };
  }

  /** L1: 故事状态 */
  private buildStoryState(novelId: string): string {
    const rows = this.db.select().from(storyStates)
      .where(eq(storyStates.novelId, novelId))
      .all();
    if (rows.length === 0) return '暂无故事状态';
    try {
      return JSON.stringify(JSON.parse(rows[0].state), null, 2);
    } catch {
      return rows[0].state;
    }
  }

  /** L2: 线索/伏笔清单 */
  private buildClueBook(novelId: string): string {
    const activeClues = this.db.select().from(clues)
      .where(and(eq(clues.novelId, novelId), eq(clues.isRecycled, false)))
      .all();

    if (activeClues.length === 0) return '暂无未回收伏笔';

    return activeClues.map(c => {
      const buried = c.buryChapter ? `（第${c.buryChapter}章埋设）` : '';
      return `- [${c.type}] ${c.title}${buried}: ${c.description ?? '无描述'}`;
    }).join('\n');
  }

  /** L3: 角色状态 */
  private buildCharacterStates(novelId: string): string {
    const chars = this.db.select().from(characters)
      .where(eq(characters.novelId, novelId))
      .all();

    if (chars.length === 0) return '暂无角色';

    return chars.map(c => {
      const parts = [`${c.name}（${c.role}）`];
      if (c.description) parts.push(`描述: ${c.description}`);
      if (c.personality) parts.push(`性格: ${c.personality}`);
      return parts.join(' | ');
    }).join('\n');
  }

  /** L4: 近期章节大纲（最近5章） */
  private buildRecentChapters(novelId: string, currentNum: number): string {
    const recent = this.db.select({
      chapterNum: chapters.chapterNum,
      title: chapters.title,
      outline: chapters.outline,
    })
      .from(chapters)
      .where(and(
        eq(chapters.novelId, novelId),
        eq(chapters.isDeleted, false),
        sql`${chapters.chapterNum} < ${currentNum}`,
      ))
      .orderBy(desc(chapters.chapterNum))
      .limit(5)
      .all();

    if (recent.length === 0) return '暂无前期章节';

    return recent.reverse().map(ch => {
      let outlineText = '无大纲';
      if (ch.outline) {
        try {
          const parsed = JSON.parse(ch.outline);
          outlineText = parsed.summary ?? ch.outline;
        } catch {
          outlineText = ch.outline;
        }
      }
      return `第${ch.chapterNum}章 ${ch.title ?? '无标题'}: ${outlineText}`;
    }).join('\n');
  }

  /** L4: 上一章Hook */
  private buildLastHook(novelId: string, currentNum: number): string {
    const prev = this.db.select({ outline: chapters.outline })
      .from(chapters)
      .where(and(
        eq(chapters.novelId, novelId),
        eq(chapters.chapterNum, currentNum - 1),
        eq(chapters.isDeleted, false),
      ))
      .all();

    if (prev.length === 0 || !prev[0].outline) return '无（第一章或无大纲）';

    try {
      const parsed = JSON.parse(prev[0].outline);
      return parsed.hook ?? '无Hook';
    } catch {
      return '无Hook';
    }
  }

  /** L5: 事件图（最近20个事件） */
  private buildEventGraph(novelId: string): string {
    const events = this.db.select()
      .from(eventNodes)
      .where(eq(eventNodes.novelId, novelId))
      .orderBy(desc(eventNodes.chapterNum))
      .limit(20)
      .all();

    if (events.length === 0) return '暂无事件记录';

    return events.reverse().map(e => {
      try {
        const data = JSON.parse(e.data);
        return `第${e.chapterNum}章: ${data.event ?? data.summary ?? e.data}`;
      } catch {
        return `第${e.chapterNum}章: ${e.data}`;
      }
    }).join('\n');
  }

  /** 全书故事线回顾 */
  private buildStoryLineReview(novelId: string): string {
    const summaries = this.db.select({
      chapterNum: chapters.chapterNum,
      fingerprint: chapterSummaries.fingerprint,
    })
      .from(chapterSummaries)
      .innerJoin(chapters, eq(chapterSummaries.chapterId, chapters.id))
      .where(and(eq(chapters.novelId, novelId), eq(chapters.isDeleted, false)))
      .orderBy(chapters.chapterNum)
      .all();

    if (summaries.length === 0) return '暂无故事线';

    return summaries
      .filter(s => s.fingerprint)
      .map(s => `第${s.chapterNum}章: ${s.fingerprint}`)
      .join('\n');
  }
}
