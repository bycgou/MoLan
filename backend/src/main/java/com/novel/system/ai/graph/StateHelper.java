package com.novel.system.ai.graph;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 状态操作工具类 — 不可变模式
 */
public final class StateHelper {

    private StateHelper() {}

    @Deprecated
    public static NovelAgentState appendTrace(NovelAgentState state, String trace) {
        var newTrace = new ArrayList<>(state.getExecutionTrace());
        newTrace.add(NodeExecutionRecord.success("__legacy__", state.getMode(),
                Instant.now(), Instant.now(), null, Map.of("message", trace)));
        return state.toBuilder().executionTrace(newTrace).build();
    }

    public static NovelAgentState appendTrace(NovelAgentState state, NodeExecutionRecord record) {
        var newTrace = new ArrayList<>(state.getExecutionTrace());
        newTrace.add(record);
        return state.toBuilder().executionTrace(newTrace).build();
    }

    @Deprecated
    public static NovelAgentState appendError(NovelAgentState state, String error) {
        var newErrors = new ArrayList<>(state.getErrors());
        newErrors.add(new StructuredError("__legacy__", null, error, null, Instant.now(), false));
        return state.toBuilder().errors(newErrors).build();
    }

    public static NovelAgentState appendError(NovelAgentState state, StructuredError error) {
        var newErrors = new ArrayList<>(state.getErrors());
        newErrors.add(error);
        return state.toBuilder().errors(newErrors).build();
    }

    public static NovelAgentState appendLlmCall(NovelAgentState state, LlmCallRecord record) {
        var newCalls = new ArrayList<>(state.getLlmCalls());
        newCalls.add(record);
        return state.toBuilder().llmCalls(newCalls).build();
    }

    public static int getNodeRetryCount(NovelAgentState state, String nodeName) {
        return state.getNodeRetryCounts().getOrDefault(nodeName, 0);
    }

    public static NovelAgentState incrementNodeRetry(NovelAgentState state, String nodeName) {
        var newCounts = new java.util.HashMap<>(state.getNodeRetryCounts());
        newCounts.merge(nodeName, 1, Integer::sum);
        return state.toBuilder().nodeRetryCounts(newCounts).build();
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

    /**
     * 截断文本到指定长度，超出部分用"..."替代
     */
    public static String truncate(String text, int maxLen) {
        if (text == null) return null;
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }

    /**
     * 格式化伏笔操作列表为可读文本
     */
    public static String formatClueActions(List<NovelAgentState.ClueAction> actions) {
        if (actions == null || actions.isEmpty()) return "无";
        return actions.stream()
                .map(a -> String.format("[%s] %s: %s%s",
                        a.getAction(), a.getClueId(), a.getDescription(),
                        a.isFromOrchestration() ? " (卷级编排)" : ""))
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    /**
     * 格式化角色动线列表为可读文本
     */
    public static String formatCharacterActions(List<NovelAgentState.CharacterAction> actions) {
        if (actions == null || actions.isEmpty()) return "无";
        return actions.stream()
                .map(a -> String.format("%s(%s): %s, 情绪:%s",
                        a.getCharacterName(), a.getRole(), a.getGoal(), a.getEmotionalState()))
                .collect(java.util.stream.Collectors.joining("\n"));
    }
}
