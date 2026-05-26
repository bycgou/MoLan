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
}
