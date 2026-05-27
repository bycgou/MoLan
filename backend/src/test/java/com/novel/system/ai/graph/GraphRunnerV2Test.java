package com.novel.system.ai.graph;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GraphRunnerV2Test {

    @Mock private NovelGraphNode arcCheckNode;
    @Mock private NovelGraphNode outlineNode;
    @Mock private NovelGraphNode writingNode;
    @Mock private NovelGraphNode summaryNode;
    @Mock private NovelGraphNode postProcessNode;
    @Mock private AuthorAgent authorAgent;

    private GraphDefinition definition;

    @BeforeEach
    void setUp() {
        when(arcCheckNode.nodeName()).thenReturn("arc_check");
        when(outlineNode.nodeName()).thenReturn("outline");
        when(writingNode.nodeName()).thenReturn("writing");
        when(summaryNode.nodeName()).thenReturn("summary");
        when(postProcessNode.nodeName()).thenReturn("post_process");

        definition = GraphDefinition.builder()
                .edge("arc_check", "outline")
                .edge("outline", "writing")
                .edge("writing", "summary")
                .edge("summary", "post_process")
                .checkpoint("outline")
                .checkpoint("writing")
                .retryPolicy("writing", 2, "summary")
                .build();
    }

    @Test
    void shouldExecuteLinearPath() throws Exception {
        NovelAgentState initialState = NovelAgentState.builder().build();

        when(arcCheckNode.apply(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outlineNode.apply(any())).thenAnswer(inv -> inv.getArgument(0));
        when(writingNode.apply(any())).thenAnswer(inv -> inv.getArgument(0));
        when(summaryNode.apply(any())).thenAnswer(inv -> {
            NovelAgentState s = inv.getArgument(0);
            return s.toBuilder().finalResult(
                    NovelAgentState.FinalResult.builder().summary("done").build()).build();
        });
        when(postProcessNode.apply(any())).thenAnswer(inv -> inv.getArgument(0));
        when(authorAgent.beforeNode(any(), any())).thenReturn(
                AuthorDirective.builder().action(AuthorDirective.Action.APPROVE).build());

        GraphRunner runner = new GraphRunner(definition);
        runner.register(arcCheckNode);
        runner.register(outlineNode);
        runner.register(writingNode);
        runner.register(summaryNode);
        runner.register(postProcessNode);
        runner.setAuthorAgent(authorAgent);

        NovelAgentState result = runner.execute(initialState, Mode.FULL);

        assertNotNull(result);
        verify(arcCheckNode).apply(any());
        verify(outlineNode).apply(any());
        verify(writingNode).apply(any());
        verify(summaryNode).apply(any());
        verify(postProcessNode).apply(any());
        // AuthorAgent called at checkpoints (outline, writing)
        verify(authorAgent, atLeast(2)).beforeNode(any(), any());
    }

    @Test
    void shouldExecuteInFastMode() throws Exception {
        GraphDefinition fastDef = GraphDefinition.builder()
                .edge("arc_check", "outline")
                .edge("outline", "writing", Mode.FAST)
                .edge("writing", "summary")
                .edge("summary", "post_process")
                .build();

        when(arcCheckNode.apply(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outlineNode.apply(any())).thenAnswer(inv -> inv.getArgument(0));
        when(writingNode.apply(any())).thenAnswer(inv -> inv.getArgument(0));
        when(summaryNode.apply(any())).thenAnswer(inv -> inv.getArgument(0));
        when(postProcessNode.apply(any())).thenAnswer(inv -> inv.getArgument(0));

        GraphRunner runner = new GraphRunner(fastDef);
        runner.register(arcCheckNode);
        runner.register(outlineNode);
        runner.register(writingNode);
        runner.register(summaryNode);
        runner.register(postProcessNode);

        NovelAgentState result = runner.execute(NovelAgentState.builder().build(), Mode.FAST);

        assertNotNull(result);
        verify(arcCheckNode).apply(any());
        verify(outlineNode).apply(any());
        verify(writingNode).apply(any());
        verify(summaryNode).apply(any());
        verify(postProcessNode).apply(any());
        // No checkpoints, so authorAgent should not be called
        verify(authorAgent, never()).beforeNode(any(), any());
    }

    @Test
    void shouldHandleMissingNodeGracefully() throws Exception {
        // Don't register writingNode
        when(arcCheckNode.apply(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outlineNode.apply(any())).thenAnswer(inv -> inv.getArgument(0));
        when(summaryNode.apply(any())).thenAnswer(inv -> inv.getArgument(0));
        when(postProcessNode.apply(any())).thenAnswer(inv -> inv.getArgument(0));
        when(authorAgent.beforeNode(any(), any())).thenReturn(
                AuthorDirective.builder().action(AuthorDirective.Action.APPROVE).build());

        GraphRunner runner = new GraphRunner(definition);
        runner.register(arcCheckNode);
        runner.register(outlineNode);
        // writingNode not registered
        runner.register(summaryNode);
        runner.register(postProcessNode);
        runner.setAuthorAgent(authorAgent);

        NovelAgentState result = runner.execute(NovelAgentState.builder().build(), Mode.FULL);

        assertNotNull(result);
        // Should have errors for missing node
        assertFalse(result.getErrors().isEmpty());
    }

    @Test
    void shouldSkipNodesWhenShouldSkipReturnsTrue() throws Exception {
        when(arcCheckNode.apply(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outlineNode.apply(any())).thenAnswer(inv -> inv.getArgument(0));
        // writingNode should skip
        when(writingNode.shouldSkip(any())).thenReturn(true);
        when(writingNode.apply(any())).thenAnswer(inv -> inv.getArgument(0));
        when(summaryNode.apply(any())).thenAnswer(inv -> inv.getArgument(0));
        when(postProcessNode.apply(any())).thenAnswer(inv -> inv.getArgument(0));
        when(authorAgent.beforeNode(any(), any())).thenReturn(
                AuthorDirective.builder().action(AuthorDirective.Action.APPROVE).build());

        GraphRunner runner = new GraphRunner(definition);
        runner.register(arcCheckNode);
        runner.register(outlineNode);
        runner.register(writingNode);
        runner.register(summaryNode);
        runner.register(postProcessNode);
        runner.setAuthorAgent(authorAgent);

        NovelAgentState result = runner.execute(NovelAgentState.builder().build(), Mode.FULL);

        assertNotNull(result);
        // writingNode should be skipped, not executed
        verify(writingNode, never()).apply(any());
        verify(summaryNode).apply(any());
    }

    @Test
    void shouldTerminateAtMaxIterations() throws Exception {
        // Create a loop: outline -> writing -> outline (infinite loop)
        GraphDefinition loopDef = GraphDefinition.builder()
                .edge("arc_check", "outline")
                .conditionalEdge("outline", "writing", s -> true)
                .conditionalEdge("writing", "outline", s -> true)
                .edge("summary", "post_process")
                .build();

        when(arcCheckNode.apply(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outlineNode.apply(any())).thenAnswer(inv -> inv.getArgument(0));
        when(writingNode.apply(any())).thenAnswer(inv -> inv.getArgument(0));

        GraphRunner runner = new GraphRunner(loopDef);
        runner.register(arcCheckNode);
        runner.register(outlineNode);
        runner.register(writingNode);

        NovelAgentState result = runner.execute(NovelAgentState.builder().build(), Mode.FULL);

        assertNotNull(result);
        // Should have max iterations error
        assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.message().contains("Max iterations")));
    }
}
