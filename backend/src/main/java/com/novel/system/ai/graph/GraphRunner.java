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
    private AuthorAgent authorAgent;
    private final ExecutorService parallelExecutor = Executors.newCachedThreadPool();

    private static final int MAX_ITERATIONS = 20;
    private static final int MAX_AUTHOR_REJECT_RETRIES = 1;
    private static final int MAX_AUTHOR_REDIRECT_RETRIES = 1;

    public GraphRunner(GraphDefinition definition) {
        this.definition = definition;
    }

    public void register(NovelGraphNode node) {
        nodes.put(node.nodeName(), node);
    }

    public void setAuthorAgent(AuthorAgent authorAgent) {
        this.authorAgent = authorAgent;
    }

    public NovelAgentState execute(NovelAgentState initialState) {
        return execute(initialState, Mode.FULL);
    }

    public NovelAgentState execute(NovelAgentState initialState, Mode mode) {
        NovelAgentState state = initialState.toBuilder().mode(mode).build();
        String currentNode = "arc_check"; // entry node
        int iterations = 0;

        while (currentNode != null && !currentNode.equals(GraphDefinition.END)) {
            if (++iterations > MAX_ITERATIONS) {
                log.error("[GraphRunner] Max iterations ({}) exceeded, terminating", MAX_ITERATIONS);
                state = StateHelper.appendError(state, new StructuredError(
                        currentNode, ErrorStrategy.FAIL, "Max iterations exceeded",
                        null, Instant.now(), false));
                break;
            }

            // AuthorAgent intercept at checkpoints (FULL mode only)
            if (mode == Mode.FULL && definition.isCheckpoint(currentNode) && authorAgent != null) {
                AuthorDirective directive = authorAgent.beforeNode(currentNode, state);
                if (directive != null && directive.getAction() == AuthorDirective.Action.REDIRECT) {
                    String redirectKey = "__redirect_from_" + currentNode;
                    int redirectCount = StateHelper.getNodeRetryCount(state, redirectKey);
                    if (redirectCount >= MAX_AUTHOR_REDIRECT_RETRIES) {
                        log.warn("[GraphRunner] AuthorAgent REDIRECT from {} exceeded max retries ({}), ignoring",
                                currentNode, MAX_AUTHOR_REDIRECT_RETRIES);
                    } else {
                        String target = directive.getTargetNode();
                        if (target != null && nodes.containsKey(target)) {
                            log.info("[GraphRunner] AuthorAgent redirect: {} -> {}", currentNode, target);
                            state = StateHelper.incrementNodeRetry(state, redirectKey);
                            NovelAgentState afterState = authorAgent.afterNode(currentNode, state, directive);
                            state = afterState != null ? afterState : state;
                            NovelGraphNode targetGraphNode = nodes.get(target);
                            if (targetGraphNode != null) {
                                NovelAgentState resetResult = targetGraphNode.resetState(state, currentNode);
                                state = resetResult != null ? resetResult : state;
                            }
                            Instant now = Instant.now();
                            state = StateHelper.appendTrace(state, NodeExecutionRecord.success(
                                    currentNode, state.getMode(), now, now, directive,
                                    Map.of("action", "author_redirect", "target", target)));
                            currentNode = target;
                            continue;
                        }
                    }
                }
                if (directive != null && directive.getAction() == AuthorDirective.Action.REJECT) {
                    String rejectKey = "__author_reject_" + currentNode;
                    int rejectCount = StateHelper.getNodeRetryCount(state, rejectKey);
                    if (rejectCount >= MAX_AUTHOR_REJECT_RETRIES) {
                        log.warn("[GraphRunner] AuthorAgent REJECT on {} exceeded max retries ({}), proceeding",
                                currentNode, MAX_AUTHOR_REJECT_RETRIES);
                    } else {
                        log.info("[GraphRunner] AuthorAgent rejected: {}", currentNode);
                        state = StateHelper.incrementNodeRetry(state, rejectKey);
                        NovelAgentState afterState = authorAgent.afterNode(currentNode, state, directive);
                        state = afterState != null ? afterState : state;
                        Instant now = Instant.now();
                        state = StateHelper.appendTrace(state, NodeExecutionRecord.success(
                                currentNode, state.getMode(), now, now, directive,
                                Map.of("action", "author_rejected")));
                        // Skip this node, route to next
                        currentNode = getNextNode(currentNode, state, mode);
                        continue;
                    }
                }
                // Store directive for afterNode
                state = state.toBuilder().authorDirective(directive).build();
            }

            // Execute node with error strategy
            Instant startTime = Instant.now();
            log.info("[GraphRunner] Executing node: {} (iter={})", currentNode, iterations);
            state = executeNodeWithErrorStrategy(currentNode, state, mode);
            Instant endTime = Instant.now();
            state = StateHelper.appendTrace(state, NodeExecutionRecord.success(
                    currentNode, state.getMode(), startTime, endTime,
                    state.getAuthorDirective(), Map.of()));

            // AuthorAgent afterNode
            if (authorAgent != null && state.getAuthorDirective() != null) {
                NovelAgentState afterState = authorAgent.afterNode(currentNode, state, state.getAuthorDirective());
                if (afterState != null) {
                    state = afterState;
                }
            }

            // Fan-out parallel execution (after source node has been executed)
            if (definition.isFanOut(currentNode)) {
                state = executeFanOut(currentNode, state, mode);
                currentNode = definition.getFanInTarget(
                        definition.getFanOutTargets(currentNode, mode), mode);
                if (currentNode == null) {
                    currentNode = GraphDefinition.END;
                }
                continue;
            }

            // Route to next node (with retry protection)
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

        return state;
    }

    /**
     * Fan-out: execute multiple nodes in parallel, merge results.
     */
    private NovelAgentState executeFanOut(String sourceNode, NovelAgentState state, Mode mode) {
        List<String> targets = definition.getFanOutTargets(sourceNode, mode);
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

        // Wait for all with timeout
        NovelAgentState merged = state;
        for (int i = 0; i < futures.size(); i++) {
            try {
                NovelAgentState result = futures.get(i).get(60, TimeUnit.SECONDS);
                merged = mergeFanOutResult(merged, result, targets.get(i));
            } catch (Exception e) {
                log.error("[GraphRunner] Fan-out target {} failed: {}", targets.get(i), e.getMessage());
                merged = StateHelper.appendError(merged, new StructuredError(
                        targets.get(i), ErrorStrategy.SKIP, e.getMessage(),
                        e, Instant.now(), true));
            }
        }

        return merged;
    }

    /**
     * Merge fan-out results: take clueActions from clue_planning, characterActions from character_planning.
     */
    private NovelAgentState mergeFanOutResult(NovelAgentState base, NovelAgentState result, String nodeName) {
        if ("clue_planning".equals(nodeName)) {
            if (result.getChapterPlan() != null) {
                NovelAgentState.ChapterPlan existing = base.getChapterPlan();
                if (existing != null) {
                    return base.toBuilder()
                            .chapterPlan(existing.toBuilder()
                                    .clueActions(result.getChapterPlan().getClueActions())
                                    .build())
                            .executionTrace(new ArrayList<>(result.getExecutionTrace()))
                            .build();
                }
                return result;
            }
        } else if ("character_planning".equals(nodeName)) {
            if (result.getChapterPlan() != null) {
                NovelAgentState.ChapterPlan existing = base.getChapterPlan();
                if (existing != null) {
                    return base.toBuilder()
                            .chapterPlan(existing.toBuilder()
                                    .characterActions(result.getChapterPlan().getCharacterActions())
                                    .build())
                            .build();
                }
                return result;
            }
        }
        return base;
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

    /**
     * Execute node with error strategy (SKIP/RETRY/FAIL). Preserved from Task 4.
     */
    private NovelAgentState executeNodeWithErrorStrategy(String nodeName, NovelAgentState state, Mode mode) {
        NovelGraphNode node = nodes.get(nodeName);
        if (node == null) {
            log.error("[GraphRunner] Node not found: {}", nodeName);
            return StateHelper.appendError(state, new StructuredError(
                    nodeName, ErrorStrategy.FAIL, "Node not found",
                    null, Instant.now(), false));
        }

        if (node.shouldSkip(state)) {
            log.debug("[GraphRunner] Skipping node: {}", nodeName);
            Instant now = Instant.now();
            state = StateHelper.appendTrace(state, NodeExecutionRecord.skipped(
                    nodeName, mode, now, now));
            return state;
        }

        ErrorStrategy strategy = node.errorStrategy();
        int maxRetries = strategy == ErrorStrategy.RETRY ? node.maxRetries() : 0;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                if (attempt > 0) {
                    log.info("[GraphRunner] Retrying node {} (attempt {}/{})", nodeName, attempt, maxRetries);
                }
                NovelAgentState result = node.apply(state);
                // Track retry count for loop-back nodes
                result = StateHelper.incrementNodeRetry(result, nodeName);
                return result;
            } catch (Exception e) {
                log.warn("[GraphRunner] Node {} failed on attempt {}: {}", nodeName, attempt + 1, e.getMessage());

                if (strategy == ErrorStrategy.FAIL) {
                    return StateHelper.appendError(state, new StructuredError(
                            nodeName, strategy, e.getMessage(), e, Instant.now(), false));
                }

                if (strategy == ErrorStrategy.SKIP || attempt == maxRetries) {
                    return StateHelper.appendError(state, new StructuredError(
                            nodeName, strategy, e.getMessage(), e, Instant.now(), true));
                }
                // RETRY: continue loop
            }
        }
        return state;
    }

}
