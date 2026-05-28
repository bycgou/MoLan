export interface ArcOutline {
  arcIndex: number;
  name: string;
  outline: string;
  strandType: string;
  rhythmIntensity: number;
}

export interface ArcChapterOutline {
  chapterNum: number;
  title: string;
  outline: string;
  ideas: string[];
}

export type BatchStatus = 'generating' | 'completed' | 'failed' | 'cancelled';

export interface BatchProgress {
  taskId: string;
  status: BatchStatus;
  total: number;
  completed: number;
  failed: number;
}
