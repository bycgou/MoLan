package com.novel.system.ai.graph;

import com.novel.system.entity.mongo.ArcOrchestration;
import com.novel.system.entity.mysql.NovelArc;
import com.novel.system.mapper.NovelArcMapper;
import com.novel.system.service.orchestration.ArcOrchestrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 卷级编排检查节点 -- 确保当前章节有编排约束
 *
 * 检查当前章节是否已有卷级编排计划。如果没有但卷纲存在，
 * 尝试自动触发编排生成（兜底机制）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArcOrchestrationNode implements NovelGraphNode {

    private final ArcOrchestrationService arcOrchestrationService;
    private final NovelArcMapper novelArcMapper;

    @Override
    public String nodeName() { return "arc_check"; }

    @Override
    public boolean shouldSkip(NovelAgentState state) {
        return state.getOutlineContext() != null && !state.getOutlineContext().isEmpty();
    }

    @Override
    public NovelAgentState apply(NovelAgentState state) throws Exception {
        log.info("[{}] 检查卷级编排, novelId={}, chapter={}",
                nodeName(), state.getNovelId(), state.getChapterNum());

        NovelAgentState result = state;
        String rhythmTag = null;
        try {
            ArcOrchestration.ChapterPlan chapterPlan = arcOrchestrationService
                    .getChapterPlan(state.getNovelId(), state.getChapterNum());
            if (chapterPlan == null) {
                // 兜底：卷纲存在但编排数据缺失，尝试自动生成
                chapterPlan = tryFallbackOrchestration(state);
            }
            if (chapterPlan == null) {
                log.warn("章节 {} 无卷级编排约束，章纲将缺少节奏/视角指导",
                        state.getChapterNum());
                result = StateHelper.appendError(result,
                        "卷级编排缺失: 章节" + state.getChapterNum() + "无编排约束");
            } else {
                log.info("卷级编排检查通过, rhythmTag={}, perspective={}",
                        chapterPlan.getRhythmTag(), chapterPlan.getPerspective());
                rhythmTag = chapterPlan.getRhythmTag();
            }
        } catch (Exception e) {
            log.error("卷级编排检查失败（不影响后续流程）: {}", e.getMessage());
            result = StateHelper.appendError(result, "卷级编排检查失败: " + e.getMessage());
        }

        result = result.toBuilder()
                .rhythmTag(rhythmTag)
                .currentPhase(NovelAgentState.GenerationPhase.ARC_CHECK.name())
                .build();
        return StateHelper.appendTrace(result, nodeName());
    }

    /**
     * 兜底：当编排数据缺失但卷纲存在时，尝试自动触发编排生成
     */
    private ArcOrchestration.ChapterPlan tryFallbackOrchestration(NovelAgentState state) {
        try {
            NovelArc arc = novelArcMapper.findByChapterNum(state.getNovelId(), state.getChapterNum());
            if (arc == null) {
                return null; // 连卷纲都没有，无法兜底
            }
            log.info("[{}] 卷纲存在但编排数据缺失，自动触发编排生成: arcId={}, arcName={}",
                    nodeName(), arc.getArcId(), arc.getArcName());
            arcOrchestrationService.orchestrate(
                    state.getNovelId(), arc.getArcId(), state.getLlmConfigId());
            // 编排生成后重新查询
            return arcOrchestrationService.getChapterPlan(state.getNovelId(), state.getChapterNum());
        } catch (Exception e) {
            log.warn("[{}] 自动编排生成失败: {}", nodeName(), e.getMessage());
            return null;
        }
    }
}
