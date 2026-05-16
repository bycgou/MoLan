/**
 * 文风卡片相关类型定义
 */

/** 文风卡片 */
export interface StyleCard {
  cardId: string
  name: string
  sourceNovel?: string
  authorName?: string
  createdBy: string
  
  // 结构化规则
  rhythmRules?: RhythmRule
  dialogueRules?: DialogueRule
  descriptionRules?: DescriptionRule
  emotionRules?: EmotionRule
  structureRules?: StructureRule
  forbiddenRules?: ForbiddenRule
  preferredRules?: PreferredRule
  
  // 自然语言描述
  styleDescription?: string
  generationTemplate?: string
  
  // 原文样本
  samplesJson?: StyleSampleItem[]
  
  // 统计数据
  statsJson?: StyleMetrics
  
  // 元信息
  tags?: string
  isPublic: boolean
  usageCount: number
  avgRating?: number
  
  status: number // 0=分析中, 1=可用, 2=分析失败
  createTime: string
  updateTime: string
}

/** 节奏规则 */
export interface RhythmRule {
  description?: string
  rule?: string
  avgSentenceLength?: number
  shortSentenceRatio?: number
  avgParagraphLength?: number
  shortParagraphRatio?: number
}

/** 对话规则 */
export interface DialogueRule {
  style?: string
  quirks?: string[]
  rule?: string
  ratio?: number
  avgLineLength?: number
}

/** 描写规则 */
export interface DescriptionRule {
  dominantSense?: string
  metaphorType?: string
  rule?: string
  senseDistribution?: Record<string, number>
  adjectiveDensity?: string
  detailApproach?: string
}

/** 情绪表达规则 */
export interface EmotionRule {
  anger?: string
  fear?: string
  joy?: string
  sadness?: string
  forbidden?: string[]
}

/** 段落结构规则 */
export interface StructureRule {
  sceneTransition?: string
  chapterEnding?: string
  povDiscipline?: string
}

/** 禁用规则 */
export interface ForbiddenRule {
  words?: string[]
  structures?: string[]
}

/** 偏好规则 */
export interface PreferredRule {
  words?: string[]
  structures?: string[]
}

/** 样本项 */
export interface StyleSampleItem {
  type: string
  text: string
  annotation?: string
}

/** 量化指标 */
export interface StyleMetrics {
  avgSentenceLength: number
  shortSentenceRatio: number
  maxSentenceLength: number
  avgParagraphLength: number
  shortParagraphRatio: number
  dialogueRatio: number
  avgDialogueLength: number
  ellipsisCount: number
  emDashCount: number
  exclamationCount: number
  visualDensity: number
  auditoryDensity: number
  tactileDensity: number
  distinctiveWords: string[]
  totalWords: number
  totalSentences: number
  totalParagraphs: number
  dialogueParagraphs: number
}

/** 质量检查结果 */
export interface StyleCheckResult {
  score: number
  pass: boolean
  issues: string[]
  suggestions?: string[]
  bestMatchParagraph?: string
  worstMatchParagraph?: string
  checkType: string
}

/** 创建卡片请求 */
export interface CreateStyleCardRequest {
  novelName: string
  text: string
  configId?: string
}

/** 绑定小说请求 */
export interface BindNovelRequest {
  novelId: string
  cardId: string
}

/** 检查请求 */
export interface CheckRequest {
  text: string
  cardId: string
}

/** 卡片状态枚举 */
export enum CardStatus {
  ANALYZING = 0,
  AVAILABLE = 1,
  FAILED = 2
}

/** 卡片状态标签 */
export const CardStatusLabels: Record<CardStatus, string> = {
  [CardStatus.ANALYZING]: '分析中',
  [CardStatus.AVAILABLE]: '可用',
  [CardStatus.FAILED]: '分析失败'
}

/** 卡片状态类型 */
export const CardStatusTypes: Record<CardStatus, string> = {
  [CardStatus.ANALYZING]: 'warning',
  [CardStatus.AVAILABLE]: 'success',
  [CardStatus.FAILED]: 'danger'
}
