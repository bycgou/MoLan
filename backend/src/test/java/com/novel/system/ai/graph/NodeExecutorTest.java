package com.novel.system.ai.graph;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

    /**
     * 失败也必须消耗节点预算：计数语义是「该节点被进入的次数」，
     * 否则持续抛异常的节点不会消耗图级预算，会被反复重入直到撞上 20 步上限。
     */
    @Test
    void failingNodeStillConsumesRetryBudget() throws Exception {
        NovelAgentState state = NovelAgentState.builder().build();
        when(testNode.apply(any())).thenThrow(new RuntimeException("节点内部异常"));
        when(testNode.errorStrategy()).thenReturn(ErrorStrategy.SKIP);

        NovelAgentState afterFirst = executor.execute(testNode, state, Mode.FULL);
        NovelAgentState afterSecond = executor.execute(testNode, afterFirst, Mode.FULL);

        assertEquals(1, StateHelper.getNodeRetryCount(afterFirst, "test_node"));
        assertEquals(2, StateHelper.getNodeRetryCount(afterSecond, "test_node"),
                "连续失败 2 次，节点计数应为 2");
        assertEquals(2, afterSecond.getErrors().size(), "每次失败都应留下一条错误");
    }

    /**
     * 计数在 apply 之前完成，并随 state 传给节点：
     * 节点内部（如路由保护）看到的状态已经包含本次进入。
     */
    @Test
    void retryCountIsVisibleInsideNodeExecution() throws Exception {
        NovelAgentState state = NovelAgentState.builder().build();
        when(testNode.errorStrategy()).thenReturn(ErrorStrategy.SKIP);

        ArgumentCaptor<NovelAgentState> captor = ArgumentCaptor.forClass(NovelAgentState.class);
        when(testNode.apply(captor.capture())).thenAnswer(inv -> {
            NovelAgentState s = inv.getArgument(0);
            throw new RuntimeException("节点内部异常");
        });

        executor.execute(testNode, state, Mode.FULL);

        assertEquals(1, StateHelper.getNodeRetryCount(captor.getValue(), "test_node"),
                "apply 收到的 state 里应已包含本次进入的计数");
    }

    /**
     * 节点自身的重试（errorStrategy=RETRY）仍算一次「进入」：
     * 内部重试不重复消耗图级预算，只有重新进入该节点才算。
     */
    @Test
    void internalRetriesCountAsSingleEntry() throws Exception {
        NovelAgentState state = NovelAgentState.builder().build();
        when(testNode.errorStrategy()).thenReturn(ErrorStrategy.RETRY);
        when(testNode.maxRetries()).thenReturn(2);
        when(testNode.apply(any())).thenThrow(new RuntimeException("节点内部异常"));

        NovelAgentState result = executor.execute(testNode, state, Mode.FULL);

        verify(testNode, times(3)).apply(any());
        assertEquals(1, StateHelper.getNodeRetryCount(result, "test_node"));
        assertEquals(1, result.getErrors().size());
    }

    /**
     * 节点返回 null 时必须兜住：否则整条图状态失效，后续节点全部拿到 null，
     * 第一个用状态的节点就会 NPE，报错还指不到真正的元凶。
     */
    @Test
    void nullResultFallsBackToPreviousStateAndIsRecorded() throws Exception {
        NovelAgentState state = NovelAgentState.builder().iterationCount(3).build();
        when(testNode.apply(any())).thenReturn(null);
        when(testNode.errorStrategy()).thenReturn(ErrorStrategy.SKIP);

        NovelAgentState result = executor.execute(testNode, state, Mode.FULL);

        assertNotNull(result, "节点返回 null 时不能把状态一起丢掉");
        assertEquals(3, result.getIterationCount(), "应保留进入节点前的状态");
        assertEquals(1, StateHelper.getNodeRetryCount(result, "test_node"),
                "节点没有产出也要消耗本次进入的预算");
        assertEquals(1, result.getErrors().size());
        assertTrue(result.getErrors().get(0).message().contains("null"),
                "错误信息应说明节点未产出状态: " + result.getErrors());
    }
}
