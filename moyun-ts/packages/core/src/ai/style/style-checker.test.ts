import { describe, it, expect } from 'vitest';
import { StyleCheckerService } from './style-checker.service.js';

describe('StyleCheckerService', () => {
  const checker = new StyleCheckerService();

  it('passes clean text', () => {
    const text = '少年踏上了旅途。他穿过密林，翻过高山，终于来到了古城门前。"到了。"他低声说。';
    const result = checker.quickCheck(text);
    expect(result.passed).toBe(true);
    expect(result.score).toBeGreaterThanOrEqual(70);
  });

  it('detects AI cliches', () => {
    const text = '他不禁感叹，一股暖流涌上心头。心中五味杂陈，不知不觉已经泪流满面。';
    const result = checker.quickCheck(text);
    expect(result.score).toBeLessThan(100);
    expect(result.issues.length).toBeGreaterThan(0);
  });

  it('returns metrics', () => {
    const text = '这是一段测试文本。包含几个句子。';
    const result = checker.quickCheck(text);
    expect(result.metrics).toBeDefined();
    expect(result.metrics!.avgSentenceLength).toBeGreaterThan(0);
  });
});
