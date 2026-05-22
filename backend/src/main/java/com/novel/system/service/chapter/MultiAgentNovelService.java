package com.novel.system.service.chapter;

import com.novel.system.ai.graph.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 多Agent编排服务 — 协调所有节点的执行流程
 *
 * 流程：策划(含写前校验) → 创作(含自审) → 审校(保存之前) → 保存
 * 支持重试：写前校验失败重规划(最多2次)，自审HIGH重写(最多2次)，
 *          审校失败重写/replan(总迭代最多4次)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MultiAgentNovelService {

    private final ArcOrchestrationNode arcCheck;
    private final OutlineNode outline;
    private final CluePlanningNode cluePlan;
    private final CharacterPlanningNode charPlan;
    private final WorldValidatorNode worldValidate;
    private final IdeaSelectionNode ideaSelect;
    private final WritingNode writing;
    private final SelfReviewNode selfReview;
    private final ValidationNode validation;
    private final QualityScoringNode qualityScoring;
    private final SummaryNode summary;
    private final PostProcessNode postProcess;

    private static final int MAX_PLAN_RETRIES = 2;
    private static final int MAX_WRITE_RETRIES = 2;
    private static final int MAX_TOTAL_ITERATIONS = 4;

    public AgentExecutionResult writeChapter(String novelId, int chapterNum,
                                              String userIntent, String styleCardId,
                                              String llmConfigId) {
        long startTime = System.currentTimeMillis();

        NovelAgentState state = NovelAgentState.builder()
                .novelId(novelId)
                .chapterNum(chapterNum)
                .userIntent(userIntent)
                .styleCardId(styleCardId)
                .llmConfigId(llmConfigId)
                .build();

        try {
            // ============ Phase 1: 策划 ============
            state = arcCheck.apply(state);
            state = outline.apply(state);
            state = cluePlan.apply(state);
            state = charPlan.apply(state);
            state = worldValidate.apply(state);

            // 写前校验路由
            while (state.getPreWriteValidation() != null
                    && !state.getPreWriteValidation().isPassed()
                    && state.getPlanRetryCount() < MAX_PLAN_RETRIES) {
                log.info("[路由] 写前校验未通过，重规划 {}/{}",
                        state.getPlanRetryCount() + 1, MAX_PLAN_RETRIES);
                state = StateHelper.incrementIteration(state, "plan");
                state = outline.apply(state);
                state = cluePlan.apply(state);
                state = charPlan.apply(state);
                state = worldValidate.apply(state);
            }

            // ============ Phase 2: 创作 ============
            state = ideaSelect.apply(state);
            state = writing.apply(state);
            state = selfReview.apply(state);

            // 自审路由
            while (state.getDraftContent() != null
                    && state.getDraftContent().getSeverity() == NovelAgentState.Severity.HIGH
                    && state.getWriteRetryCount() < MAX_WRITE_RETRIES) {
                log.info("[路由] 自审严重问题，重写 {}/{}",
                        state.getWriteRetryCount() + 1, MAX_WRITE_RETRIES);
                state = StateHelper.incrementIteration(state, "write");
                state = writing.apply(state);
                state = selfReview.apply(state);
            }

            // ============ Phase 3: 审校（保存之前！） ============
            state = validation.apply(state);
            state = qualityScoring.apply(state);

            // 审校路由（循环直到通过或达到上限）
            while (state.getAuditResult() != null && !state.getAuditResult().isAllPassed()) {
                if (state.getIterationCount() >= MAX_TOTAL_ITERATIONS) {
                    log.warn("[路由] 总迭代次数达上限({})，强制通过", state.getIterationCount());
                    break;
                }

                NovelAgentState.AuditResult audit = state.getAuditResult();

                if (!audit.isOocPassed() || !audit.isSettingPassed()) {
                    log.info("[路由] OOC/设定校验未通过，重新规划");
                    state = StateHelper.incrementIteration(state, "plan");
                    state = outline.apply(state);
                    state = cluePlan.apply(state);
                    state = charPlan.apply(state);
                    state = worldValidate.apply(state);
                    state = writing.apply(state);
                    state = selfReview.apply(state);
                } else {
                    log.info("[路由] 审校未通过（质量{}），重写", audit.getQualityScore());
                    state = StateHelper.incrementIteration(state, "write");
                    state = writing.apply(state);
                    state = selfReview.apply(state);
                }

                state = validation.apply(state);
                state = qualityScoring.apply(state);
            }

            // ============ Phase 4: 保存 ============
            state = summary.apply(state);
            state = postProcess.apply(state);

        } catch (Exception e) {
            log.error("章节生成异常: {}", e.getMessage(), e);
            state = StateHelper.appendError(state, "章节生成异常: " + e.getMessage());
        }

        long elapsed = System.currentTimeMillis() - startTime;

        NovelAgentState.FinalResult result = state.getFinalResult();
        return AgentExecutionResult.builder()
                .content(result != null ? result.getFinalContent() : "")
                .wordCount(result != null ? result.getWordCount() : 0)
                .qualityScore(result != null ? result.getQualityScore() : 0)
                .chapterId(result != null ? result.getChapterId() : null)
                .executionTrace(state.getExecutionTrace())
                .errors(state.getErrors())
                .elapsedMs(elapsed)
                .build();
    }
}
