import { describe, it, expect } from 'vitest';
import { GraphRunner } from './graph-runner.js';
import type { NovelAgentState, NovelGraphNode } from '../../types/graph.js';

describe('GraphRunner', () => {
  function createInitialState(): NovelAgentState {
    return {
      novelId: 'test',
      chapterNum: 1,
      iterationCount: 0,
      planRetryCount: 0,
      writeRetryCount: 0,
      rewriteCount: 0,
      currentPhase: 'INIT',
      executionTrace: [],
      errors: [],
    };
  }

  it('executes nodes in order', async () => {
    const runner = new GraphRunner();
    const trace: string[] = [];

    const mockNode = (name: string): NovelGraphNode => ({
      nodeName: name,
      apply: async (state) => {
        trace.push(name);
        return { ...state, currentPhase: name.toUpperCase() as any };
      },
    });

    runner.register(mockNode('arc_check'));
    runner.register(mockNode('outline'));
    runner.register(mockNode('summary'));

    const result = await runner.execute(createInitialState());
    expect(trace).toEqual(['arc_check', 'outline', 'summary']);
  });

  it('skips nodes when shouldSkip returns true', async () => {
    const runner = new GraphRunner();
    const trace: string[] = [];

    runner.register({
      nodeName: 'arc_check',
      shouldSkip: () => true,
      apply: async (state) => { trace.push('arc_check'); return state; },
    });
    runner.register({
      nodeName: 'outline',
      apply: async (state) => { trace.push('outline'); return state; },
    });

    await runner.execute(createInitialState());
    expect(trace).toEqual(['outline']);
  });

  it('marks unregistered nodes as missing in trace', async () => {
    const runner = new GraphRunner();
    const trace: string[] = [];

    runner.register({
      nodeName: 'outline',
      apply: async (state) => { trace.push('outline'); return state; },
    });

    const result = await runner.execute(createInitialState());
    expect(result.executionTrace).toContain('arc_check(missing)');
    expect(result.executionTrace).toContain('outline');
  });

  it('routes to summary when audit_decision is PASS', async () => {
    const runner = new GraphRunner();
    const trace: string[] = [];

    const mockNode = (name: string, extra?: Partial<NovelAgentState>): NovelGraphNode => ({
      nodeName: name,
      apply: async (state) => {
        trace.push(name);
        return { ...state, ...extra };
      },
    });

    runner.register(mockNode('audit_router', { auditDecision: 'PASS' }));
    runner.register(mockNode('summary'));

    const result = await runner.execute(createInitialState());
    expect(trace).toEqual(['audit_router', 'summary']);
  });

  it('routes back to outline when audit_decision is REPLAN', async () => {
    const runner = new GraphRunner();
    const trace: string[] = [];

    const mockNode = (name: string, extra?: Partial<NovelAgentState>): NovelGraphNode => ({
      nodeName: name,
      apply: async (state) => {
        trace.push(name);
        return { ...state, ...extra };
      },
    });

    runner.register(mockNode('outline'));
    runner.register(mockNode('audit_router', { auditDecision: 'REPLAN' }));
    runner.register(mockNode('summary'));

    const result = await runner.execute(createInitialState());
    expect(trace).toEqual(['outline', 'audit_router', 'outline']);
  });

  it('routes back to writing when audit_decision is REWRITE', async () => {
    const runner = new GraphRunner();
    const trace: string[] = [];

    runner.register({
      nodeName: 'writing',
      apply: async (state) => { trace.push('writing'); return state; },
    });
    // On first call return REWRITE, on second call return PASS to exit loop
    let auditCall = 0;
    runner.register({
      nodeName: 'audit_router',
      apply: async (state) => {
        trace.push('audit_router');
        auditCall++;
        return { ...state, auditDecision: auditCall === 1 ? 'REWRITE' : 'PASS' };
      },
    });
    runner.register({
      nodeName: 'summary',
      apply: async (state) => { trace.push('summary'); return state; },
    });

    const result = await runner.execute(createInitialState());
    expect(trace).toEqual(['writing', 'audit_router', 'writing', 'audit_router', 'summary']);
  });

  it('self_review triggers rewrite when severity is HIGH and rewriteCount < 2', async () => {
    const runner = new GraphRunner();
    const trace: string[] = [];

    // writing increments rewriteCount so the loop terminates after one re-entry
    runner.register({
      nodeName: 'writing',
      apply: async (state) => {
        trace.push('writing');
        return { ...state, rewriteCount: state.rewriteCount + 1 };
      },
    });
    runner.register({
      nodeName: 'self_review',
      apply: async (state) => {
        trace.push('self_review');
        // On second call, lower severity so it proceeds to validation
        if (state.rewriteCount >= 2) {
          return { ...state, draftContent: { content: 'test', wordCount: 100, severity: 'LOW' } };
        }
        return { ...state, draftContent: { content: 'test', wordCount: 100, severity: 'HIGH' } };
      },
    });
    runner.register({
      nodeName: 'validation',
      apply: async (state) => { trace.push('validation'); return state; },
    });

    const result = await runner.execute(createInitialState());
    // First: writing (rewriteCount 0→1) → self_review (HIGH → routes back to writing)
    // Second: writing (rewriteCount 1→2) → self_review (LOW → proceeds to validation)
    expect(trace).toEqual(['writing', 'self_review', 'writing', 'self_review', 'validation']);
  });

  it('self_review does not trigger rewrite when rewriteCount >= 2', async () => {
    const runner = new GraphRunner();
    const trace: string[] = [];

    const mockNode = (name: string, extra?: Partial<NovelAgentState>): NovelGraphNode => ({
      nodeName: name,
      apply: async (state) => {
        trace.push(name);
        return { ...state, ...extra };
      },
    });

    runner.register(mockNode('writing'));
    runner.register(mockNode('self_review', {
      draftContent: { content: 'test', wordCount: 100, severity: 'HIGH' },
    }));
    runner.register(mockNode('validation'));

    const result = await runner.execute({
      ...createInitialState(),
      rewriteCount: 2,
    });
    expect(trace).toEqual(['writing', 'self_review', 'validation']);
  });

  it('respects maxIterations limit', async () => {
    const runner = new GraphRunner();
    let callCount = 0;

    runner.register({
      nodeName: 'audit_router',
      apply: async (state) => {
        callCount++;
        return { ...state, auditDecision: 'REPLAN' };
      },
    });
    runner.register({
      nodeName: 'outline',
      apply: async (state) => state,
    });

    const result = await runner.execute(createInitialState());
    expect(callCount).toBeLessThanOrEqual(20);
  });

  it('populates executionTrace in returned state', async () => {
    const runner = new GraphRunner();

    runner.register({
      nodeName: 'arc_check',
      apply: async (state) => state,
    });
    runner.register({
      nodeName: 'outline',
      apply: async (state) => state,
    });
    runner.register({
      nodeName: 'summary',
      apply: async (state) => state,
    });

    const result = await runner.execute(createInitialState());
    expect(result.executionTrace).toContain('arc_check');
    expect(result.executionTrace).toContain('outline');
    expect(result.executionTrace).toContain('summary');
  });
});
