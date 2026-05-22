package com.novel.system.ai.graph;

import com.novel.system.ai.chain.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 五重并行校验节点 — 完整映射5个维度
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ValidationNode implements NovelGraphNode {

    private final ValidationService validationService;

    @Override
    public String nodeName() { return "validation"; }

    @Override
    public NovelAgentState apply(NovelAgentState state) throws Exception {
        log.info("[{}] 五重并行校验", nodeName());

        NovelAgentState.DraftContent draft = state.getDraftContent();
        NovelAgentState.ChapterPlan plan = state.getChapterPlan();

        ValidationRequest request = new ValidationRequest();
        request.setNovelId(state.getNovelId());
        request.setChapterNum(state.getChapterNum());
        request.setChapterContent(draft.getContent());
        request.setChapterOutline(plan.getSummary());

        ValidationResult result = validationService.validateAll(request);

        NovelAgentState.AuditResult.AuditResultBuilder auditBuilder = NovelAgentState.AuditResult.builder()
                .allPassed(result.getPassed());

        if (result.getOocResult() != null) {
            auditBuilder.oocPassed(result.getOocResult().isPassed())
                    .oocScore(result.getOocResult().getTotalScore())
                    .oocDetail(String.format("语言风格:%d, 行为动机:%d, 知识水平:%d, 成长适应:%d",
                            result.getOocResult().getLanguageStyleScore(),
                            result.getOocResult().getBehaviorMotivationScore(),
                            result.getOocResult().getKnowledgeLevelScore(),
                            result.getOocResult().getGrowthAdaptationScore()));
        }
        if (result.getLogicResult() != null) {
            auditBuilder.logicPassed(result.getLogicResult().isPassed())
                    .logicDetail(String.format("因果链:%s, 一致性:%s, 冲突:%s",
                            result.getLogicResult().getCausalChainStatus(),
                            result.getLogicResult().getConsistencyStatus(),
                            result.getLogicResult().getConflictDescription()));
        }
        if (result.getClueResult() != null) {
            auditBuilder.cluePassed(result.getClueResult().isPassed())
                    .clueDetail(String.format("埋设:%d, 回收:%d, 遗漏:%s, 不合理:%s",
                            result.getClueResult().getBuriedCount(),
                            result.getClueResult().getRecycledCount(),
                            result.getClueResult().getMissingClues(),
                            result.getClueResult().getUnnaturalRecycle()));
        }
        if (result.getSettingResult() != null) {
            auditBuilder.settingPassed(result.getSettingResult().isPassed())
                    .settingDetail(String.format("世界规则:%s, 时钟:%s, 冲突:%s",
                            result.getSettingResult().getWorldRuleStatus(),
                            result.getSettingResult().getWorldClockStatus(),
                            result.getSettingResult().getConflicts()));
        }
        if (result.getRhythmResult() != null) {
            auditBuilder.rhythmPassed(result.getRhythmResult().isPassed())
                    .rhythmDetail(String.format("预期:%s, 实际:%s",
                            result.getRhythmResult().getExpectedRhythm(),
                            result.getRhythmResult().getActualRhythm()));
        }

        List<String> suggestions = new ArrayList<>();
        if (!result.getPassed() && result.getSummary() != null) {
            suggestions.add(result.getSummary());
        }
        auditBuilder.suggestions(suggestions);

        NovelAgentState updated = state.toBuilder()
                .auditResult(auditBuilder.build())
                .currentPhase(NovelAgentState.GenerationPhase.VALIDATION.name())
                .build();

        return StateHelper.appendTrace(updated, nodeName());
    }
}
