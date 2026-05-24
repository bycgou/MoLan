package com.novel.system.ai.graph;

import lombok.extern.slf4j.Slf4j;

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
                state = appendTrace(state, nodeName + "(missing)");
                currentIndex++;
                continue;
            }

            if (node.shouldSkip(state)) {
                log.debug("[GraphRunner] 跳过节点: {}", nodeName);
                state = appendTrace(state, nodeName + "(skipped)");
                currentIndex++;
                continue;
            }

            AuthorDirective directive = null;
            if (authorAgent != null) {
                directive = authorAgent.beforeNode(nodeName, state);
                if (directive != null && directive.getAction() == AuthorDirective.Action.REDIRECT) {
                    String target = directive.getTargetNode();
                    if (target != null) {
                        int targetIndex = NODE_ORDER.indexOf(target);
                        if (targetIndex >= 0) {
                            log.info("[GraphRunner] AuthorAgent重定向: {} → {}", nodeName, target);
                            state = authorAgent.afterNode(nodeName, state, directive);
                            state = resetStateForReroute(state, target);
                            state = appendTrace(state, nodeName + "(author_redirect→" + target + ")");
                            currentIndex = targetIndex;
                            continue;
                        }
                    }
                }
                if (directive != null && directive.getAction() == AuthorDirective.Action.REJECT) {
                    log.info("[GraphRunner] AuthorAgent否决: {}", nodeName);
                    state = authorAgent.afterNode(nodeName, state, directive);
                    state = appendTrace(state, nodeName + "(author_rejected)");
                    currentIndex++;
                    continue;
                }
            }

            try {
                log.info("[GraphRunner] 执行节点: {} (iter={})", nodeName, iter);
                state = node.apply(state);
                state = appendTrace(state, nodeName);
            } catch (Exception e) {
                log.error("[GraphRunner] 节点执行失败: {} - {}", nodeName, e.getMessage(), e);
                state = StateHelper.appendError(state, nodeName + "失败: " + e.getMessage());
                state = appendTrace(state, nodeName + "(error)");
            }

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

    private String resolveNextNode(String currentNode, NovelAgentState state) {
        if ("audit_router".equals(currentNode)) {
            String decision = state.getAuditDecision();
            if ("PASS".equals(decision)) return "summary";
            if ("REPLAN".equals(decision)) return "outline";
            if ("REWRITE".equals(decision)) return "writing";
            return null;
        }

        if ("self_review".equals(currentNode)) {
            NovelAgentState.DraftContent draft = state.getDraftContent();
            if (draft != null && draft.getSeverity() == NovelAgentState.Severity.HIGH
                    && state.getRewriteCount() < 2) {
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
