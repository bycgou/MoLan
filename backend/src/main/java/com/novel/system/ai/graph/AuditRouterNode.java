package com.novel.system.ai.graph;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 审校路由节点 — 决定 PASS / REWRITE / REPLAN / MANUAL_REVIEW。
 *
 * <p><b>反馈链</b>：审校结论（五重校验 + 七维加权 + 伏笔落地）必须真正进入下一轮写作，
 * 否则最贵的一环在空转。因此 REWRITE 时把结论合并进 {@code pendingRevisionNotes}，
 * 由 {@link WritingNode} 在写作 prompt 里注入「=== 待修订问题 ===」段落。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditRouterNode implements NovelGraphNode {

    private static final int MAX_REWRITES = 2;
    private static final int MAX_PLAN_RETRIES = 3;
    private static final double MIN_QUALITY_SCORE = 60.0;

    /** 维度英文名 → 中文标签（写进修订意见，便于模型理解）。 */
    private static final Map<String, String> DIMENSION_LABELS = Map.of(
            "ooc", "人设一致性",
            "logic", "逻辑",
            "clue", "伏笔",
            "setting", "设定",
            "rhythm", "节奏",
            "quality", "质量",
            "clueLanding", "伏笔落地"
    );

    private final ValidationConfig validationConfig;
    private final GradedAuditCalculator gradedAuditCalculator;

    @Override
    public String nodeName() {
        return "audit_router";
    }

    @Override
    public ErrorStrategy errorStrategy() { return ErrorStrategy.FAIL; }

    @Override
    public NovelAgentState apply(NovelAgentState state) throws Exception {
        NovelAgentState.AuditResult audit = state.getAuditResult();

        if (audit == null) {
            return pass(state);
        }

        if (validationConfig.isUseGradedAudit()) {
            return gradedRoute(state, audit);
        } else {
            return legacyRoute(state, audit);
        }
    }

    private NovelAgentState gradedRoute(NovelAgentState state, NovelAgentState.AuditResult audit) {
        GradedAuditCalculator.GradedResult graded = gradedAuditCalculator.calculate(audit);

        NovelAgentState.AuditResult updatedAudit = audit.toBuilder()
                .weightedScore(graded.getWeightedScore())
                .dimensionResults(graded.getDimensions())
                .fatalFailures(graded.getFatalFailures())
                .warnings(graded.getWarnings())
                .allPassed(graded.isAllPassed())
                .build();
        state = state.toBuilder().auditResult(updatedAudit).build();

        if (!graded.getFatalFailures().isEmpty()) {
            if (state.getPlanRetryCount() < MAX_PLAN_RETRIES) {
                log.info("[audit_router] FATAL 失败: {}，决策: REPLAN", graded.getFatalFailures());
                return replan(state);
            }
            log.warn("[audit_router] FATAL 失败且重规划次数达上限，决策: MANUAL_REVIEW");
            return manualReview(state);
        }

        if (!graded.isAllPassed()) {
            if (state.getRewriteCount() < MAX_REWRITES) {
                log.info("[audit_router] 加权分 {} < {}，决策: REWRITE",
                        graded.getWeightedScore(), validationConfig.getPassThreshold());
                return rewrite(state);
            }
            log.warn("[audit_router] 加权分 {} 未达标且重写次数达上限({})，决策: MANUAL_REVIEW",
                    graded.getWeightedScore(), state.getRewriteCount());
            return manualReview(state);
        }

        log.info("[audit_router] 通过（加权分={}），决策: PASS", graded.getWeightedScore());
        return pass(state);
    }

    private NovelAgentState legacyRoute(NovelAgentState state, NovelAgentState.AuditResult audit) {
        if (state.getRewriteCount() >= MAX_REWRITES && !audit.isAllPassed()) {
            log.warn("[audit_router] 重写次数达上限({})，标记MANUAL_REVIEW", state.getRewriteCount());
            return manualReview(state);
        }

        if (state.getPreWriteValidation() != null
                && !state.getPreWriteValidation().isPassed()
                && state.getPlanRetryCount() < MAX_PLAN_RETRIES) {
            log.info("[audit_router] 写前校验未通过，决策: REPLAN");
            return replan(state);
        }

        if (!audit.isAllPassed()) {
            if (!audit.isOocPassed() || !audit.isSettingPassed()) {
                if (state.getPlanRetryCount() < MAX_PLAN_RETRIES) {
                    log.info("[audit_router] OOC/设定校验未通过，决策: REPLAN");
                    return replan(state);
                }
            }

            if (state.getRewriteCount() < MAX_REWRITES) {
                log.info("[audit_router] 审校未通过(质量{})，决策: REWRITE", audit.getQualityScore());
                return rewrite(state);
            }
        }

        if (audit.getQualityScore() < MIN_QUALITY_SCORE && state.getRewriteCount() < MAX_REWRITES) {
            log.info("[audit_router] 质量分过低({})，决策: REWRITE", audit.getQualityScore());
            return rewrite(state);
        }

        log.info("[audit_router] 全部通过，决策: PASS");
        return pass(state);
    }

    private NovelAgentState pass(NovelAgentState state) {
        return state.toBuilder()
                .auditDecision("PASS")
                .currentPhase(NovelAgentState.GenerationPhase.AUDIT_ROUTER.name())
                .build();
    }

    private NovelAgentState replan(NovelAgentState state) {
        // REPLAN 会退回 outline，但审校意见仍要写进 pendingRevisionNotes：
        // 意见存在 state 里，WritingNode 直接从 state 读并注入 prompt，OutlineNode 只是
        // 「路过」不消费。默认 ValidationConfig 里 ooc/logic/setting 是 FATAL → 走 REPLAN
        // 而非 REWRITE，这是最常见的审校失败路径，不写意见反馈链就断在默认配置下。
        List<String> notes = buildAuditNotes(state.getAuditResult());
        log.info("[audit_router] REPLAN 写入待修订问题 {} 条，供下一轮写作消费", notes.size());
        return state.toBuilder()
                .auditDecision("REPLAN")
                .planRetryCount(state.getPlanRetryCount() + 1)
                .iterationCount(state.getIterationCount() + 1)
                .currentPhase(NovelAgentState.GenerationPhase.AUDIT_ROUTER.name())
                .pendingRevisionNotes(StateHelper.mergeRevisionNotes(
                        state.getPendingRevisionNotes(), notes))
                .build();
    }

    private NovelAgentState rewrite(NovelAgentState state) {
        // 审校意见必须进入下一轮写作的 prompt，否则「审校→改写」这条链是断的
        List<String> notes = buildAuditNotes(state.getAuditResult());
        log.info("[audit_router] 写入待修订问题 {} 条，供下一轮写作消费", notes.size());
        return state.toBuilder()
                .auditDecision("REWRITE")
                .rewriteCount(state.getRewriteCount() + 1)
                .iterationCount(state.getIterationCount() + 1)
                .currentPhase(NovelAgentState.GenerationPhase.AUDIT_ROUTER.name())
                .pendingRevisionNotes(StateHelper.mergeRevisionNotes(
                        state.getPendingRevisionNotes(), notes))
                .build();
    }

    /**
     * 把审计结论拼成可执行的中文修订意见。
     *
     * <p>顺序：致命失败 → 未通过维度的明细 → 伏笔落地问题 → 修改建议 → 总体评价。
     * 只取有值的条目，空值直接跳过，避免把空串灌进 prompt。
     */
    private List<String> buildAuditNotes(NovelAgentState.AuditResult audit) {
        List<String> notes = new ArrayList<>();
        if (audit == null) {
            return notes;
        }

        // 1. 致命失败（加权评分里的 FATAL 维度）
        if (audit.getFatalFailures() != null) {
            for (String fatal : audit.getFatalFailures()) {
                if (fatal == null || fatal.isBlank()) {
                    continue;
                }
                String label = DIMENSION_LABELS.getOrDefault(fatal, fatal);
                String detail = dimensionDetail(fatal, audit);
                String content = (detail == null || detail.isBlank())
                        ? label + "维度未通过"
                        : detail.trim();
                notes.add(String.format("审校未通过[致命·%s]：%s —— 请针对性修改本段后重写",
                        label, content));
            }
        }

        // 2. 五重校验中未通过维度的明细
        addDimensionNote(notes, "人设一致性", !audit.isOocPassed(), audit.getOocDetail());
        addDimensionNote(notes, "逻辑", !audit.isLogicPassed(), audit.getLogicDetail());
        addDimensionNote(notes, "伏笔", !audit.isCluePassed(), audit.getClueDetail());
        addDimensionNote(notes, "设定", !audit.isSettingPassed(), audit.getSettingDetail());
        addDimensionNote(notes, "节奏", !audit.isRhythmPassed(), audit.getRhythmDetail());

        // 3. 伏笔落地问题
        if (audit.getClueLandingIssues() != null) {
            for (String issue : audit.getClueLandingIssues()) {
                if (issue == null || issue.isBlank()) {
                    continue;
                }
                notes.add(String.format("审校未通过[伏笔落地]：%s —— 请针对性修改本段后重写",
                        issue.trim()));
            }
        }

        // 4. 审校给出的修改建议
        if (audit.getSuggestions() != null) {
            for (String suggestion : audit.getSuggestions()) {
                if (suggestion == null || suggestion.isBlank()) {
                    continue;
                }
                notes.add(String.format("审校建议：%s —— 请按此修改本段后重写", suggestion.trim()));
            }
        }

        // 5. 总体评价
        if (audit.getOverallAssessment() != null && !audit.getOverallAssessment().isBlank()) {
            notes.add(String.format("审校总体意见：%s —— 请针对性修改本段后重写",
                    audit.getOverallAssessment().trim()));
        }

        return notes;
    }

    private void addDimensionNote(List<String> notes, String label, boolean failed, String detail) {
        if (!failed) {
            return;
        }
        String content = (detail == null || detail.isBlank()) ? "该项校验未通过" : detail.trim();
        notes.add(String.format("审校未通过[%s]：%s —— 请针对性修改本段后重写", label, content));
    }

    /** 取某个维度对应的明细文本（致命失败那条要带上细节才好改）。 */
    private String dimensionDetail(String dimension, NovelAgentState.AuditResult audit) {
        if (dimension == null) {
            return null;
        }
        return switch (dimension) {
            case "ooc" -> audit.getOocDetail();
            case "logic" -> audit.getLogicDetail();
            case "clue" -> audit.getClueDetail();
            case "setting" -> audit.getSettingDetail();
            case "rhythm" -> audit.getRhythmDetail();
            case "quality" -> audit.getQualityAssessment();
            case "clueLanding" -> audit.getClueLandingIssues() == null
                    ? null : String.join("；", audit.getClueLandingIssues());
            default -> null;
        };
    }

    private NovelAgentState manualReview(NovelAgentState state) {
        return state.toBuilder()
                .auditDecision("MANUAL_REVIEW")
                .currentPhase(NovelAgentState.GenerationPhase.AUDIT_ROUTER.name())
                .build();
    }
}
