package com.novel.system.ai.graph;

import java.util.ArrayList;
import java.util.List;

/**
 * 扇出合并策略。
 * 合并语义：多个分支写同一字段时，后遍历的覆盖先遍历的。
 * 大多数场景下各分支写不同字段，不会冲突。
 */
@FunctionalInterface
public interface MergeStrategy {

    NovelAgentState merge(NovelAgentState base, List<NovelAgentState> results);

    /**
     * 默认策略：累积所有分支的 executionTrace、errors、llmCalls。
     */
    static MergeStrategy defaultStrategy() {
        return (base, results) -> {
            NovelAgentState.NovelAgentStateBuilder builder = base.toBuilder();

            List<NodeExecutionRecord> allTraces = new ArrayList<>(base.getExecutionTrace());
            List<StructuredError> allErrors = new ArrayList<>(base.getErrors());
            List<LlmCallRecord> allLlmCalls = new ArrayList<>(base.getLlmCalls());

            for (NovelAgentState result : results) {
                allTraces.addAll(result.getExecutionTrace());
                allErrors.addAll(result.getErrors());
                allLlmCalls.addAll(result.getLlmCalls());
            }

            return builder
                    .executionTrace(allTraces)
                    .errors(allErrors)
                    .llmCalls(allLlmCalls)
                    .build();
        };
    }

    /**
     * 章纲合并策略：在默认策略基础上，合并各分支的 chapterPlan 字段。
     * clue_planning 分支写 clueActions，character_planning 分支写 characterActions。
     */
    static MergeStrategy chapterPlanMerge() {
        return (base, results) -> {
            NovelAgentState merged = defaultStrategy().merge(base, results);

            NovelAgentState.ChapterPlan.ChapterPlanBuilder planBuilder =
                    merged.getChapterPlan() != null
                            ? merged.getChapterPlan().toBuilder()
                            : NovelAgentState.ChapterPlan.builder();

            for (NovelAgentState result : results) {
                if (result.getChapterPlan() == null) {
                    continue;
                }
                if (result.getChapterPlan().getClueActions() != null) {
                    planBuilder.clueActions(result.getChapterPlan().getClueActions());
                }
                if (result.getChapterPlan().getCharacterActions() != null) {
                    planBuilder.characterActions(result.getChapterPlan().getCharacterActions());
                }
            }

            return merged.toBuilder().chapterPlan(planBuilder.build()).build();
        };
    }
}
