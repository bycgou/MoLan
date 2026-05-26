package com.novel.system.ai.graph;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class GraphRunner {

    private static final List<String> NODE_ORDER = List.of(
            "arc_check", "outline", "clue_planning", "character_planning",
            "world_validate", "idea_select", "writing", "self_review",
            "validation", "quality_scoring", "audit_router", "summary", "post_process"
    );

    private static final int MAX_ITERATIONS = 20;
    private static final int MAX_AUTHOR_REJECT_RETRIES = 1;
    private static final int MAX_AUTHOR_REDIRECT_RETRIES = 1;

    private final Map<String, NovelGraphNode> nodes = new ConcurrentHashMap<>();
    private AuthorAgent authorAgent;

    public void register(NovelGraphNode node) {
        nodes.put(node.nodeName(), node);
    }

    public void setAuthorAgent(AuthorAgent authorAgent) {
        this.authorAgent = authorAgent;
    }

    public NovelAgentState execute(NovelAgentState initialState) {
        return executeFrom(initialState, 0);
    }

    /**
     * 从指定节点索引开始执行，用于跳过已完成的前置阶段。
     */
    public NovelAgentState executeFrom(NovelAgentState initialState, int startIndex) {
        NovelAgentState state = initialState;
        int currentIndex = startIndex;

        for (int iter = 0; iter < MAX_ITERATIONS && currentIndex < NODE_ORDER.size(); iter++) {
            String nodeName = NODE_ORDER.get(currentIndex);
            NovelGraphNode node = nodes.get(nodeName);

            if (node == null) {
                log.warn("[GraphRunner] 节点未注册: {}", nodeName);
                Instant now = Instant.now();
                state = StateHelper.appendTrace(state, NodeExecutionRecord.error(
                        nodeName, state.getMode(), now, now, ErrorStrategy.FAIL,
                        new StructuredError(nodeName, ErrorStrategy.FAIL, "Node not registered", null, now, false)));
                currentIndex++;
                continue;
            }

            if (node.shouldSkip(state)) {
                log.debug("[GraphRunner] 跳过节点: {}", nodeName);
                Instant now = Instant.now();
                state = StateHelper.appendTrace(state, NodeExecutionRecord.skipped(
                        nodeName, state.getMode(), now, now));
                currentIndex++;
                continue;
            }

            AuthorDirective directive = null;
            if (authorAgent != null) {
                directive = authorAgent.beforeNode(nodeName, state);
                if (directive != null && directive.getAction() == AuthorDirective.Action.REDIRECT) {
                    String redirectKey = "__redirect_from_" + nodeName;
                    int redirectCount = StateHelper.getNodeRetryCount(state, redirectKey);
                    if (redirectCount >= MAX_AUTHOR_REDIRECT_RETRIES) {
                        log.warn("[GraphRunner] AuthorAgent REDIRECT from {} exceeded max retries ({}), ignoring directive",
                                nodeName, MAX_AUTHOR_REDIRECT_RETRIES);
                    } else {
                        String target = directive.getTargetNode();
                        if (target != null) {
                            int targetIndex = NODE_ORDER.indexOf(target);
                            if (targetIndex >= 0) {
                                log.info("[GraphRunner] AuthorAgent重定向: {} → {}", nodeName, target);
                                state = StateHelper.incrementNodeRetry(state, redirectKey);
                                state = authorAgent.afterNode(nodeName, state, directive);
                                state = resetStateForReroute(state, target);
                                Instant now = Instant.now();
                                state = StateHelper.appendTrace(state, NodeExecutionRecord.success(
                                        nodeName, state.getMode(), now, now, directive,
                                        Map.of("action", "author_redirect", "target", target)));
                                currentIndex = targetIndex;
                                continue;
                            }
                        }
                    }
                }
                if (directive != null && directive.getAction() == AuthorDirective.Action.REJECT) {
                    String rejectKey = "__author_reject_" + nodeName;
                    int rejectCount = StateHelper.getNodeRetryCount(state, rejectKey);
                    if (rejectCount >= MAX_AUTHOR_REJECT_RETRIES) {
                        log.warn("[GraphRunner] AuthorAgent REJECT on {} exceeded max retries ({}), proceeding with execution",
                                nodeName, MAX_AUTHOR_REJECT_RETRIES);
                    } else {
                        log.info("[GraphRunner] AuthorAgent否决: {}", nodeName);
                        state = StateHelper.incrementNodeRetry(state, rejectKey);
                        state = authorAgent.afterNode(nodeName, state, directive);
                        Instant now = Instant.now();
                        state = StateHelper.appendTrace(state, NodeExecutionRecord.success(
                                nodeName, state.getMode(), now, now, directive,
                                Map.of("action", "author_rejected")));
                        currentIndex++;
                        continue;
                    }
                }
            }

            Instant startTime = Instant.now();
            log.info("[GraphRunner] 执行节点: {} (iter={})", nodeName, iter);
            state = executeNodeWithErrorStrategy(nodeName, state);
            Instant endTime = Instant.now();
            state = StateHelper.appendTrace(state, NodeExecutionRecord.success(
                    nodeName, state.getMode(), startTime, endTime, directive, Map.of()));

            if (authorAgent != null && directive != null) {
                state = authorAgent.afterNode(nodeName, state, directive);
            }

            String nextNode = resolveNextNode(nodeName, state);
            if (nextNode != null) {
                int targetIndex = NODE_ORDER.indexOf(nextNode);
                if (targetIndex >= 0) {
                    log.info("[GraphRunner] 路由跳转: {} → {}", nodeName, nextNode);
                    state = resetStateForReroute(state, nextNode);
                    currentIndex = targetIndex;
                    continue;
                }
            }
            currentIndex++;
        }

        return state;
    }

    private NovelAgentState executeNodeWithErrorStrategy(String nodeName, NovelAgentState state) {
        NovelGraphNode node = nodes.get(nodeName);
        if (node == null) {
            log.error("Node not found: {}", nodeName);
            return StateHelper.appendError(state, new StructuredError(
                nodeName, ErrorStrategy.FAIL, "Node not found", null, Instant.now(), false));
        }

        ErrorStrategy strategy = node.errorStrategy();
        int maxRetries = strategy == ErrorStrategy.RETRY ? node.maxRetries() : 0;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                if (attempt > 0) {
                    log.info("Retrying node {} (attempt {}/{})", nodeName, attempt, maxRetries);
                }
                return node.apply(state);
            } catch (Exception e) {
                log.warn("Node {} failed on attempt {}: {}", nodeName, attempt + 1, e.getMessage());

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

    private String resolveNextNode(String currentNode, NovelAgentState state) {
        if ("audit_router".equals(currentNode)) {
            String decision = state.getAuditDecision();
            String next = "PASS".equals(decision) ? "summary"
                    : "REPLAN".equals(decision) ? "outline"
                    : "REWRITE".equals(decision) ? "writing" : null;

            // Node-level retry protection for loop paths
            if (next != null && ("outline".equals(next) || "writing".equals(next))) {
                int count = StateHelper.getNodeRetryCount(state, next);
                int maxRetries = "outline".equals(next) ? 2 : 3;
                if (count >= maxRetries) {
                    log.warn("[GraphRunner] Node {} exceeded max retries ({}), forcing fallback to summary", next, maxRetries);
                    return "summary";
                }
            }
            return next;
        }

        if ("self_review".equals(currentNode)) {
            NovelAgentState.DraftContent draft = state.getDraftContent();
            if (draft != null && draft.getSeverity() == NovelAgentState.Severity.HIGH
                    && state.getRewriteCount() < 2) {
                // Node-level retry protection for writing loop
                int count = StateHelper.getNodeRetryCount(state, "writing");
                if (count >= 3) {
                    log.warn("[GraphRunner] Node writing exceeded max retries (3) from self_review, forcing fallback to summary");
                    return "summary";
                }
                log.info("[GraphRunner] 自审HIGH，回退重写 (rewriteCount={})", state.getRewriteCount());
                return "writing";
            }
        }

        return null;
    }

    private NovelAgentState appendTrace(NovelAgentState state, String trace) {
        return StateHelper.appendTrace(state, trace);
    }

    private NovelAgentState resetStateForReroute(NovelAgentState state, String targetNode) {
        NovelAgentState.NovelAgentStateBuilder builder = state.toBuilder();

        switch (targetNode) {
            case "outline":
                builder.chapterPlan(null)
                        .preWriteValidation(null)
                        .selectedIdea(null)
                        .implementationIdeas(null);
                break;
            case "writing":
                NovelAgentState.DraftContent draft = state.getDraftContent();
                if (draft != null) {
                    builder.draftContent(draft.toBuilder()
                            .severity(NovelAgentState.Severity.HIGH)
                            .build());
                }
                break;
            default:
                break;
        }

        return builder.build();
    }
}
