/**
 * AI套话黑名单 - 从 AntiAICliches.java 移植
 * 用于生成时约束 + 生成后检测
 */

/** 高频AI套话（18个） */
export const CLICHES: readonly string[] = [
  '不禁', '似乎', '仿佛', '眼神中闪过', '嘴角微微上扬',
  '心中涌起一股', '一种...的感觉', '空气中弥漫着', '不约而同',
  '若有所思', '意味深长', '难以名状', '若隐若现', '一股暖流',
  '缓缓说道', '淡淡地说', '微微点头', '轻声说道',
];

/** 过渡句万能胶黑名单（15个） */
export const TRANSITION_CLICHES: readonly string[] = [
  '时间飞逝', '转眼间', '转眼X天过去了', '时光荏苒',
  '与此同时', '另一边', '且不说', '单说',
  '就在XXX即将XXX的时候', '就在这个时候',
  '话音刚落', '沉默片刻', '微微一怔',
  '且听下回分解', '欲知后事如何',
];

/** 升级/收获描写流水账黑名单（11个） */
export const UPGRADE_CLICHES: readonly string[] = [
  '盘膝而坐', '运转功法', '灵力奔涌', '气息爆发',
  '睁开双眼', '嘴角笑意', '实力大增', '突破成功',
  '丹田中', '经脉中', '气海中',
];

/** 生成套话的Prompt注入文本 */
export function toPromptText(): string {
  return `禁止使用以下高频AI套话（全文出现即失败）：\n${CLICHES.map(c => `"${c}"`).join(' ')}`;
}

/** 生成过渡句的Prompt注入文本 */
export function toTransitionPromptText(): string {
  return `禁止使用以下万能过渡句（直接切场景，不需要过渡）：\n${TRANSITION_CLICHES.map(c => `"${c}"`).join(' ')}`;
}

/** 生成升级流水账的Prompt注入文本 */
export function toUpgradePromptText(): string {
  return `禁止使用以下升级流水账描写（用细节和情感替代）：\n${UPGRADE_CLICHES.map(c => `"${c}"`).join(' ')}`;
}

/** 检测文本中的套话 */
export function findCliches(text: string): string[] {
  if (!text) return [];
  return CLICHES.filter(c => text.includes(c));
}

/** 检测文本中的过渡句 */
export function findTransitionCliches(text: string): string[] {
  if (!text) return [];
  return TRANSITION_CLICHES.filter(c => text.includes(c));
}

/** 检测文本中的升级流水账 */
export function findUpgradeCliches(text: string): string[] {
  if (!text) return [];
  return UPGRADE_CLICHES.filter(c => text.includes(c));
}

/** 扫描文本，返回所有检测到的AI套话（三类合并） */
export function scanAllCliches(text: string): { cliches: string[]; transitions: string[]; upgrades: string[] } {
  return {
    cliches: findCliches(text),
    transitions: findTransitionCliches(text),
    upgrades: findUpgradeCliches(text),
  };
}
