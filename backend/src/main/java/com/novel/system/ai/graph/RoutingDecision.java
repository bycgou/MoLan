package com.novel.system.ai.graph;

import java.util.Map;

public record RoutingDecision(
    String fromNode,
    String toNode,
    String reason,
    Map<String, Object> stateSnapshot
) {
    @Override
    public String toString() {
        return fromNode + " -> " + toNode + " (" + reason + ")";
    }
}
