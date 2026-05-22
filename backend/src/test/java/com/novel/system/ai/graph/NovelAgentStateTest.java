package com.novel.system.ai.graph;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NovelAgentStateTest {

    @Test
    void builderCreatesImmutableCopy() {
        NovelAgentState original = NovelAgentState.builder()
                .novelId("novel-1")
                .chapterNum(1)
                .build();

        NovelAgentState modified = original.toBuilder()
                .chapterNum(2)
                .build();

        assertEquals(1, original.getChapterNum());
        assertEquals(2, modified.getChapterNum());
        assertEquals("novel-1", modified.getNovelId());
    }

    @Test
    void defaultValuesInitialized() {
        NovelAgentState state = NovelAgentState.builder().build();
        assertEquals(0, state.getIterationCount());
        assertEquals(0, state.getPlanRetryCount());
        assertEquals(0, state.getWriteRetryCount());
        assertNotNull(state.getExecutionTrace());
        assertNotNull(state.getErrors());
        assertTrue(state.getExecutionTrace().isEmpty());
    }

    @Test
    void nestedDtosSupportToBuilder() {
        NovelAgentState.ChapterPlan plan = NovelAgentState.ChapterPlan.builder()
                .title("测试标题")
                .summary("测试概要")
                .build();

        NovelAgentState.ChapterPlan modified = plan.toBuilder()
                .hook("新钩子")
                .build();

        assertEquals("测试标题", modified.getTitle());
        assertEquals("新钩子", modified.getHook());
        assertNull(plan.getHook());
    }

    @Test
    void severityEnumValues() {
        assertEquals(3, NovelAgentState.Severity.values().length);
        assertEquals("LOW", NovelAgentState.Severity.LOW.name());
        assertEquals("HIGH", NovelAgentState.Severity.HIGH.name());
    }
}
