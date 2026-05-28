/**
 * 摘要生成Prompt
 */

export const SUMMARY_SYSTEM_PROMPT = `你是一位小说摘要专家。从章节内容中提取结构化摘要信息。

提取内容：
1. fingerprint - 章节指纹（一句话概括核心事件，20字以内）
2. keywords - 关键词列表（3-5个）
3. characters - 出场角色列表
4. locations - 出现场景/地点
5. relations - 本章发生的关系变化

输出纯JSON：
{"fingerprint":"一句话","keywords":["词1","词2"],"characters":["角色1"],"locations":["地点1"],"relations":["关系变化1"]}`;

export function buildSummaryUserPrompt(params: {
  chapterNum: number;
  content: string;
}): string {
  return `章节号: 第${params.chapterNum}章\n\n【章节内容】\n${params.content}`;
}
