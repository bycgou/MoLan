package com.novel.system.ai.graph;

import com.novel.system.ai.chain.ChapterGenerationChain;
import com.novel.system.mapper.NovelBaseMapper;
import com.novel.system.service.chapter.ContextBuilderService;
import com.novel.system.service.chapter.TokenBudgetService;
import com.novel.system.service.llm.LlmConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 门禁 findings 必须真的进到重写时给模型的上下文里，否则门禁只是在计数。
 */
@ExtendWith(MockitoExtension.class)
class WritingNodeRevisionInjectionTest {

    @Mock private ChapterGenerationChain chain;
    @Mock private ContextBuilderService contextBuilderService;
    @Mock private TokenBudgetService tokenBudgetService;
    @Mock private NovelBaseMapper novelBaseMapper;
    @Mock private LlmConfigService llmConfigService;

    private WritingNode node;

    @BeforeEach
    void setUp() {
        node = buildNode(false);
    }

    private WritingNode buildNode(boolean decontextualizePlan) {
        TokenBudgetService.TokenBudget budget = new TokenBudgetService.TokenBudget();
        budget.setTotalTokens(8192);
        budget.getAllocation().put("characterState", 2000);
        budget.getAllocation().put("clueBook", 1000);
        when(tokenBudgetService.calculateBudget(anyInt())).thenReturn(budget);
        when(tokenBudgetService.truncateToBudget(anyString(), anyInt()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(chain.executeWritingChain(anyString(), anyInt(), anyString(), anyString(),
                any(), any())).thenReturn("正文");
        return new WritingNode(chain, contextBuilderService, tokenBudgetService,
                novelBaseMapper, llmConfigService, decontextualizePlan);
    }

    private static NovelAgentState baseState() {
        return NovelAgentState.builder()
                .novelId("n1")
                .chapterNum(1)
                .writingContext("基础上下文")
                .chapterPlan(NovelAgentState.ChapterPlan.builder()
                        .title("T")
                        .summary("概要")
                        .characterActions(List.of(NovelAgentState.CharacterAction.builder()
                                .characterName("张三")
                                .role("PROTAGONIST")
                                .goal("找到玉佩")
                                .emotionalState("警惕")
                                .keyAction("与李四对峙")
                                .interactions(List.of("李四"))
                                .build()))
                        .build())
                .build();
    }

    private String capturedContext() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(chain).executeWritingChain(anyString(), anyInt(), anyString(),
                captor.capture(), any(), any());
        return captor.getValue();
    }

    @Test
    void injectsPendingRevisionNotes() throws Exception {
        NovelAgentState state = baseState().toBuilder()
                .pendingRevisionNotes(List.of("- 「trailer-ending」章末总结体｜命中：这一切，｜改法：用动作收束"))
                .build();

        node.apply(state);

        String context = capturedContext();
        assertTrue(context.contains("待修订问题"), context);
        assertTrue(context.contains("trailer-ending"), context);
        assertTrue(context.contains("命中：这一切，"), context);
    }

    @Test
    void fallsBackToDraftRevisionNotes() throws Exception {
        NovelAgentState state = baseState().toBuilder()
                .draftContent(NovelAgentState.DraftContent.builder()
                        .content("旧稿")
                        .revisionNotes(List.of("- 自审意见：补一个动作"))
                        .build())
                .build();

        node.apply(state);

        String context = capturedContext();
        assertTrue(context.contains("自审意见：补一个动作"), context);
    }

    @Test
    void clearsPendingRevisionNotesAfterConsuming() throws Exception {
        NovelAgentState state = baseState().toBuilder()
                .pendingRevisionNotes(List.of("- 门禁意见"))
                .build();

        NovelAgentState result = node.apply(state);

        assertNull(result.getPendingRevisionNotes(), "建议消费一次即失效");
    }

    @Test
    void omitsRevisionSectionWhenNothingPending() throws Exception {
        node.apply(baseState());

        String context = capturedContext();
        assertFalse(context.contains("待修订问题"), context);
    }

    @Test
    void keepsTaggedPlanByDefault() throws Exception {
        node.apply(baseState());

        String context = capturedContext();
        assertTrue(context.contains("角色定位：PROTAGONIST"), context);
        assertTrue(context.contains("本章目标：找到玉佩"), context);
    }

    @Test
    void decontextualizedPlanUsesNarrativeLines() throws Exception {
        WritingNode narrative = buildNode(true);
        narrative.apply(baseState());

        String context = capturedContext();
        assertFalse(context.contains("角色定位："), context);
        assertFalse(context.contains("本章目标："), context);
        assertTrue(context.contains("张三"), context);
        assertTrue(context.contains("这一章要做的是找到玉佩"), context);
    }

    @Test
    void decontextualizedPlanKeepsClueActionsTagged() throws Exception {
        WritingNode narrative = buildNode(true);
        NovelAgentState state = baseState().toBuilder()
                .chapterPlan(baseState().getChapterPlan().toBuilder()
                        .clueActions(List.of(NovelAgentState.ClueAction.builder()
                                .clueId("c1")
                                .action("PLANT")
                                .description("玉佩出现")
                                .build()))
                        .build())
                .build();

        narrative.apply(state);

        String context = capturedContext();
        assertTrue(context.contains("[PLANT] c1: 玉佩出现"), context);
    }
}
