package com.novel.system.ai.graph;

public interface NovelGraphNode {

    NovelAgentState apply(NovelAgentState state) throws Exception;

    String nodeName();

    default boolean shouldSkip(NovelAgentState state) {
        return false;
    }

    /**
     * 该节点的错误策略。默认 SKIP。
     */
    default ErrorStrategy errorStrategy() {
        return ErrorStrategy.SKIP;
    }

    /**
     * 该节点的最大重试次数（仅当 errorStrategy=RETRY 时生效）。默认 2。
     */
    default int maxRetries() {
        return 2;
    }

    /**
     * 当流程重新路由到该节点时，声明如何重置状态。
     * 默认不做任何重置。
     *
     * @param state  当前状态
     * @param source 触发重置的来源节点名（预留参数，当前实现可忽略）
     */
    default NovelAgentState resetState(NovelAgentState state, String source) {
        return state;
    }
}
