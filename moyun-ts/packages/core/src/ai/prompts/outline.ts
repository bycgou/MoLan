/**
 * 大纲生成Prompt - 从 OutlineAgent.java 移植
 */
export const OUTLINE_SYSTEM_PROMPT = `你是一位专业的网络小说作家，擅长创作情节紧凑、引人入胜的小说大纲。

【叙事结构多样性——防止情节模板化】
1. 禁止连续3章使用相同叙事视角（主角视角 → 配角视角 → 反派视角 → 旁观者视角 交替）
2. 每卷至少2章使用非线性叙事（倒叙、插叙、多线并行）
3. 战斗结局不能全是"主角险胜"，必须多样化：
   平局 / 惨胜 / 被迫撤退 / 赢了但失去重要东西 / 敌人主动退走
4. 每5章至少1章的核心冲突不是外部战斗（内心挣扎/人际矛盾/道德困境/信息不对称的误解）
5. 偶尔打破"每章都有钩子"的规则（有些章节平淡收尾，制造节奏变化）

【输出铁律（违反任何一条视为无效输出）】
1. 只输出纯JSON，不得有任何前缀、后缀、说明文字或markdown代码块
2. 所有字段值中不得出现未转义的双引号，如需引用书名请用【】代替引号
3. summary控制在80字以内，key_points每条15字以内，hook 20字以内
4. 每章情节必须推进故事，严禁重复【全书已发生关键事件】中的任何事件
5. 所有人物的职务、身份、处境必须与【人物当前状态】完全一致
6. 如有【待回收伏笔】，应在合适章节安排自然回收，不得无限期悬置
7. 如有【上一章结尾钩子】，本章summary开头必须直接承接该悬念
8. 每章hook必须为下一章埋下具体可衔接的悬念，不得使用空洞表述
9. 如有【全书故事线回顾】，本章情节必须在其基础上自然演进

【输出格式】
{"chapter_number":整数,"title":"标题","summary":"概要","key_points":["点1","点2"],"hook":"钩子"}`;

export interface OutlineResult {
  chapter_number: number;
  title: string;
  summary: string;
  key_points: string[];
  hook: string;
}

export function buildOutlineUserPrompt(params: {
  novelId: string;
  chapterNum: number;
  totalOutline?: string;
  worldSetting?: string;
  recentChapters?: string;
  lastHook?: string;
  characterStates?: string;
  clueBook?: string;
  storyLineReview?: string;
}): string {
  const parts: string[] = [];
  parts.push(`小说ID: ${params.novelId}`);
  parts.push(`章节号: 第${params.chapterNum}章`);

  if (params.totalOutline) parts.push(`\n【总纲】\n${params.totalOutline}`);
  if (params.worldSetting) parts.push(`\n【世界观设定】\n${params.worldSetting}`);
  if (params.characterStates) parts.push(`\n【人物当前状态】\n${params.characterStates}`);
  if (params.clueBook) parts.push(`\n【待回收伏笔】\n${params.clueBook}`);
  if (params.lastHook) parts.push(`\n【上一章结尾钩子】\n${params.lastHook}`);
  if (params.recentChapters) parts.push(`\n【近期章节大纲】\n${params.recentChapters}`);
  if (params.storyLineReview) parts.push(`\n【全书故事线回顾】\n${params.storyLineReview}`);

  return parts.join('\n');
}
