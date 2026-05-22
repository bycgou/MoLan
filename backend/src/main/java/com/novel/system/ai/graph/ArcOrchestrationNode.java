package com.novel.system.ai.graph;

import com.novel.system.entity.mongo.ArcOrchestration;
import com.novel.system.service.orchestration.ArcOrchestrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 卷级编排检查节点 -- 确保当前章节有编排约束
 *
 * 检查当前章节是否已有卷级编排计划。如果没有，记录警告
 * （自动编排需要 arcId，不在当前状态中，由上游卷纲流程负责生成）。
 * 错误不阻断流程，仅附加到 state.errors。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArcOrchestrationNode implements NovelGraphNode {

    private final ArcOrchestrationService arcOrchestrationService;

    @Override
    public String nodeName() { return "arc_check"; }

    @Override
    public NovelAgentState apply(NovelAgentState state) throws Exception {
        log.info("[{}] 检查卷级编排, novelId={}, chapter={}",
                nodeName(), state.getNovelId(), state.getChapterNum());

        NovelAgentState result = state;
        try {
            ArcOrchestration.ChapterPlan chapterPlan = arcOrchestrationService
                    .getChapterPlan(state.getNovelId(), state.getChapterNum());
            if (chapterPlan == null) {
                log.warn("章节 {} 无卷级编排约束，章纲将缺少节奏/视角指导",
                        state.getChapterNum());
                result = StateHelper.appendError(result,
                        "卷级编排缺失: 章节" + state.getChapterNum() + "无编排约束");
            } else {
                log.info("卷级编排检查通过, rhythmTag={}, perspective={}",
                        chapterPlan.getRhythmTag(), chapterPlan.getPerspective());
            }
        } catch (Exception e) {
            log.error("卷级编排检查失败（不影响后续流程）: {}", e.getMessage());
            result = StateHelper.appendError(result, "卷级编排检查失败: " + e.getMessage());
        }

        result = result.toBuilder()
                .currentPhase(NovelAgentState.GenerationPhase.ARC_CHECK.name())
                .build();
        return StateHelper.appendTrace(result, nodeName());
    }
}
