package com.novel.system.ai.graph;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NodeExecutorTest {

    @Mock private AuthorAgent authorAgent;
    @Mock private StateCheckpointService checkpointService;
    @Mock private GraphDefinition definition;
    @Mock private NovelGraphNode testNode;

    private NodeExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new NodeExecutor(authorAgent, checkpointService, definition);
        when(testNode.nodeName()).thenReturn("test_node");
    }

    @Test
    void executesNodeAndReturnsResult() throws Exception {
        NovelAgentState state = NovelAgentState.builder().build();
        NovelAgentState expected = state.toBuilder().iterationCount(1).build();
        when(testNode.apply(any())).thenReturn(expected);
        when(definition.isCheckpoint("test_node")).thenReturn(false);

        NovelAgentState result = executor.execute(testNode, state, Mode.FULL);

        assertEquals(1, result.getIterationCount());
        verify(testNode).apply(any());
    }

    @Test
    void savesCheckpointForCheckpointNodes() throws Exception {
        NovelAgentState state = NovelAgentState.builder().build();
        when(testNode.apply(any())).thenReturn(state);
        when(testNode.nodeName()).thenReturn("outline");
        when(definition.isCheckpoint("outline")).thenReturn(true);
        when(authorAgent.beforeNode(any(), any())).thenReturn(
                AuthorDirective.builder().action(AuthorDirective.Action.APPROVE).build());

        executor.execute(testNode, state, Mode.FULL);

        verify(checkpointService).saveCheckpoint(eq("outline"), any(NovelAgentState.class));
    }

    @Test
    void skipsNodeWhenShouldSkipReturnsTrue() throws Exception {
        NovelAgentState state = NovelAgentState.builder().build();
        when(testNode.shouldSkip(any())).thenReturn(true);
        when(definition.isCheckpoint("test_node")).thenReturn(false);

        NovelAgentState result = executor.execute(testNode, state, Mode.FULL);

        verify(testNode, never()).apply(any());
        assertFalse(result.getExecutionTrace().isEmpty());
    }
}
