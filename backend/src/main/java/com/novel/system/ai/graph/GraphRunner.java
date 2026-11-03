package com.novel.system.ai.graph;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@Slf4j
public class GraphRunner {

    private final GraphDefinition definition;
    private final java.util.Map<String, NovelGraphNode> nodes = new ConcurrentHashMap<>();
    private final ExecutorService parallelExecutor = Executors.newFixedThreadPool(
            Math.min(Runtime.getRuntime().availableProcessors(), 8));
    private NodeExecutor nodeExecutor;
    private StateCheckpointService checkpointService;

    private static final int MAX_ITERATIONS = 20;

    public GraphRunner(GraphDefinition definition) {
        this.definition = definition;
    }

    public void register(NovelGraphNode node) {
        nodes.put(node.nodeName(), node);
    }

    public void setNodeExecutor(NodeExecutor nodeExecutor) {
        this.nodeExecutor = nodeExecutor;
    }

    public void setCheckpointService(StateCheckpointService checkpointService) {
        this.checkpointService = checkpointService;
    }

    // 保留 setAuthorAgent 用于测试兼容，但实际 AuthorAgent 由 NodeExecutor 管理
    public void setAuthorAgent(AuthorAgent authorAgent) {
        // 创建一个简单的 NodeExecutor 用于向后兼容
        this.nodeExecutor = new NodeExecutor(authorAgent, null, definition);
    }

    public NovelAgentState execute(NovelAgentState initialState) {
        return execute(initialState, Mode.FULL);
    }

    public NovelAgentState execute(NovelAgentState initialState, Mode mode) {
        NovelAgentState state = initialState.toBuilder().mode(mode).build();

        // 断点恢复检查
        if (mode == Mode.FULL && checkpointService != null) {
            java.util.Optional<StateSnapshot> checkpoint = checkpointService
                    .loadLatestCheckpoint(state.getNovelId(), state.getChapterNum());
            if (checkpoint.isPresent()) {
                log.info("[GraphRunner] 从检查点恢复: {}", checkpoint.get().getNodeName());
                try {
                    NovelAgentState restored = new com.fasterxml.jackson.databind.ObjectMapper()
                            .readValue(checkpoint.get().getStateJson(), NovelAgentState.class);
                    String resumeNode = getNextNode(checkpoint.get().getNodeName(), restored, mode);
                    return executeFrom(resumeNode, restored, mode);
                } catch (Exception e) {
                    log.warn("[GraphRunner] 检查点恢复失败，从头执行: {}", e.getMessage());
                }
            }
        }

        return executeFrom("arc_check", state, mode);
    }

    private NovelAgentState executeFrom(String startNode, NovelAgentState state, Mode mode) {
        String currentNode = startNode;
        int iterations = 0;

        while (currentNode != null && !currentNode.equals(GraphDefinition.END)) {
            // 客户端断开检测：检查取消标志
            if (state.getCancelFlag() != null && state.getCancelFlag().get()) {
                log.info("[GraphRunner] Execution cancelled by client disconnect, stopping at node: {}", currentNode);
                break;
            }

            if (++iterations > MAX_ITERATIONS) {
                log.error("[GraphRunner] Max iterations ({}) exceeded, terminating", MAX_ITERATIONS);
                state = StateHelper.appendError(state, new StructuredError(
                        currentNode, ErrorStrategy.FAIL, "Max iterations exceeded",
                        null, Instant.now(), false));
                break;
            }

            NovelGraphNode node = nodes.get(currentNode);
            if (node == null) {
                log.error("[GraphRunner] Node not found: {}", currentNode);
                state = StateHelper.appendError(state, new StructuredError(
                        currentNode, ErrorStrategy.FAIL, "Node not registered",
                        null, Instant.now(), false));
                break;
            }

            log.info("[GraphRunner] Executing node: {} (iter={}, mode={})", currentNode, iterations, mode);

            if (nodeExecutor != null) {
                state = nodeExecutor.execute(node, state, mode);
            } else {
                // Fallback: 直接执行（无 AuthorAgent、无检查点）
                try {
                    state = node.apply(state);
                } catch (Exception e) {
                    log.error("[GraphRunner] Node {} failed: {}", currentNode, e.getMessage(), e);
                    state = StateHelper.appendError(state, new StructuredError(
                            currentNode, ErrorStrategy.SKIP, e.getMessage(),
                            e, Instant.now(), true));
                }
            }

            log.info("[GraphRunner] Node {} completed: hasChapterPlan={}, hasDraft={}, hasFinalResult={}, errors={}",
                    currentNode,
                    state.getChapterPlan() != null,
                    state.getDraftContent() != null,
                    state.getFinalResult() != null,
                    state.getErrors().size());

            Instant now = Instant.now();
            state = StateHelper.appendTrace(state, NodeExecutionRecord.success(
                    currentNode, state.getMode(), now, now,
                    state.getAuthorDirective(), Map.of()));

            if (definition.isFanOut(currentNode, mode)) {
                state = executeFanOut(currentNode, state, mode);
                currentNode = definition.getFanInTarget(
                        definition.getFanOutTargets(currentNode, mode), mode);
                if (currentNode == null) {
                    currentNode = GraphDefinition.END;
                }
                continue;
            }

            String nextNode = getNextNode(currentNode, state, mode);
            if (nextNode != null && !nextNode.equals(GraphDefinition.END)) {
                NovelGraphNode nextGraphNode = nodes.get(nextNode);
                if (nextGraphNode != null) {
                    NovelAgentState resetResult = nextGraphNode.resetState(state, currentNode);
                    state = resetResult != null ? resetResult : state;
                }
            }
            currentNode = nextNode;
        }

        // 章节生成完成后清理检查点
        if (checkpointService != null && state.getNovelId() != null) {
            checkpointService.clearCheckpoints(state.getNovelId(), state.getChapterNum());
        }

        return state;
    }

    /**
     * Fan-out: execute multiple nodes in parallel, merge results using pluggable MergeStrategy.
     */
    private NovelAgentState executeFanOut(String sourceNode, NovelAgentState state, Mode mode) {
        List<String> targets = definition.getFanOutTargets(sourceNode, mode);
        MergeStrategy strategy = definition.getFanOutMergeStrategy(sourceNode, mode);
        if (targets.isEmpty()) {
            return state;
        }

        log.info("[GraphRunner] Fan-out from {}: {}", sourceNode, targets);
        final NovelAgentState currentState = state;

        List<CompletableFuture<NovelAgentState>> futures = targets.stream()
                .map(target -> CompletableFuture.supplyAsync(() -> {
                    NovelGraphNode node = nodes.get(target);
                    if (node == null) {
                        log.warn("[GraphRunner] Fan-out node not registered: {}", target);
                        return StateHelper.appendError(currentState, new StructuredError(
                                target, ErrorStrategy.SKIP, "Node not registered",
                                null, Instant.now(), true));
                    }
                    try {
                        return node.apply(currentState);
                    } catch (Exception e) {
                        log.error("[GraphRunner] Fan-out node {} failed: {}", target, e.getMessage());
                        return StateHelper.appendError(currentState, new StructuredError(
                                target, ErrorStrategy.SKIP, e.getMessage(),
                                e, Instant.now(), true));
                    }
                }, parallelExecutor))
                .toList();

        List<NovelAgentState> results = new ArrayList<>();
        for (int i = 0; i < futures.size(); i++) {
            try {
                results.add(futures.get(i).get(60, TimeUnit.SECONDS));
            } catch (Exception e) {
                log.error("[GraphRunner] Fan-out target {} failed: {}", targets.get(i), e.getMessage());
                results.add(StateHelper.appendError(currentState, new StructuredError(
                        targets.get(i), ErrorStrategy.SKIP, e.getMessage(),
                        e, Instant.now(), true)));
            }
        }

        return strategy.merge(state, results);
    }

    /**
     * Route to next node using GraphDefinition, with retry protection overlay.
     */
    private String getNextNode(String currentNode, NovelAgentState state, Mode mode) {
        // 1. Try conditional edges (first match wins, with retry protection)
        for (GraphDefinition.ConditionalEdge ce : definition.getConditionalEdges()) {
            if (ce.from().equals(currentNode) && definition.matchesMode(ce.modes(), mode)) {
                if (ce.condition().test(state)) {
                    String target = resolveWithRetryProtection(ce.to(), state, currentNode);
                    if (target != null) {
                        return target;
                    }
                    // Target exceeded retries, try next conditional edge
                }
            }
        }

        // 2. Try simple edges (with retry protection)
        for (GraphDefinition.Edge edge : definition.getSimpleEdges()) {
            if (edge.from().equals(currentNode) && definition.matchesMode(edge.modes(), mode)) {
                String target = resolveWithRetryProtection(edge.to(), state, currentNode);
                if (target != null) {
                    return target;
                }
                // Target exceeded retries, try next edge
            }
        }

        // 3. No edge matched — use definition fallback or END
        String fallback = definition.getFallbackNode(currentNode);
        if (!GraphDefinition.END.equals(fallback)) {
            return fallback;
        }
        return GraphDefinition.END;
    }

    /**
     * Check if target node has exceeded retry limits. Returns target if OK, fallback if exceeded.
     */
    private String resolveWithRetryProtection(String target, NovelAgentState state, String fromNode) {
        int maxRetries = definition.getMaxRetries(target);
        if (maxRetries <= 0) {
            return target;
        }
        int retryCount = StateHelper.getNodeRetryCount(state, target);
        if (retryCount >= maxRetries) {
            log.warn("[GraphRunner] Node {} exceeded max retries ({}/{}) from {}, using fallback",
                    target, retryCount, maxRetries, fromNode);
            return definition.getFallbackNode(target);
        }
        return target;
    }

}
