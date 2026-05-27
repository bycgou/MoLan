package com.novel.system.ai.graph;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 实现思路选择节点 — HITL 预留接口
 */
@Slf4j
@Component
public class IdeaSelectionNode implements NovelGraphNode {

    @Override
    public String nodeName() { return "idea_select"; }

    @Override
    public NovelAgentState apply(NovelAgentState state) throws Exception {
        log.info("[{}] 实现思路选择", nodeName());

        NovelAgentState.ChapterPlan plan = state.getChapterPlan();
        List<String> ideas = plan.getImplementationIdeas();
        String selected = (ideas != null && !ideas.isEmpty()) ? ideas.get(0) : null;

        // TODO: Human-in-the-Loop — 多个思路时暂停等待用户选择

        NovelAgentState updated = state.toBuilder()
                .selectedIdea(selected)
                .implementationIdeas(ideas)
                .currentPhase(NovelAgentState.GenerationPhase.IDEA_SELECT.name())
                .build();

        return updated;
    }
}
