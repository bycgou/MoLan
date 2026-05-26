package com.novel.system.ai.graph;

import java.util.*;
import java.util.function.Predicate;

public class GraphDefinition {
    public static final String END = "__END__";
    public static final String START = "__START__";

    private final List<Edge> edges;
    private final List<ConditionalEdge> conditionalEdges;
    private final Map<String, FanOutGroup> fanOutGroups;
    private final Map<String, FanInGroup> fanInGroups;
    private final Set<String> checkpoints;
    private final Map<String, Integer> retryPolicies;
    private final Map<String, String> fallbackNodes;

    private GraphDefinition(Builder builder) {
        this.edges = List.copyOf(builder.edges);
        this.conditionalEdges = List.copyOf(builder.conditionalEdges);
        this.fanOutGroups = Map.copyOf(builder.fanOutGroups);
        this.fanInGroups = Map.copyOf(builder.fanInGroups);
        this.checkpoints = Set.copyOf(builder.checkpoints);
        this.retryPolicies = Map.copyOf(builder.retryPolicies);
        this.fallbackNodes = Map.copyOf(builder.fallbackNodes);
    }

    public record Edge(String from, String to, Set<Mode> modes) {}
    public record ConditionalEdge(String from, String to, Predicate<NovelAgentState> condition, Set<Mode> modes) {}
    public record FanOutGroup(String source, List<String> targets, Mode mode) {}
    public record FanInGroup(List<String> sources, String target, Mode mode) {}

    public String getNextNode(String currentNode, NovelAgentState state, Mode mode) {
        // 1. Check conditional edges first (order matters - first match wins)
        for (ConditionalEdge ce : conditionalEdges) {
            if (ce.from().equals(currentNode) && matchesMode(ce.modes(), mode)) {
                if (ce.condition().test(state)) {
                    return ce.to();
                }
            }
        }
        // 2. Check simple edges
        for (Edge edge : edges) {
            if (edge.from().equals(currentNode) && matchesMode(edge.modes(), mode)) {
                return edge.to();
            }
        }
        return END;
    }

    public boolean isFanOut(String nodeName) {
        return fanOutGroups.containsKey(nodeName);
    }

    public List<String> getFanOutTargets(String nodeName, Mode mode) {
        FanOutGroup group = fanOutGroups.get(nodeName);
        if (group != null && (group.mode() == null || group.mode() == mode)) {
            return group.targets();
        }
        return List.of();
    }

    public String getFanInTarget(List<String> sources, Mode mode) {
        String key = String.join(",", sources.stream().sorted().toList());
        FanInGroup group = fanInGroups.get(key);
        if (group != null && (group.mode() == null || group.mode() == mode)) {
            return group.target();
        }
        return END;
    }

    public boolean isCheckpoint(String nodeName) {
        return checkpoints.contains(nodeName);
    }

    public int getMaxRetries(String nodeName) {
        return retryPolicies.getOrDefault(nodeName, 0);
    }

    public String getFallbackNode(String nodeName) {
        return fallbackNodes.getOrDefault(nodeName, END);
    }

    public ErrorStrategy getErrorStrategy(String nodeName) {
        return switch (nodeName) {
            case "outline", "writing" -> ErrorStrategy.RETRY;
            case "audit_router" -> ErrorStrategy.FAIL;
            default -> ErrorStrategy.SKIP;
        };
    }

    private boolean matchesMode(Set<Mode> modes, Mode current) {
        return modes.isEmpty() || modes.contains(current);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final List<Edge> edges = new ArrayList<>();
        private final List<ConditionalEdge> conditionalEdges = new ArrayList<>();
        private final Map<String, FanOutGroup> fanOutGroups = new LinkedHashMap<>();
        private final Map<String, FanInGroup> fanInGroups = new LinkedHashMap<>();
        private final Set<String> checkpoints = new LinkedHashSet<>();
        private final Map<String, Integer> retryPolicies = new LinkedHashMap<>();
        private final Map<String, String> fallbackNodes = new LinkedHashMap<>();

        public Builder edge(String from, String to) {
            edges.add(new Edge(from, to, Set.of()));
            return this;
        }

        public Builder edge(String from, String to, Mode mode) {
            edges.add(new Edge(from, to, Set.of(mode)));
            return this;
        }

        public Builder conditionalEdge(String from, String to, Predicate<NovelAgentState> condition) {
            conditionalEdges.add(new ConditionalEdge(from, to, condition, Set.of()));
            return this;
        }

        public Builder conditionalEdge(String from, String to, Predicate<NovelAgentState> condition, Mode mode) {
            conditionalEdges.add(new ConditionalEdge(from, to, condition, Set.of(mode)));
            return this;
        }

        public Builder fanOut(String source, List<String> targets, Mode mode) {
            fanOutGroups.put(source, new FanOutGroup(source, targets, mode));
            return this;
        }

        public Builder fanIn(List<String> sources, String target, Mode mode) {
            String key = String.join(",", sources.stream().sorted().toList());
            fanInGroups.put(key, new FanInGroup(sources, target, mode));
            return this;
        }

        public Builder checkpoint(String nodeName) {
            checkpoints.add(nodeName);
            return this;
        }

        public Builder retryPolicy(String nodeName, int maxRetries, String fallback) {
            retryPolicies.put(nodeName, maxRetries);
            fallbackNodes.put(nodeName, fallback);
            return this;
        }

        public GraphDefinition build() {
            return new GraphDefinition(this);
        }
    }
}
