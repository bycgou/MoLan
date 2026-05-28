import type { ChatOpenAI } from '@langchain/openai';
import { HumanMessage, SystemMessage } from '@langchain/core/messages';

export interface VolumeOutlineResult {
  arcName: string;
  arcOutline: string;
  startChapter: number;
  endChapter: number;
  chapterCount: number;
  rhythmTag: string;
  rhythmIntensity: number;
}

const VOLUME_OUTLINE_SYSTEM_PROMPT = `你是一位专业的网络小说主编，擅长将小说总纲拆分为合理的卷结构。

【输出铁律（违反任何一条视为无效输出）】
1. 只输出纯JSON数组，不得有任何前缀、后缀、说明文字或markdown代码块
2. 每个卷必须有明确的主题和剧情弧线，卷名应体现本卷核心内容
3. 每卷章节数建议在10-30章之间，根据剧情需要灵活调整
4. 卷纲至少200字，包含：本卷核心冲突、关键转折点、高潮场景、结尾钩子
5. rhythmTag必须为以下之一：quest（主线推进）、fire（感情/冲突高潮）、constellation（世界观展开/支线）
6. rhythmIntensity范围1-10，表示该卷的剧情张力强度
7. 所有卷的章节号必须连续，从第1章开始
8. 卷纲之间必须有剧情递进关系，后卷在前卷基础上发展

【输出格式】
[{"arcName":"卷名","arcOutline":"卷纲详细内容（至少200字）","startChapter":1,"endChapter":15,"chapterCount":15,"rhythmTag":"quest","rhythmIntensity":5}, ...]`;

export class VolumeOutlineChain {
  constructor(private llm: ChatOpenAI) {}

  async execute(params: {
    totalOutline?: string;
    genre?: string;
    worldSetting?: string;
    novelType?: string;
    tone?: string;
    tabooList?: string[];
  }): Promise<VolumeOutlineResult[]> {
    const parts: string[] = [];
    if (params.genre) parts.push(`【类型】${params.genre}`);
    if (params.novelType) parts.push(`【小说类型】${params.novelType}`);
    if (params.tone) parts.push(`【基调】${params.tone}`);
    if (params.totalOutline) parts.push(`【总大纲】\n${params.totalOutline}`);
    if (params.worldSetting) parts.push(`【世界观设定】\n${params.worldSetting}`);
    if (params.tabooList && params.tabooList.length > 0) {
      parts.push(`【禁忌事项】\n${params.tabooList.join('；')}`);
    }

    const userPrompt = `请根据以下小说设定，生成合理的卷结构：\n\n${parts.join('\n\n')}`;

    const response = await this.llm.invoke([
      new SystemMessage(VOLUME_OUTLINE_SYSTEM_PROMPT),
      new HumanMessage(userPrompt),
    ]);

    const text = typeof response.content === 'string'
      ? response.content
      : JSON.stringify(response.content);

    return this.parseVolumeOutlines(text);
  }

  private parseVolumeOutlines(text: string): VolumeOutlineResult[] {
    const jsonMatch = text.match(/\[[\s\S]*\]/);
    if (!jsonMatch) {
      return [];
    }

    try {
      const parsed = JSON.parse(jsonMatch[0]);
      if (!Array.isArray(parsed)) return [];

      return parsed.map((item: any) => ({
        arcName: item.arcName ?? '未命名卷',
        arcOutline: item.arcOutline ?? '',
        startChapter: item.startChapter ?? 1,
        endChapter: item.endChapter ?? 1,
        chapterCount: item.chapterCount ?? 1,
        rhythmTag: item.rhythmTag ?? 'quest',
        rhythmIntensity: item.rhythmIntensity ?? 5,
      }));
    } catch {
      return [];
    }
  }
}
