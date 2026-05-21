// ============================================================
// API 统一响应格式
// ============================================================

export interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
}

// ============================================================
// 认证相关
// ============================================================

export interface LoginRequest {
  username: string
  password: string
}

export interface RegisterRequest {
  username: string
  password: string
  email: string
  nickname: string
}

export interface UserVO {
  userId: string
  username: string
  email: string
  nickname: string
  role: string
  status: number
  token: string
}

// ============================================================
// 小说相关
// ============================================================

export interface VolumeSummary {
  arcId: string
  arcName: string
  startChapter: number
  endChapter: number
  chapterCount: number
  arcOutline: string
}

export interface NovelVO {
  novelId: string
  novelName: string
  genre: string
  novelType?: string // JSON数组
  tone?: string // JSON数组
  tabooList?: string // JSON数组
  totalOutline: string
  worldSetting: string
  status: number // 0=草稿, 1=连载中, 2=已完结
  chapterCount: number
  wordCount: number
  createTime: string
  updateTime: string
  creationMode?: string // 'wizard' | 'classic'
  arcCount?: number
  volumes?: VolumeSummary[]
}

export interface CreateNovelRequest {
  novelName: string
  genre: string
  novelType?: string
  tone?: string
  tabooList?: string
  totalOutline: string
  worldSetting: string
}

export interface UpdateNovelRequest {
  novelName?: string
  genre?: string
  novelType?: string
  tone?: string
  tabooList?: string
  totalOutline?: string
  worldSetting?: string
  status?: number
}

export interface NovelGraphVO {
  nodes: GraphNode[]
  edges: GraphEdge[]
}

export interface GraphNode {
  id: string
  name: string
  type: string
  properties: Record<string, unknown>
}

export interface GraphEdge {
  source: string
  target: string
  type: string
  label?: string
  relation?: string
}

export interface TimelineVO {
  clockId?: string
  novelId: string
  currentTime: string
  currentDate: string
  season: string
  weather: string
  chapterDay: number
  totalDays: number
  updateChapter?: number
  updateTime?: string
}

// ============================================================
// 章节相关
// ============================================================

export interface ChapterVO {
  chapterId: string
  novelId: string
  chapterNum: number
  chapterTitle: string
  content: string
  wordCount: number
  strandType: string // quest / fire / constellation
  plotFingerprint: string
  chapterOutline?: string
  summary: string
  status: number
  createTime: string
  updateTime: string
}

export interface ChapterListItem {
  chapterId: string
  chapterNum: number
  chapterTitle: string
  chapterOutline?: string
  wordCount: number
  strandType: string
  status: number
  createTime: string
  updateTime: string
}

export interface GenerateChapterRequest {
  chapterNum: number
  chapterTitle: string
  configId: string
  strandType: string
}

export interface GenerateOutlineRequest {
  novelName: string
  genre: string
  prompt?: string
}

export interface ChapterVersionVO {
  versionId: string
  chapterNum: number
  content: string
  createTime: string
}

// ============================================================
// 人物相关
// ============================================================

export interface CharacterVO {
  characterId: string
  novelId: string
  name: string
  role: string // 主角/配角/反派/龙套
  description: string
  personality: string
  background: string
  growthNodes: GrowthNode[]
  createTime: string
  updateTime: string
}

export interface CreateCharacterRequest {
  name: string
  role: string
  description: string
  personality: string
  background: string
}

export interface UpdateCharacterRequest {
  name?: string
  role?: string
  description?: string
  personality?: string
  background?: string
}

export interface GrowthNode {
  nodeId: string
  chapterNum: number
  title: string
  description: string
  growthType: string // 能力提升/性格转变/关系变化
  createTime: string
}

export interface AddGrowthNodeRequest {
  chapterNum: number
  title: string
  description: string
  growthType: string
}

// ============================================================
// 伏笔/线索相关
// ============================================================

export interface ClueVO {
  clueId: string
  novelId: string
  title: string
  description: string
  clueType: string // 主线伏笔/支线伏笔/暗线
  plantedChapter: number
  recycleChapter: number | null
  status: number // 0=未回收, 1=已回收
  createTime: string
  updateTime: string
}

export interface CreateClueRequest {
  title: string
  description: string
  clueType: string
  plantedChapter: number
}

// ============================================================
// LLM 配置相关
// ============================================================

export interface LlmConfigVO {
  configId: string
  configName: string
  provider: string
  apiKey: string
  apiBaseUrl: string
  modelName: string
  authType: string
  temperature: number
  maxTokens: number
  topP: number
  isActive: number // 0=禁用, 1=启用
  usageType: string // all / outline / content / summary
  userId: string
  createTime: string
  updateTime: string
}

export interface CreateLlmConfigRequest {
  configName: string
  provider: string
  apiKey: string
  apiBaseUrl: string
  modelName: string
  authType?: string
  temperature?: number
  maxTokens?: number
  topP?: number
  isActive?: number
  usageType?: string
}

export interface UpdateLlmConfigRequest {
  configName?: string
  provider?: string
  apiKey?: string
  apiBaseUrl?: string
  modelName?: string
  authType?: string
  temperature?: number
  maxTokens?: number
  topP?: number
  isActive?: number
  usageType?: string
}

export interface TestConnectionRequest {
  provider: string
  apiKey: string
  apiBaseUrl: string
  modelName: string
}

// ============================================================
// 节奏系统相关
// ============================================================

export interface RhythmStatusVO {
  lastStrand: string
  questConsecutiveCount: number
  fireGapCount: number
  constellationGapCount: number
  questRatio: number
  fireRatio: number
  constellationRatio: number
}

export interface ReadPullTrendVO {
  averageScore: number
  trend: string // rising / stable / declining
  scores: number[]
  chapterNumbers?: number[]
  readPullScores?: number[]
}

// ============================================================
// 题材模板相关
// ============================================================

export interface GenreTemplateVO {
  code: string
  name: string
  description: string
  tropes: string[]
  pitfalls: string[]
  coreThemes: string[]
  characterArchetypes: Record<string, string>
  plotPatterns: string[]
  status: number
}

// ============================================================
// 钩子分析相关
// ============================================================

export interface HookAnalysisVO {
  openingAttraction: number // 开篇吸引力 1-10
  suspenseSetup: number // 悬念设置 1-10
  emotionTrigger: number // 情感触发 1-10
  endingHook: number // 结尾钩子 1-10
  coolPointDensity: number // 爽点密度 1-10
  pullLevel: string // 高/中/低
  totalScore: number // 总分
  suggestion: string // 改进建议
}

// ============================================================
// 向导流程相关
// ============================================================

export interface ArcOutlineDTO {
  arcName: string
  arcOutline: string
  startChapter: number
  endChapter: number
  chapterCount: number
  rhythmTag: string
  rhythmIntensity: number
}

export interface ArcChapterOutlineDTO {
  chapterNum: number
  chapterTitle: string
  outline: string
  implementationIdeas: string[]
}

export interface BatchGenerationProgress {
  taskId: string
  status: string // GENERATING, COMPLETED, FAILED, CANCELLED
  totalChapters: number
  completedChapters: number
  failedChapters: number
  currentChapterNum: number
  currentPhase: string
  errorMessage?: string
}

export interface WizardSettingsRequest {
  novelName: string
  genre: string
  novelType?: string
  tone?: string
  tabooList?: string
  totalOutline: string
  worldSetting: string
  prompt: string
  styleCardId?: string
  llmConfigId?: string
}

export interface WizardSaveRequest {
  novelName: string
  genre: string
  novelType?: string
  tone?: string
  tabooList?: string
  totalOutline: string
  worldSetting: string
  styleCardId?: string
  llmConfigId?: string
  arcs: ArcOutlineDTO[]
}

// ============================================================
// 文风卡片相关
// ============================================================

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
