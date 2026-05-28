// packages/core/src/ai/style/style-text-analyzer.ts
import { SENSORY_WORDS, EMOTION_WORDS, TRANSITION_CLICHES, METAPHOR_MARKERS } from './word-lists.js';

export interface StyleMetrics {
  // 基础指标
  avgSentenceLength: number;
  shortSentenceRatio: number;
  maxSentenceLength: number;
  avgParagraphLength: number;
  shortParagraphRatio: number;
  // 对话统计
  dialogueRatio: number;
  avgDialogueLength: number;
  maxConsecutiveDialogue: number;
  // 标点习惯
  ellipsisCount: number;
  emDashCount: number;
  exclamationCount: number;
  questionMarkCount: number;
  // 感官词密度（每千字）
  visualDensity: number;
  auditoryDensity: number;
  tactileDensity: number;
  olfactoryDensity: number;
  gustatoryDensity: number;
  // 反模式检测
  sentenceDiversityIndex: number;
  directCutRatio: number;
  transitionClicheRatio: number;
  actionEndingRatio: number;
  dialogueEndingRatio: number;
  suspenseEndingRatio: number;
  emptySceneRatio: number;
  // 风格指标
  metaphorDensity: number;
  alternationRate: number;
  positiveEmotionDensity: number;
  negativeEmotionDensity: number;
  // 特色词
  distinctiveWords: string[];
}

export class StyleTextAnalyzer {
  analyze(text: string): StyleMetrics {
    const sentences = this.splitSentences(text);
    const paragraphs = text.split(/\n\n+/).filter(p => p.trim().length > 0);
    const charCount = text.replace(/\s+/g, '').length || 1;

    return {
      avgSentenceLength: this.avg(sentences.map(s => s.replace(/\s+/g, '').length)),
      shortSentenceRatio: sentences.filter(s => s.replace(/\s+/g, '').length < 15).length / (sentences.length || 1),
      maxSentenceLength: Math.max(0, ...sentences.map(s => s.replace(/\s+/g, '').length)),
      avgParagraphLength: this.avg(paragraphs.map(p => p.replace(/\s+/g, '').length)),
      shortParagraphRatio: paragraphs.filter(p => p.replace(/\s+/g, '').length < 60).length / (paragraphs.length || 1),

      dialogueRatio: this.calcDialogueRatio(text),
      avgDialogueLength: this.avgDialogueLength(text),
      maxConsecutiveDialogue: this.maxConsecutiveDialogue(paragraphs),

      ellipsisCount: (text.match(/……/g) || []).length,
      emDashCount: (text.match(/——/g) || []).length,
      exclamationCount: (text.match(/！/g) || []).length,
      questionMarkCount: (text.match(/？/g) || []).length,

      visualDensity: this.sensoryDensity(text, SENSORY_WORDS.visual, charCount),
      auditoryDensity: this.sensoryDensity(text, SENSORY_WORDS.auditory, charCount),
      tactileDensity: this.sensoryDensity(text, SENSORY_WORDS.tactile, charCount),
      olfactoryDensity: this.sensoryDensity(text, SENSORY_WORDS.olfactory, charCount),
      gustatoryDensity: this.sensoryDensity(text, SENSORY_WORDS.gustatory, charCount),

      sentenceDiversityIndex: this.coefficientOfVariation(sentences.map(s => s.replace(/\s+/g, '').length)),
      directCutRatio: this.directCutRatio(paragraphs),
      transitionClicheRatio: this.transitionClicheRatio(paragraphs),
      actionEndingRatio: this.endingRatio(paragraphs, 'action'),
      dialogueEndingRatio: this.endingRatio(paragraphs, 'dialogue'),
      suspenseEndingRatio: this.endingRatio(paragraphs, 'suspense'),
      emptySceneRatio: this.emptySceneRatio(paragraphs),

      metaphorDensity: this.metaphorDensity(text, charCount),
      alternationRate: this.alternationRate(sentences),
      positiveEmotionDensity: this.emotionDensity(text, EMOTION_WORDS.positive, charCount),
      negativeEmotionDensity: this.emotionDensity(text, EMOTION_WORDS.negative, charCount),

      distinctiveWords: this.topDistinctiveWords(text, 20),
    };
  }

  private splitSentences(text: string): string[] {
    return text.split(/[。！？……]+/).filter(s => s.trim().length > 0);
  }

  private avg(nums: number[]): number {
    if (nums.length === 0) return 0;
    return nums.reduce((a, b) => a + b, 0) / nums.length;
  }

  private coefficientOfVariation(nums: number[]): number {
    if (nums.length === 0) return 0;
    const mean = this.avg(nums);
    if (mean === 0) return 0;
    const variance = nums.reduce((sum, n) => sum + (n - mean) ** 2, 0) / nums.length;
    return Math.sqrt(variance) / mean;
  }

  private calcDialogueRatio(text: string): number {
    const dialogueMatches = text.match(/[""「][^""」]*[""」]/g);
    const dialogueLength = dialogueMatches?.reduce((sum, m) => sum + m.length, 0) ?? 0;
    const totalLength = text.replace(/\s+/g, '').length || 1;
    return dialogueLength / totalLength;
  }

  private avgDialogueLength(text: string): number {
    const dialogueMatches = text.match(/[""「][^""」]*[""」]/g);
    if (!dialogueMatches || dialogueMatches.length === 0) return 0;
    return this.avg(dialogueMatches.map(m => m.length));
  }

  private maxConsecutiveDialogue(paragraphs: string[]): number {
    let max = 0;
    let current = 0;
    for (const p of paragraphs) {
      if (p.trim().startsWith('"') || p.trim().startsWith('“') || p.trim().startsWith('「')) {
        current++;
        max = Math.max(max, current);
      } else {
        current = 0;
      }
    }
    return max;
  }

  private sensoryDensity(text: string, words: string[], charCount: number): number {
    let count = 0;
    for (const word of words) {
      const matches = text.match(new RegExp(word, 'g'));
      count += matches?.length ?? 0;
    }
    return (count / charCount) * 1000;
  }

  private emotionDensity(text: string, words: string[], charCount: number): number {
    return this.sensoryDensity(text, words, charCount);
  }

  private directCutRatio(paragraphs: string[]): number {
    if (paragraphs.length === 0) return 0;
    const directCuts = paragraphs.filter(p => {
      const trimmed = p.trim();
      return !TRANSITION_CLICHES.some(c => trimmed.startsWith(c));
    }).length;
    return directCuts / paragraphs.length;
  }

  private transitionClicheRatio(paragraphs: string[]): number {
    if (paragraphs.length === 0) return 0;
    const cliched = paragraphs.filter(p =>
      TRANSITION_CLICHES.some(c => p.includes(c))
    ).length;
    return cliched / paragraphs.length;
  }

  private endingRatio(paragraphs: string[], type: 'action' | 'dialogue' | 'suspense'): number {
    if (paragraphs.length === 0) return 0;
    const last3 = paragraphs.slice(-3);
    let count = 0;
    for (const p of last3) {
      const trimmed = p.trim();
      if (type === 'dialogue' && (trimmed.endsWith('"') || trimmed.endsWith('”') || trimmed.endsWith('」'))) count++;
      if (type === 'suspense' && (trimmed.endsWith('……') || trimmed.endsWith('…') || trimmed.endsWith('——'))) count++;
      if (type === 'action' && !trimmed.endsWith('"') && !trimmed.endsWith('”') && !trimmed.endsWith('……')) count++;
    }
    return count / last3.length;
  }

  private emptySceneRatio(paragraphs: string[]): number {
    if (paragraphs.length === 0) return 0;
    const sensoryAll = [...SENSORY_WORDS.visual, ...SENSORY_WORDS.auditory, ...SENSORY_WORDS.tactile];
    const empty = paragraphs.filter(p => {
      const hasSensory = sensoryAll.some(w => p.includes(w));
      const hasDialogue = p.includes('"') || p.includes('“') || p.includes('「');
      return !hasSensory && !hasDialogue && p.length > 20;
    }).length;
    return empty / paragraphs.length;
  }

  private metaphorDensity(text: string, charCount: number): number {
    let count = 0;
    for (const marker of METAPHOR_MARKERS) {
      const matches = text.match(new RegExp(marker, 'g'));
      count += matches?.length ?? 0;
    }
    return (count / charCount) * 1000;
  }

  private alternationRate(sentences: string[]): number {
    if (sentences.length < 2) return 0;
    let alternations = 0;
    for (let i = 1; i < sentences.length; i++) {
      const prevLen = sentences[i - 1].replace(/\s+/g, '').length;
      const currLen = sentences[i].replace(/\s+/g, '').length;
      const prevShort = prevLen < 15;
      const currShort = currLen < 15;
      if (prevShort !== currShort) alternations++;
    }
    return alternations / (sentences.length - 1);
  }

  private topDistinctiveWords(text: string, topN: number): string[] {
    const stopWords = new Set(['的', '了', '是', '在', '有', '和', '就', '不', '人', '都', '一', '一个', '上', '也', '很', '到', '说', '要', '去', '你', '会', '着', '没有', '看', '好', '自己', '这']);
    const bigrams = new Map<string, number>();
    const cleanText = text.replace(/[^一-鿿]/g, '');
    for (let i = 0; i < cleanText.length - 1; i++) {
      const gram = cleanText.substring(i, i + 2);
      if (!stopWords.has(gram)) {
        bigrams.set(gram, (bigrams.get(gram) ?? 0) + 1);
      }
    }
    return [...bigrams.entries()]
      .sort((a, b) => b[1] - a[1])
      .slice(0, topN)
      .map(([word]) => word);
  }
}
