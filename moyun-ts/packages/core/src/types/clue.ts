export type ClueType = 'main_plot' | 'subplot' | 'dark_line';

export interface Clue {
  id: string;
  novelId: string;
  title: string;
  type: ClueType;
  description: string | null;
  buryChapter: number | null;
  recycleChapter: number | null;
  isRecycled: boolean;
}

export interface CreateClueInput {
  novelId: string;
  title: string;
  type: ClueType;
  description?: string;
  buryChapter?: number;
}
