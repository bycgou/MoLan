import type { Db } from '../../db/index.js';
import { ChapterService } from '../../services/chapter.service.js';
import type { NovelGraphNode, NovelAgentState } from '../../types/graph.js';

export class PostProcessNode implements NovelGraphNode {
  nodeName = 'post_process';
  private chapterService: ChapterService;

  constructor(db: Db) {
    this.chapterService = new ChapterService(db);
  }

  shouldSkip(state: NovelAgentState): boolean {
    return !!state.finalResult?.chapterId;
  }

  async apply(state: NovelAgentState): Promise<NovelAgentState> {
    const content = state.finalResult?.finalContent ?? state.draftContent?.content;
    if (!content) {
      return {
        ...state,
        currentPhase: 'POST_PROCESS',
        errors: [...state.errors, 'post_process: 无内容可保存'],
      };
    }

    try {
      const chapter = this.chapterService.upsert({
        novelId: state.novelId,
        chapterNum: state.chapterNum,
        title: state.chapterPlan?.title,
        content,
      });

      return {
        ...state,
        finalResult: {
          finalContent: content,
          wordCount: chapter.wordCount,
          qualityScore: state.finalResult?.qualityScore ?? state.qualityScore ?? 0,
          chapterId: chapter.id,
          summary: state.finalResult?.summary,
        },
        currentPhase: 'POST_PROCESS',
      };
    } catch (error) {
      return {
        ...state,
        currentPhase: 'POST_PROCESS',
        errors: [...state.errors, `post_process失败: ${error instanceof Error ? error.message : '未知错误'}`],
      };
    }
  }
}
