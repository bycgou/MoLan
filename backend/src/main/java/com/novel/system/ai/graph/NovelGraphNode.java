package com.novel.system.ai.graph;

/**
 * 节点接口 — 所有编排节点实现此接口
 * 当前手动编排使用，后续可适配 LangGraph4j
 */
public interface NovelGraphNode {

    /**
     * 执行节点逻辑
     *
     * @param state 当前状态（不可变，返回新实例）
     * @return 更新后的状态
     */
    NovelAgentState apply(NovelAgentState state) throws Exception;

    /**
     * 节点名称（用于日志和执行追踪）
     */
    String nodeName();
}
