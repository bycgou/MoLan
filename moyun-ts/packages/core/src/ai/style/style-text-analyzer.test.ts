// packages/core/src/ai/style/style-text-analyzer.test.ts
import { describe, it, expect } from 'vitest';
import { StyleTextAnalyzer } from './style-text-analyzer.js';

describe('StyleTextAnalyzer', () => {
  const analyzer = new StyleTextAnalyzer();

  it('computes basic sentence metrics', () => {
    const text = '这是一个短句。这是一个比较长的句子，包含了很多内容和细节。';
    const metrics = analyzer.analyze(text);
    expect(metrics.avgSentenceLength).toBeGreaterThan(0);
    expect(metrics.shortSentenceRatio).toBeGreaterThanOrEqual(0);
  });

  it('detects dialogue ratio', () => {
    const text = '"你好。""我很好。"他说。';
    const metrics = analyzer.analyze(text);
    expect(metrics.dialogueRatio).toBeGreaterThan(0);
  });

  it('counts punctuation', () => {
    const text = '你好！再见？嗯……好的——';
    const metrics = analyzer.analyze(text);
    expect(metrics.exclamationCount).toBe(1);
    expect(metrics.questionMarkCount).toBe(1);
    expect(metrics.ellipsisCount).toBe(1);
    expect(metrics.emDashCount).toBe(1);
  });

  it('computes sensory density', () => {
    const text = '他看见远方的光芒，听见远处的轰鸣，触摸到冰冷的墙壁。';
    const metrics = analyzer.analyze(text);
    expect(metrics.visualDensity).toBeGreaterThan(0);
    expect(metrics.auditoryDensity).toBeGreaterThan(0);
    expect(metrics.tactileDensity).toBeGreaterThan(0);
  });

  it('returns distinctive words', () => {
    const text = '少年踏上旅途。少年遇见老人。少年学会了剑术。';
    const metrics = analyzer.analyze(text);
    expect(metrics.distinctiveWords.length).toBeGreaterThan(0);
  });
});
