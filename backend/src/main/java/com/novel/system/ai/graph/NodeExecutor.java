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

    /**
     * 执行节点，失败时按节点自身的错误策略重试。
     *
     * <p><b>预算语义</b>：{@code nodeRetryCounts} 记录的是「该节点被进入的次数」，
     * 成功和失败都计一次。计数在 {@code apply} 之前完成，并把带计数的 state
     * 传给 {@code apply}，这样节点内部（如 GraphRunner 的路由保护）看到的状态
     * 已经包含本次进入。
     */
    private NovelAgentState executeWithRetry(NovelGraphNode node, NovelAgentState state, Mode mode) {
        ErrorStrategy strategy = node.errorStrategy();
        int maxRetries = strategy == ErrorStrategy.RETRY ? node.maxRetries() : 0;

        // 进入即计数：失败同样消耗图级重试预算，避免异常节点被无限重入
        NovelAgentState countedState = StateHelper.incrementNodeRetry(state, node.nodeName());

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                if (attempt > 0) {
                    log.info("[NodeExecutor] Retrying node {} (attempt {}/{})",
                            node.nodeName(), attempt, maxRetries);
                }
                NovelAgentState result = node.apply(countedState);
                if (result == null) {
                    // 节点返回 null 会让整条图状态失效：后续每个节点拿到的都是 null，
                    // 第一个用状态的节点就会 NPE，报错还指不到真正的元凶。
                    // 退回到进入本节点前的状态并记一条可恢复错误，让调用方看得出是哪个节点没产出。
                    log.warn("[NodeExecutor] Node {} returned null state, keeping previous state",
                            node.nodeName());
                    return StateHelper.appendError(countedState, new StructuredError(
                            node.nodeName(), ErrorStrategy.SKIP, "节点返回 null 状态，已保留进入前的状态",
                            null, Instant.now(), true));
                }
                return result;
            } catch (Exception e) {
                log.warn("[NodeExecutor] Node {} failed on attempt {}: {}",
                        node.nodeName(), attempt + 1, e.getMessage());

                if (strategy == ErrorStrategy.FAIL) {
                    return StateHelper.appendError(countedState, new StructuredError(
                            node.nodeName(), strategy, e.getMessage(), e, Instant.now(), false));
                }

                if (strategy == ErrorStrategy.SKIP || attempt == maxRetries) {
                    return StateHelper.appendError(countedState, new StructuredError(
                            node.nodeName(), strategy, e.getMessage(), e, Instant.now(), true));
                }
            }
        }
        return countedState;
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
