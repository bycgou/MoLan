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
