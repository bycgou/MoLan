package com.novel.system.ai.graph;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StateHelperTest {

    @Test
    void appendTraceAddsToEmptyList() {
        NovelAgentState state = NovelAgentState.builder().build();
        NovelAgentState result = StateHelper.appendTrace(state, "node1");

        assertTrue(state.getExecutionTrace().isEmpty());
        assertEquals(1, result.getExecutionTrace().size());
        assertEquals("node1", result.getExecutionTrace().get(0).metadata().get("message"));
    }

    @Test
    void appendTracePreservesExisting() {
        NovelAgentState state = NovelAgentState.builder().build();
        NovelAgentState step1 = StateHelper.appendTrace(state, "node1");
        NovelAgentState step2 = StateHelper.appendTrace(step1, "node2");

        assertEquals(1, step1.getExecutionTrace().size());
        assertEquals(2, step2.getExecutionTrace().size());
        assertEquals("node1", step2.getExecutionTrace().get(0).metadata().get("message"));
        assertEquals("node2", step2.getExecutionTrace().get(1).metadata().get("message"));
    }

    @Test
    void appendTraceWithNodeExecutionRecord() {
        NovelAgentState state = NovelAgentState.builder().build();
        var record = NodeExecutionRecord.success("testNode", Mode.FULL,
                java.time.Instant.now(), java.time.Instant.now(), null, Map.of());
        NovelAgentState result = StateHelper.appendTrace(state, record);

        assertEquals(1, result.getExecutionTrace().size());
        assertEquals("testNode", result.getExecutionTrace().get(0).nodeName());
        assertEquals(Mode.FULL, result.getExecutionTrace().get(0).mode());
    }

    @Test
    void appendErrorUsesImmutablePattern() {
        NovelAgentState state = NovelAgentState.builder().build();
        NovelAgentState result = StateHelper.appendError(state, "err1");

        assertTrue(state.getErrors().isEmpty());
        assertEquals(1, result.getErrors().size());
        assertEquals("err1", result.getErrors().get(0).message());
        assertEquals("__legacy__", result.getErrors().get(0).nodeName());
    }

    @Test
    void appendErrorWithStructuredError() {
        NovelAgentState state = NovelAgentState.builder().build();
        var error = new StructuredError("testNode", ErrorStrategy.RETRY, "fail", null,
                java.time.Instant.now(), false);
        NovelAgentState result = StateHelper.appendError(state, error);

        assertEquals(1, result.getErrors().size());
        assertEquals("testNode", result.getErrors().get(0).nodeName());
        assertEquals(ErrorStrategy.RETRY, result.getErrors().get(0).strategy());
    }

    @Test
    void appendLlmCall() {
        NovelAgentState state = NovelAgentState.builder().build();
        var record = new LlmCallRecord("writing", "chat", "gpt-4", 100, 50, 150, 2000,
                java.time.Instant.now());
        NovelAgentState result = StateHelper.appendLlmCall(state, record);

        assertTrue(state.getLlmCalls().isEmpty());
        assertEquals(1, result.getLlmCalls().size());
        assertEquals("writing", result.getLlmCalls().get(0).nodeName());
        assertEquals(150, result.getLlmCalls().get(0).totalTokens());
    }

    @Test
    void getNodeRetryCountDefaultsToZero() {
        NovelAgentState state = NovelAgentState.builder().build();
        assertEquals(0, StateHelper.getNodeRetryCount(state, "writing"));
    }

    @Test
    void incrementNodeRetry() {
        NovelAgentState state = NovelAgentState.builder().build();
        NovelAgentState step1 = StateHelper.incrementNodeRetry(state, "writing");
        NovelAgentState step2 = StateHelper.incrementNodeRetry(step1, "writing");
        NovelAgentState step3 = StateHelper.incrementNodeRetry(step2, "outline");

        assertEquals(0, StateHelper.getNodeRetryCount(state, "writing"));
        assertEquals(1, StateHelper.getNodeRetryCount(step1, "writing"));
        assertEquals(2, StateHelper.getNodeRetryCount(step2, "writing"));
        assertEquals(1, StateHelper.getNodeRetryCount(step3, "outline"));
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
