export type StrandType = 'quest' | 'fire' | 'constellation';

export interface Chapter {
  id: string;
  novelId: string;
  chapterNum: number;
  title: string | null;
  content: string | null;
  outline: ChapterOutline | null;
  plotFingerprint: string | null;
  strandType: StrandType | null;
  wordCount: number;
  isDeleted: boolean;
  createdAt: Date;
  updatedAt: Date;
}

export interface ChapterOutline {
  summary: string;
  keyPoints: string[];
  hook: string;
  ideas?: string[];
}

export interface ChapterListItem {
  id: string;
  chapterNum: number;
  title: string | null;
  strandType: StrandType | null;
  wordCount: number;
  createdAt: Date;
}

export interface ChapterSummary {
  chapterId: string;
  fingerprint: string | null;
  keywords: string[];
  characters: string[];
  locations: string[];
  relations: string[];
}

export interface ChapterVersion {
  id: string;
  chapterId: string;
  versionNum: number;
  content: string;
  createdAt: Date;
}
