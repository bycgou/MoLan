package com.novel.system.ai.graph;

import java.util.ArrayList;
import java.util.List;

/**
 * 状态操作工具类 — 不可变模式
 */
public final class StateHelper {

    private StateHelper() {}

    public static NovelAgentState appendTrace(NovelAgentState state, String trace) {
        List<String> newTrace = new ArrayList<>(state.getExecutionTrace());
        newTrace.add(trace);
        return state.toBuilder().executionTrace(newTrace).build();
    }

    public static NovelAgentState appendError(NovelAgentState state, String error) {
        List<String> newErrors = new ArrayList<>(state.getErrors());
        newErrors.add(error);
        return state.toBuilder().errors(newErrors).build();
    }

    public static NovelAgentState incrementIteration(NovelAgentState state, String type) {
        NovelAgentState.NovelAgentStateBuilder builder = state.toBuilder()
                .iterationCount(state.getIterationCount() + 1);
        if ("plan".equals(type)) {
            builder.planRetryCount(state.getPlanRetryCount() + 1);
        } else if ("write".equals(type)) {
            builder.writeRetryCount(state.getWriteRetryCount() + 1);
        }
        return builder.build();
    }
}
