package com.novel.system.ai.graph;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StateHelperTest {

    @Test
    void appendTraceAddsToEmptyList() {
        NovelAgentState state = NovelAgentState.builder().build();
        NovelAgentState result = StateHelper.appendTrace(state, "node1");

        assertTrue(state.getExecutionTrace().isEmpty());
        assertEquals(1, result.getExecutionTrace().size());
        assertEquals("node1", result.getExecutionTrace().get(0));
    }

    @Test
    void appendTracePreservesExisting() {
        NovelAgentState state = NovelAgentState.builder().build();
        NovelAgentState step1 = StateHelper.appendTrace(state, "node1");
        NovelAgentState step2 = StateHelper.appendTrace(step1, "node2");

        assertEquals(1, step1.getExecutionTrace().size());
        assertEquals(2, step2.getExecutionTrace().size());
        assertEquals("node1", step2.getExecutionTrace().get(0));
        assertEquals("node2", step2.getExecutionTrace().get(1));
    }

    @Test
    void appendErrorUsesImmutablePattern() {
        NovelAgentState state = NovelAgentState.builder().build();
        NovelAgentState result = StateHelper.appendError(state, "err1");

        assertTrue(state.getErrors().isEmpty());
        assertEquals(1, result.getErrors().size());
        assertEquals("err1", result.getErrors().get(0));
    }

    @Test
    void incrementIterationPlanType() {
        NovelAgentState state = NovelAgentState.builder().build();
        NovelAgentState result = StateHelper.incrementIteration(state, "plan");

        assertEquals(1, result.getIterationCount());
        assertEquals(1, result.getPlanRetryCount());
        assertEquals(0, result.getWriteRetryCount());
    }

    @Test
    void incrementIterationWriteType() {
        NovelAgentState state = NovelAgentState.builder()
                .iterationCount(2)
                .writeRetryCount(1)
                .build();
        NovelAgentState result = StateHelper.incrementIteration(state, "write");

        assertEquals(3, result.getIterationCount());
        assertEquals(0, result.getPlanRetryCount());
        assertEquals(2, result.getWriteRetryCount());
    }
}
