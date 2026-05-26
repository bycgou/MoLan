package com.novel.system.ai.graph;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GraphDefinitionTest {

    @Test
    void shouldResolveSimpleEdge() {
        GraphDefinition def = GraphDefinition.builder()
            .edge("a", "b")
            .edge("b", "c")
            .build();
        assertEquals("b", def.getNextNode("a", createState(), Mode.FULL));
        assertEquals("c", def.getNextNode("b", createState(), Mode.FULL));
        assertEquals(GraphDefinition.END, def.getNextNode("c", createState(), Mode.FULL));
    }

    @Test
    void shouldResolveConditionalEdge() {
        GraphDefinition def = GraphDefinition.builder()
            .edge("a", "b")
            .conditionalEdge("b", "c", s -> "PASS".equals(s.getAuditDecision()))
            .conditionalEdge("b", "a", s -> "REPLAN".equals(s.getAuditDecision()))
            .build();
        NovelAgentState passState = createState().toBuilder().auditDecision("PASS").build();
        assertEquals("c", def.getNextNode("b", passState, Mode.FULL));
        NovelAgentState replanState = createState().toBuilder().auditDecision("REPLAN").build();
        assertEquals("a", def.getNextNode("b", replanState, Mode.FULL));
    }

    @Test
    void shouldFilterByMode() {
        GraphDefinition def = GraphDefinition.builder()
            .edge("a", "b", Mode.FAST)
            .edge("a", "c", Mode.FULL)
            .build();
        assertEquals("b", def.getNextNode("a", createState(), Mode.FAST));
        assertEquals("c", def.getNextNode("a", createState(), Mode.FULL));
    }

    @Test
    void shouldDetectFanOut() {
        GraphDefinition def = GraphDefinition.builder()
            .fanOut("a", List.of("b", "c"), Mode.FULL)
            .fanIn(List.of("b", "c"), "d", Mode.FULL)
            .build();
        assertTrue(def.isFanOut("a"));
        assertFalse(def.isFanOut("b"));
        assertEquals(List.of("b", "c"), def.getFanOutTargets("a", Mode.FULL));
        assertEquals("d", def.getFanInTarget(List.of("b", "c"), Mode.FULL));
    }

    @Test
    void shouldCheckRetryPolicy() {
        GraphDefinition def = GraphDefinition.builder()
            .retryPolicy("writing", 3, "summary")
            .build();
        assertEquals(3, def.getMaxRetries("writing"));
        assertEquals("summary", def.getFallbackNode("writing"));
        assertEquals(0, def.getMaxRetries("unknown"));
    }

    @Test
    void shouldDetectCheckpoints() {
        GraphDefinition def = GraphDefinition.builder()
            .checkpoint("outline")
            .checkpoint("writing")
            .build();
        assertTrue(def.isCheckpoint("outline"));
        assertTrue(def.isCheckpoint("writing"));
        assertFalse(def.isCheckpoint("arc_check"));
    }

    @Test
    void conditionalEdgesTakePrecedenceOverSimpleEdges() {
        GraphDefinition def = GraphDefinition.builder()
            .edge("a", "b")
            .conditionalEdge("a", "c", s -> true)
            .build();
        assertEquals("c", def.getNextNode("a", createState(), Mode.FULL));
    }

    private NovelAgentState createState() {
        return NovelAgentState.builder().build();
    }
}
