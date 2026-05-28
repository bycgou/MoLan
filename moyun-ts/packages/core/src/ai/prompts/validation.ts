/**
 * 五维验证Prompt模板
 */

export const OOC_VALIDATION_PROMPT = `你是一位严格的角色一致性审查员。检查以下章节内容是否存在角色OOC（Out of Character）问题。

评分维度（每项0-100分）：
1. languageStyle - 语言风格是否符合角色设定
2. behaviorMotivation - 行为动机是否合理
3. knowledgeLevel - 知识水平是否一致（角色不应知道超出其认知的信息）
4. growthAdaptation - 成长适应性（角色行为是否反映已发生的成长）

输出纯JSON：
{"languageStyle":分数,"behaviorMotivation":分数,"knowledgeLevel":分数,"growthAdaptation":分数,"totalScore":平均分,"passed":总分>=70,"issues":["问题1"]}`;

export const LOGIC_VALIDATION_PROMPT = `你是一位严格的逻辑审查员。检查以下章节内容的逻辑一致性。

检查项：
1. 因果链是否清晰
2. 时间线是否矛盾
3. 空间位置是否合理
4. 是否有情节冲突

输出纯JSON：
{"passed":true/false,"score":0-100,"issues":["问题1"],"suggestions":["建议1"]}`;

export const CLUE_VALIDATION_PROMPT = `你是一位伏笔审查员。检查以下章节对伏笔/线索的处理是否合理。

检查项：
1. 标记应回收的伏笔是否已回收
2. 新埋设的伏笔是否自然
3. 已回收伏笔是否合理闭合

输出纯JSON：
{"passed":true/false,"score":0-100,"planted":["新埋设"],"advanced":["推进"],"resolved":["已回收"],"issues":["问题1"]}`;

export const SETTING_VALIDATION_PROMPT = `你是一位世界观设定审查员。检查以下章节是否违反世界观设定。

检查项：
1. 力量体系是否一致
2. 地理/历史是否矛盾
3. 特殊规则是否被违反

输出纯JSON：
{"passed":true/false,"score":0-100,"violations":["违规1"],"warnings":["警告1"]}`;

export const RHYTHM_VALIDATION_PROMPT = `你是一位节奏审查员。检查以下章节的节奏是否合理。

检查项：
1. 期望节奏与实际节奏是否匹配
2. 紧张/舒缓交替是否合理
3. 信息密度是否适中

输出纯JSON：
{"passed":true/false,"score":0-100,"expectedStrand":"quest/fire/constellation","actualStrand":"quest/fire/constellation","issues":["问题1"]}`;

export function buildValidationUserPrompt(params: {
  chapterNum: number;
  content: string;
  outline?: string;
  characterStates?: string;
  clueBook?: string;
  worldSetting?: string;
  expectedStrand?: string;
}): string {
  const parts: string[] = [];
  parts.push(`章节号: 第${params.chapterNum}章`);
  parts.push(`\n【章节内容】\n${params.content}`);
  if (params.outline) parts.push(`\n【章纲】\n${params.outline}`);
  if (params.characterStates) parts.push(`\n【人物当前状态】\n${params.characterStates}`);
  if (params.clueBook) parts.push(`\n【伏笔清单】\n${params.clueBook}`);
  if (params.worldSetting) parts.push(`\n【世界观设定】\n${params.worldSetting}`);
  if (params.expectedStrand) parts.push(`\n【期望节奏】\n${params.expectedStrand}`);
  return parts.join('\n');
}
