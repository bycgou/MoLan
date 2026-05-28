export type NovelStatus = 'draft' | 'serializing' | 'completed';
export type CreationMode = 'classic' | 'wizard';
export type Genre = 'xuanhuan' | 'dog-blood-romance' | 'period-drama' | 'realistic' | 'rules-mystery' | 'zhihu-short';

export interface Novel {
  id: string;
  title: string;
  genre: Genre;
  status: NovelStatus;
  creationMode: CreationMode;
  totalOutline: string | null;
  worldSetting: string | null;
  storyTags: string[] | null;
  toneTags: string[] | null;
  tabooList: string[] | null;
  createdAt: Date;
  updatedAt: Date;
}

export interface CreateNovelInput {
  title: string;
  genre: Genre;
  creationMode?: 'classic' | 'wizard';
  totalOutline?: string;
  worldSetting?: string;
  storyTags?: string[];
  toneTags?: string[];
  tabooList?: string[];
}

export interface UpdateNovelInput {
  title?: string;
  genre?: Genre;
  status?: NovelStatus;
  totalOutline?: string;
  worldSetting?: string;
  storyTags?: string[];
  toneTags?: string[];
  tabooList?: string[];
}
