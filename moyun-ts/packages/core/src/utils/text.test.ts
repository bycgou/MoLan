import { describe, it, expect } from 'vitest';
import { countWords } from './text.js';

describe('countWords', () => {
  it('counts Chinese characters', () => {
    expect(countWords('你好世界')).toBe(4);
  });

  it('counts mixed Chinese and English', () => {
    expect(countWords('你好Hello世界')).toBe(9);
  });

  it('removes whitespace before counting', () => {
    expect(countWords('  你 好 世 界  ')).toBe(4);
  });

  it('counts Chinese punctuation', () => {
    expect(countWords('你好，世界！')).toBe(6);
  });

  it('handles empty string', () => {
    expect(countWords('')).toBe(0);
  });

  it('matches Java version behavior', () => {
    const text = '这是第一章的内容，讲述了一个勇敢的少年踏上旅途。Hello world';
    expect(countWords(text)).toBe(34);
  });
});
