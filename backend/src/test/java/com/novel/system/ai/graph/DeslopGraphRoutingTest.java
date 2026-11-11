package com.novel.system.ai.graph;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 门禁节点的路由 —— 插在 writing 与 self_review 之间，且不能把图带进死循环。
 */
class DeslopGraphRoutingTest {

    private static GraphDefinition definition() {
        return GraphDefinition.builder()
                .edge("arc_check", "writing")
                .edge("writing", "deslop_check")
                .conditionalEdge("deslop_check", "writing",
                        s -> "REWRITE".equals(s.getDeslopDecision()))
                .edge("deslop_check", "self_review")
                .edge("self_review", "summary")
                .retryPolicy("writing", 3, "summary")
                .build();
    }

    private static class StubNode implements NovelGraphNode {
        private final String name;
        private final Function<NovelAgentState, NovelAgentState> body;
        final AtomicInteger calls = new AtomicInteger();

        StubNode(String name, Function<NovelAgentState, NovelAgentState> body) {
            this.name = name;
            this.body = body;
        }

        @Override
        public String nodeName() {
            return name;
        }

        @Override
        public NovelAgentState apply(NovelAgentState state) {
            calls.incrementAndGet();
            return body.apply(state);
        }
    }

    @Test
    void writingRoutesToDeslopCheck() {
        assertEquals("deslop_check", definition().getNextNode("writing",
                NovelAgentState.builder().build(), Mode.FULL));
    }

    @Test
    void deslopRoutesToSelfReviewOnPass() {
        NovelAgentState state = NovelAgentState.builder().deslopDecision("PASS").build();
        assertEquals("self_review", definition().getNextNode("deslop_check", state, Mode.FULL));
    }

    @Test
    void deslopRoutesToWritingOnRewrite() {
        NovelAgentState state = NovelAgentState.builder().deslopDecision("REWRITE").build();
        assertEquals("writing", definition().getNextNode("deslop_check", state, Mode.FULL));
    }

    @Test
    void exhaustedRewriteBudgetRoutesToSelfReview() {
        NovelAgentState state = NovelAgentState.builder()
                .deslopDecision("REWRITE_EXHAUSTED").build();
        assertEquals("self_review", definition().getNextNode("deslop_check", state, Mode.FULL));
    }

    @Test
    void rewriteLoopTerminatesAfterBudgetExhausted() {
        GraphDefinition def = definition();
        GraphRunner runner = new GraphRunner(def);
        runner.setNodeExecutor(new NodeExecutor(null, null, def));

        StubNode writing = new StubNode("writing", s -> s.toBuilder()
                .draftContent(NovelAgentState.DraftContent.builder()
                        .content("正文").wordCount(2).build())
                .build());
        // 一直要求重写
        StubNode deslop = new StubNode("deslop_check", s -> s.toBuilder()
                .deslopDecision("REWRITE")
                .build());
        StubNode selfReview = new StubNode("self_review", s -> s);
        StubNode summary = new StubNode("summary", s -> s.toBuilder()
                .finalResult(NovelAgentState.FinalResult.builder().finalContent("正文").build())
                .build());

        runner.register(new StubNode("arc_check", s -> s));
        runner.register(writing);
        runner.register(deslop);
        runner.register(selfReview);
        runner.register(summary);

        NovelAgentState result = runner.execute(
                NovelAgentState.builder().novelId("n1").chapterNum(1).build(), Mode.FULL);

        assertEquals(3, writing.calls.get(), "writing 的重试配额是 3 次，不应无限重写");
        assertEquals(3, deslop.calls.get());
        assertEquals(0, selfReview.calls.get());
        assertEquals("正文", result.getFinalResult().getFinalContent());
    }

    @Test
    void oneRewriteThenPassContinuesToSelfReview() {
        GraphDefinition def = definition();
        GraphRunner runner = new GraphRunner(def);
        runner.setNodeExecutor(new NodeExecutor(null, null, def));

        StubNode writing = new StubNode("writing", s -> s.toBuilder()
                .draftContent(NovelAgentState.DraftContent.builder()
                        .content("正文").wordCount(2).build())
                .build());
        // 第一次要求重写，之后放行
        AtomicInteger pass = new AtomicInteger();
        StubNode deslop = new StubNode("deslop_check", s -> s.toBuilder()
                .deslopDecision(pass.getAndIncrement() == 0 ? "REWRITE" : "PASS")
                .build());
        StubNode selfReview = new StubNode("self_review", s -> s);
        StubNode summary = new StubNode("summary", s -> s.toBuilder()
                .finalResult(NovelAgentState.FinalResult.builder().finalContent("正文").build())
                .build());

        runner.register(new StubNode("arc_check", s -> s));
        runner.register(writing);
        runner.register(deslop);
        runner.register(selfReview);
        runner.register(summary);

        runner.execute(NovelAgentState.builder().novelId("n1").chapterNum(1).build(), Mode.FULL);

        assertEquals(2, writing.calls.get());
        assertEquals(2, deslop.calls.get());
        assertEquals(1, selfReview.calls.get());
        assertEquals(1, summary.calls.get());
    }
}
