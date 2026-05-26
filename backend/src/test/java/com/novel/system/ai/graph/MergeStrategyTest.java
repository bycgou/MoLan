package com.novel.system.ai.graph;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MergeStrategyTest {

    @Test
    void defaultStrategy_accumulatesTracesFromAllBranches() {
        NovelAgentState base = NovelAgentState.builder()
                .executionTrace(new ArrayList<>(List.of(
                        NodeExecutionRecord.success("base_node", Mode.FULL,
                                java.time.Instant.now(), java.time.Instant.now(),
                                null, java.util.Map.of()))))
                .errors(new ArrayList<>())
                .llmCalls(new ArrayList<>())
                .build();

        NovelAgentState branch1 = base.toBuilder()
                .executionTrace(new ArrayList<>(List.of(
                        NodeExecutionRecord.success("branch1", Mode.FULL,
                                java.time.Instant.now(), java.time.Instant.now(),
                                null, java.util.Map.of()))))
                .build();

        NovelAgentState branch2 = base.toBuilder()
                .executionTrace(new ArrayList<>(List.of(
                        NodeExecutionRecord.success("branch2", Mode.FULL,
                                java.time.Instant.now(), java.time.Instant.now(),
                                null, java.util.Map.of()))))
                .build();

        MergeStrategy strategy = MergeStrategy.defaultStrategy();
        NovelAgentState merged = strategy.merge(base, List.of(branch1, branch2));

        assertEquals(3, merged.getExecutionTrace().size());
        assertEquals("base_node", merged.getExecutionTrace().get(0).nodeName());
        assertEquals("branch1", merged.getExecutionTrace().get(1).nodeName());
        assertEquals("branch2", merged.getExecutionTrace().get(2).nodeName());
    }

    @Test
    void chapterPlanMerge_combinesClueAndCharacterActions() {
        NovelAgentState base = NovelAgentState.builder()
                .chapterPlan(NovelAgentState.ChapterPlan.builder()
                        .summary("test").build())
                .executionTrace(new ArrayList<>())
                .errors(new ArrayList<>())
                .llmCalls(new ArrayList<>())
                .build();

        NovelAgentState clueBranch = base.toBuilder()
                .chapterPlan(NovelAgentState.ChapterPlan.builder()
                        .clueActions(List.of(
                                NovelAgentState.ClueAction.builder()
                                        .clueId("c1").action("PLANT").build()))
                        .build())
                .build();

        NovelAgentState charBranch = base.toBuilder()
                .chapterPlan(NovelAgentState.ChapterPlan.builder()
                        .characterActions(List.of(
                                NovelAgentState.CharacterAction.builder()
                                        .characterName("Alice").build()))
                        .build())
                .build();

        MergeStrategy strategy = MergeStrategy.chapterPlanMerge();
        NovelAgentState merged = strategy.merge(base, List.of(clueBranch, charBranch));

        assertNotNull(merged.getChapterPlan().getClueActions());
        assertEquals(1, merged.getChapterPlan().getClueActions().size());
        assertEquals("c1", merged.getChapterPlan().getClueActions().get(0).getClueId());

        assertNotNull(merged.getChapterPlan().getCharacterActions());
        assertEquals(1, merged.getChapterPlan().getCharacterActions().size());
        assertEquals("Alice", merged.getChapterPlan().getCharacterActions().get(0).getCharacterName());
    }

    @Test
    void chapterPlanMerge_handlesNullChapterPlan() {
        NovelAgentState base = NovelAgentState.builder()
                .chapterPlan(NovelAgentState.ChapterPlan.builder().summary("test").build())
                .executionTrace(new ArrayList<>())
                .errors(new ArrayList<>())
                .llmCalls(new ArrayList<>())
                .build();

        NovelAgentState failedBranch = base.toBuilder()
                .chapterPlan(null)
                .build();

        MergeStrategy strategy = MergeStrategy.chapterPlanMerge();
        NovelAgentState merged = strategy.merge(base, List.of(failedBranch));

        assertNotNull(merged.getChapterPlan());
        assertEquals("test", merged.getChapterPlan().getSummary());
    }
}
