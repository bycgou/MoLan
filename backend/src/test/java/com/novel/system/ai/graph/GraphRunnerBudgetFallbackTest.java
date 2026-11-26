package com.novel.system.ai.graph;

import com.novel.system.service.chapter.MultiAgentNovelService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 重试预算耗尽时的降级行为。
 *
 * <p>两条硬约束：
 * <ul>
 *   <li>降级必须留痕：{@code state.getErrors()} 里要有一条「预算耗尽」的错误，
 *       否则调用方（{@code AgentExecutionResult.errors}）不知道这一章是被强制放行的。</li>
 *   <li>降级不能跳过一致性门：writing 预算耗尽后仍要走 fact_lock_violation。</li>
 * </ul>
 */
class GraphRunnerBudgetFallbackTest {

    /** 只保留与「writing 被反复重入」相关的边，结构对齐生产图定义。 */
    private static GraphDefinition definition() {
        return GraphDefinition.builder()
                .edge("arc_check", "writing")
                .edge("writing", "deslop_check")
                .conditionalEdge("deslop_check", "writing",
                        s -> "REWRITE".equals(s.getDeslopDecision()))
                .edge("deslop_check", "self_review")
                .edge("fact_lock_violation", "summary")
                .retryPolicy("writing", 3, "fact_lock_violation")
                .build();
    }

    private static class RecordingNode implements NovelGraphNode {
        private final String name;
        private final List<String> visited;
        private final Function<NovelAgentState, NovelAgentState> body;

        RecordingNode(String name, List<String> visited,
                      Function<NovelAgentState, NovelAgentState> body) {
            this.name = name;
            this.visited = visited;
            this.body = body;
        }

        @Override
        public String nodeName() {
            return name;
        }

        @Override
        public NovelAgentState apply(NovelAgentState state) {
            visited.add(name);
            return body.apply(state);
        }
    }

    /** 造一个带节点名的 mock（GraphRunner.register 需要 nodeName 非空）。 */
    private static <T extends NovelGraphNode> T mockNode(Class<T> type, String name) {
        T node = mock(type);
        when(node.nodeName()).thenReturn(name);
        return node;
    }

    @Test
    void exhaustedWritingBudgetLeavesVisibleErrorAndStillRunsFactLock() {
        GraphDefinition def = definition();
        GraphRunner runner = new GraphRunner(def);
        runner.setNodeExecutor(new NodeExecutor(null, null, def));

        List<String> visited = new ArrayList<>();
        runner.register(new RecordingNode("arc_check", visited, s -> s));
        runner.register(new RecordingNode("writing", visited, s -> s.toBuilder()
                .draftContent(NovelAgentState.DraftContent.builder()
                        .content("正文").wordCount(2).build())
                .build()));
        // 门禁一直要求重写 → writing 被反复重入直到预算耗尽
        runner.register(new RecordingNode("deslop_check", visited, s -> s.toBuilder()
                .deslopDecision("REWRITE")
                .build()));
        runner.register(new RecordingNode("self_review", visited, s -> s));
        runner.register(new RecordingNode("fact_lock_violation", visited, s -> s.toBuilder()
                .currentPhase("fact_lock_checked")
                .build()));
        runner.register(new RecordingNode("summary", visited, s -> s.toBuilder()
                .finalResult(NovelAgentState.FinalResult.builder().finalContent("正文").build())
                .build()));

        NovelAgentState result = runner.execute(
                NovelAgentState.builder().novelId("n1").chapterNum(1).build(), Mode.FULL);

        // (a) 预算耗尽必须留下可见的错误
        assertTrue(result.getErrors().stream().anyMatch(e ->
                        "writing".equals(e.nodeName()) && e.message().contains("预算耗尽")),
                "预算耗尽必须在 errors 里留痕: " + result.getErrors());
        assertTrue(result.getErrors().stream().anyMatch(e ->
                        "writing".equals(e.nodeName()) && e.message().contains("fact_lock_violation")),
                "错误应指明放行到 fact_lock_violation: " + result.getErrors());

        // (b) 到达 summary 之前必须先过 fact_lock_violation 这道一致性门
        int factLockIndex = visited.indexOf("fact_lock_violation");
        int summaryIndex = visited.indexOf("summary");
        assertTrue(factLockIndex >= 0, "预算耗尽后仍应经过 fact_lock_violation: " + visited);
        assertTrue(summaryIndex > factLockIndex,
                "fact_lock_violation 必须先于 summary: " + visited);

        // 预算语义：writing 被进入 3 次就不再进入
        assertEquals(3, java.util.Collections.frequency(visited, "writing"));
    }

    /**
     * 生产图定义里 writing 的兜底必须是 fact_lock_violation，
     * 不能是 summary（那会绕过唯一的一致性校验）。
     */
    @Test
    void productionGraphFallsBackToFactLockViolation() throws Exception {
        MultiAgentNovelService service = new MultiAgentNovelService(
                mockNode(ArcOrchestrationNode.class, "arc_check"),
                mockNode(OutlineNode.class, "outline"),
                mockNode(CluePlanningNode.class, "clue_planning"),
                mockNode(CharacterPlanningNode.class, "character_planning"),
                mockNode(WorldValidatorNode.class, "world_validate"),
                mockNode(IdeaSelectionNode.class, "idea_select"),
                mockNode(WritingNode.class, "writing"),
                mockNode(SelfReviewNode.class, "self_review"),
                mockNode(DeslopCheckNode.class, "deslop_check"),
                mockNode(ContinuityValidationNode.class, "continuity_validation"),
                mockNode(ValidationNode.class, "validation"),
                mockNode(QualityScoringNode.class, "quality_scoring"),
                mockNode(AuditRouterNode.class, "audit_router"),
                mockNode(SummaryNode.class, "summary"),
                mockNode(PostProcessNode.class, "post_process"),
                mock(AuthorAgent.class),
                mockNode(FactLockViolationNode.class, "fact_lock_violation"),
                null);

        Field runnerField = MultiAgentNovelService.class.getDeclaredField("runner");
        runnerField.setAccessible(true);
        Object runner = runnerField.get(service);
        assertNotNull(runner);

        Field definitionField = GraphRunner.class.getDeclaredField("definition");
        definitionField.setAccessible(true);
        GraphDefinition definition = (GraphDefinition) definitionField.get(runner);

        assertEquals("fact_lock_violation", definition.getFallbackNode("writing"),
                "writing 预算耗尽时必须仍走一致性校验");
        assertEquals(3, definition.getMaxRetries("writing"));
        // outline 预算耗尽时本来就没有章纲和正文，summary 是合理的「无正文」出口
        assertEquals("summary", definition.getFallbackNode("outline"));
        // audit_router 与 writing 同缺陷：兜底到 summary 会绕过 fact_lock_violation 一致性门
        assertEquals("fact_lock_violation", definition.getFallbackNode("audit_router"),
                "audit_router 预算耗尽时仍须走一致性校验");
    }
}
