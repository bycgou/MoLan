export interface StyleCard {
  id: string;
  name: string;
  metrics: StyleMetrics | null;
  sampleText: string | null;
  isPublic: boolean;
}

export interface StyleMetrics {
  avgSentenceLength: number;
  shortSentenceRatio: number;
  dialogueRatio: number;
  avgParagraphLength: number;
}

export interface StyleRules {
  rhythm?: { description?: string; rule?: string; avgSentenceLength?: number; shortSentenceRatio?: number };
  dialogue?: { style?: string; quirks?: string[]; rule?: string; ratio?: number };
  description?: { dominantSense?: string; metaphorType?: string; rule?: string };
  emotion?: { anger?: string; fear?: string; joy?: string; sadness?: string; forbidden?: string[] };
  structure?: { sceneTransition?: string; chapterEnding?: string; povDiscipline?: string };
  forbidden?: { words?: string[]; structures?: string[] };
  preferred?: { words?: string[]; structures?: string[] };
}
