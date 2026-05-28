export type CharacterRole = 'protagonist' | 'supporting' | 'villain' | 'extra';
export type GrowthType = 'ability' | 'personality' | 'relationship';

export interface Character {
  id: string;
  novelId: string;
  name: string;
  role: CharacterRole;
  description: string | null;
  personality: string | null;
  background: string | null;
  growthNodes: GrowthNode[];
}

export interface GrowthNode {
  id: string;
  characterId: string;
  chapterNum: number;
  title: string;
  type: GrowthType;
}

export interface CreateCharacterInput {
  novelId: string;
  name: string;
  role: CharacterRole;
  description?: string;
  personality?: string;
  background?: string;
}

export interface UpdateCharacterInput {
  name?: string;
  role?: CharacterRole;
  description?: string;
  personality?: string;
  background?: string;
}
