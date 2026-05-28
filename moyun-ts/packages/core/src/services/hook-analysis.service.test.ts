import { describe, it, expect } from 'vitest';
import { HookAnalysisService } from './hook-analysis.service.js';

describe('HookAnalysisService', () => {
  const service = new HookAnalysisService();

  it('should analyze content and return scores', () => {
    const content = '忽然，一道光芒闪过！少年心中涌起无限激动。\n他领悟了新的力量！\n这到底是怎么回事？';
    const result = service.analyze(content);
    expect(result.totalScore).toBeGreaterThan(0);
    expect(result.totalScore).toBeLessThanOrEqual(100);
    expect(result.pullLevel).toBeDefined();
    expect(result.suggestion).toBeDefined();
  });

  it('should return 0 for empty content', () => {
    const result = service.analyze('');
    expect(result.totalScore).toBe(0);
    expect(result.pullLevel).toBe('D');
    expect(result.suggestion).toBe('内容为空，无法分析');
  });

  it('should score high for content with many hooks', () => {
    const content = '「什么！」他忽然震惊了。突破！领悟！觉醒！\n这怎么可能？他逆袭了！\n所有人都震惊全场！';
    const result = service.analyze(content);
    expect(result.totalScore).toBeGreaterThan(40);
  });

  it('should provide suggestions for weak areas', () => {
    const content = '普通的一天过去了。';
    const result = service.analyze(content);
    expect(result.suggestion).not.toBe('整体节奏良好');
  });
});
