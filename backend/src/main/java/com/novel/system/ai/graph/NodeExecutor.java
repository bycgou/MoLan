package com.novel.system.ai.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.Map;

@Slf4j
public class NodeExecutor {

    private final AuthorAgent authorAgent;
    private final StateCheckpointService checkpointService;
    private final GraphDefinition definition;

    public NodeExecutor(AuthorAgent authorAgent, StateCheckpointService checkpointService,
                        GraphDefinition definition) {
        this.authorAgent = authorAgent;
        this.checkpointService = checkpointService;
        this.definition = definition;
    }

    public NovelAgentState execute(NovelGraphNode node, NovelAgentState state, Mode mode) {
        String nodeName = node.nodeName();

        if (node.shouldSkip(state)) {
            log.info("[NodeExecutor] Skipping node {} (shouldSkip=true)", nodeName);
            Instant now = Instant.now();
            return StateHelper.appendTrace(state, NodeExecutionRecord.skipped(
                    nodeName, mode, now, now));
        }

        AuthorDirective directive = null;
        if (mode == Mode.FULL && authorAgent != null) {
            directive = authorAgent.beforeNode(nodeName, state);
            if (directive != null) {
                // 发送检查点 SSE 事件（无论是否介入，前端均可展示）
                sendCheckpointEvent(state, nodeName, directive);
                if (directive.shouldIntervene()) {
                    if (directive.getAction() == AuthorDirective.Action.REDIRECT) {
                        return handleRedirect(node, state, directive, nodeName);
                    }
                    if (directive.getAction() == AuthorDirective.Action.REJECT) {
                        return handleReject(node, state, directive, nodeName);
                    }
                }
            }
        }
        state = state.toBuilder().authorDirective(directive).build();

        NovelAgentState result = executeWithRetry(node, state, mode);

        if (authorAgent != null && directive != null) {
            NovelAgentState after = authorAgent.afterNode(nodeName, result, directive);
            if (after != null) result = after;
        }

        if (checkpointService != null && definition.isCheckpoint(nodeName)) {
            checkpointService.saveCheckpoint(nodeName, result);
        }

        return result;
    }

    private NovelAgentState executeWithRetry(NovelGraphNode node, NovelAgentState state, Mode mode) {
        ErrorStrategy strategy = node.errorStrategy();
        int maxRetries = strategy == ErrorStrategy.RETRY ? node.maxRetries() : 0;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                if (attempt > 0) {
                    log.info("[NodeExecutor] Retrying node {} (attempt {}/{})",
                            node.nodeName(), attempt, maxRetries);
                }
                NovelAgentState result = node.apply(state);
                result = StateHelper.incrementNodeRetry(result, node.nodeName());
                return result;
            } catch (Exception e) {
                log.warn("[NodeExecutor] Node {} failed on attempt {}: {}",
                        node.nodeName(), attempt + 1, e.getMessage());

                if (strategy == ErrorStrategy.FAIL) {
                    return StateHelper.appendError(state, new StructuredError(
                            node.nodeName(), strategy, e.getMessage(), e, Instant.now(), false));
                }

                if (strategy == ErrorStrategy.SKIP || attempt == maxRetries) {
                    return StateHelper.appendError(state, new StructuredError(
                            node.nodeName(), strategy, e.getMessage(), e, Instant.now(), true));
                }
            }
        }
        return state;
    }

    private NovelAgentState handleRedirect(NovelGraphNode node, NovelAgentState state,
                                            AuthorDirective directive, String nodeName) {
        String target = directive.getTargetNode();
        if (target != null) {
            log.info("[NodeExecutor] AuthorAgent redirect: {} -> {}", nodeName, target);
        }
        // REDIRECT 不应跳过节点执行。AuthorAgent 的重定向意图通过 afterNode 中的
        // applyRedirectDirective 注入状态（如设置 severity=HIGH、清理旧数据），
        // 然后继续执行当前节点，而非返回未执行的空状态。
        state = state.toBuilder().authorDirective(directive).build();
        NovelAgentState result = executeWithRetry(node, state, state.getMode());

        if (authorAgent != null) {
            NovelAgentState after = authorAgent.afterNode(nodeName, result, directive);
            if (after != null) result = after;
        }

        return result;
    }

    private NovelAgentState handleReject(NovelGraphNode node, NovelAgentState state,
                                          AuthorDirective directive, String nodeName) {
        log.info("[NodeExecutor] AuthorAgent rejected: {}", nodeName);
        Instant now = Instant.now();
        state = StateHelper.appendTrace(state, NodeExecutionRecord.success(
                nodeName, state.getMode(), now, now, directive,
                Map.of("action", "author_rejected")));
        return state;
    }

    /**
     * 向 SSE 流发送 AuthorAgent 检查点事件，供前端实时展示
     */
    private void sendCheckpointEvent(NovelAgentState state, String nodeName, AuthorDirective directive) {
        SseEmitter emitter = state.getSseEmitter();
        if (emitter == null || directive == null) return;
        try {
            Map<String, Object> data = Map.of(
                    "node", nodeName,
                    "action", directive.getAction().name(),
                    "rationale", directive.getRationale() != null ? directive.getRationale() : ""
            );
            emitter.send(SseEmitter.event().name("checkpoint").data(
                    new ObjectMapper().writeValueAsString(data)));
        } catch (Exception e) {
            log.debug("[NodeExecutor] 发送checkpoint SSE事件失败: {}", e.getMessage());
        }
    }
}
