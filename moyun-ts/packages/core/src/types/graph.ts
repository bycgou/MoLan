export type GenerationPhase =
  | 'INIT' | 'CONTEXT' | 'ARC_CHECK' | 'OUTLINE'
  | 'CLUE_PLAN' | 'CHAR_PLAN' | 'PRE_WRITE_VALID'
  | 'IDEA_SELECT' | 'WRITING' | 'SELF_REVIEW'
  | 'VALIDATION' | 'QUALITY_CHECK' | 'AUDIT_ROUTER'
  | 'SUMMARY' | 'POST_PROCESS' | 'COMPLETED' | 'FAILED';

export interface ChapterPlan {
  title: string;
  summary: string;
  keyPoints: string[];
  hook: string;
  implementationIdeas: string[];
  clueActions: ClueAction[];
  characterActions: CharacterAction[];
  emotionalArc?: string;
  climaxPoint?: string;
}

export interface ClueAction {
  clueId?: string;
  clueTitle: string;
  action: 'PLANT' | 'RESOLVE' | 'ADVANCE';
  description: string;
  fromOrchestration?: boolean;
}

export interface CharacterAction {
  characterId?: string;
  characterName: string;
  role: string;
  goal: string;
  emotionalState: string;
  keyAction: string;
  interactions?: string[];
}

export interface PreWriteValidation {
  passed: boolean;
  violations: string[];
  warnings: string[];
  suggestions: string[];
}

export interface DraftContent {
  content: string;
  wordCount: number;
  severity: 'LOW' | 'MEDIUM' | 'HIGH';
  revisionNotes?: string;
}

export interface AuditResult {
  allPassed: boolean;
  oocPassed: boolean;
  logicPassed: boolean;
  cluePassed: boolean;
  settingPassed: boolean;
  rhythmPassed: boolean;
  qualityScore: number;
  clueLandingCheck: boolean;
  suggestions: string[];
}

export interface FinalResult {
  finalContent: string;
  wordCount: number;
  qualityScore: number;
  chapterId?: string;
  summary?: string;
}

export interface NovelAgentState {
  // Request input
  novelId: string;
  chapterNum: number;
  userIntent?: string;
  styleCardId?: string;

  // Context
  outlineContext?: string;
  writingContext?: string;

  // Phase 1: Planning
  chapterPlan?: ChapterPlan;
  preWriteValidation?: PreWriteValidation;

  // Phase 2: Writing
  selectedIdea?: string;
  draftContent?: DraftContent;

  // Phase 3: Audit
  validationResults?: import('../ai/validation.service.js').ValidationResults;
  qualityScore?: number;
  auditDecision?: 'PASS' | 'REWRITE' | 'REPLAN' | 'MANUAL_REVIEW';
  selfReviewNotes?: string;

  // Phase 4: Final
  finalResult?: FinalResult;

  // Flow control
  iterationCount: number;
  planRetryCount: number;
  writeRetryCount: number;
  rewriteCount: number;
  currentPhase: GenerationPhase;
  executionTrace: string[];
  errors: string[];
}

export interface AgentExecutionResult {
  content: string;
  wordCount: number;
  qualityScore: number;
  chapterId?: string;
  executionTrace: string[];
  errors: string[];
  elapsedMs: number;
}

export interface NovelGraphNode {
  nodeName: string;
  apply(state: NovelAgentState): Promise<NovelAgentState>;
  shouldSkip?(state: NovelAgentState): boolean;
}
