export type StrandType = 'quest' | 'fire' | 'constellation';

export interface RhythmStatus {
  lastStrand: StrandType | null;
  consecutive: number;
  gap: number;
  ratios: {
    quest: number;
    fire: number;
    constellation: number;
  };
}
