import type { HookAnalysis } from '../types/hook.js';

export class HookAnalysisService {
  analyze(content: string): HookAnalysis {
    if (!content.trim()) {
      return {
        openingAttraction: 0,
        suspenseSetup: 0,
        emotionTrigger: 0,
        endingHook: 0,
        coolPointDensity: 0,
        totalScore: 0,
        pullLevel: 'D',
        suggestion: '内容为空，无法分析',
      };
    }

    const openingAttraction = this.scoreOpening(content);
    const suspenseSetup = this.scoreSuspense(content);
    const emotionTrigger = this.scoreEmotion(content);
    const endingHook = this.scoreEnding(content);
    const coolPointDensity = this.scoreCoolPoints(content);
    const totalScore = openingAttraction + suspenseSetup + emotionTrigger + endingHook + coolPointDensity;

    return {
      openingAttraction,
      suspenseSetup,
      emotionTrigger,
      endingHook,
      coolPointDensity,
      totalScore,
      pullLevel: this.getPullLevel(totalScore),
      suggestion: this.getSuggestion(openingAttraction, suspenseSetup, emotionTrigger, endingHook, coolPointDensity),
    };
  }

  private scoreOpening(content: string): number {
    const lines = content.split('\n').filter(l => l.trim());
    if (lines.length === 0) return 0;
    const opening = lines[0];
    let score = 10; // base
    if (opening.length < 50) score += 3; // short punchy opening
    if (/[！？!？]/.test(opening)) score += 4; // exclamation/question
    if (/["「]/.test(opening)) score += 3; // dialogue opening
    return Math.min(score, 20);
  }

  private scoreSuspense(content: string): number {
    let score = 8; // base
    const suspenseWords = ['忽然', '突然', '猛然', '意外', '竟然', '居然', '没想到', '不料'];
    for (const w of suspenseWords) {
      if (content.includes(w)) score += 2;
    }
    const questionMarks = (content.match(/[？?]/g) || []).length;
    score += Math.min(questionMarks * 2, 6);
    return Math.min(score, 20);
  }

  private scoreEmotion(content: string): number {
    let score = 8;
    const emotionWords = ['心痛', '愤怒', '狂喜', '绝望', '感动', '震惊', '恐惧', '悲伤', '激动'];
    for (const w of emotionWords) {
      if (content.includes(w)) score += 2;
    }
    const exclamations = (content.match(/[！!]/g) || []).length;
    score += Math.min(exclamations, 6);
    return Math.min(score, 20);
  }

  private scoreEnding(content: string): number {
    const lines = content.split('\n').filter(l => l.trim());
    if (lines.length === 0) return 0;
    const ending = lines[lines.length - 1];
    let score = 10;
    if (/[！？!？]/.test(ending)) score += 4;
    if (/["「]/.test(ending)) score += 3;
    if (ending.length > 100) score -= 3; // too long = weak hook
    return Math.max(Math.min(score, 20), 0);
  }

  private scoreCoolPoints(content: string): number {
    let score = 6;
    const coolWords = ['突破', '领悟', '觉醒', '进化', '逆袭', '碾压', '秒杀', '震惊全场'];
    for (const w of coolWords) {
      if (content.includes(w)) score += 2;
    }
    return Math.min(score, 20);
  }

  private getPullLevel(total: number): string {
    if (total >= 80) return 'S';
    if (total >= 60) return 'A';
    if (total >= 40) return 'B';
    if (total >= 20) return 'C';
    return 'D';
  }

  private getSuggestion(o: number, s: number, e: number, en: number, c: number): string {
    const suggestions: string[] = [];
    if (o < 10) suggestions.push('开头吸引力不足，建议用对话或冲突开场');
    if (s < 10) suggestions.push('悬念设置薄弱，增加伏笔和疑问');
    if (e < 10) suggestions.push('情感触发不足，增加角色内心描写');
    if (en < 10) suggestions.push('结尾钩子不强，用悬念或反转收尾');
    if (c < 10) suggestions.push('爽点密度低，增加高光时刻');
    return suggestions.length > 0 ? suggestions.join('；') : '整体节奏良好';
  }
}
