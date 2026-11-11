package com.novel.system.ai.graph;

import com.novel.system.ai.deslop.DeslopRulesetProvider;
import com.novel.system.service.style.StyleTextAnalyzer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 反 AI 味门禁节点 —— 只有 mechanical + blocking 能挡稿，且预算独立。
 */
class DeslopCheckNodeTest {

    /** 命中随包规则表里的 blocking 规则 trailer-ending（章末总结体）。 */
    private static final String BODY_WITH_TRAILER =
            "他把灯打开，屋子里的霉味散开一些。\n\n"
                    + "他站在原地没有动。\n\n"
                    + "这一刻，他终于明白这一切都不是巧合。";

    private static final String CLEAN_BODY =
            "他把灯打开，屋子里的霉味散开一些。\n\n"
                    + "他从口袋里摸出半块干粮，就着凉水咽下去。\n\n"
                    + "窗外的雨小了些，屋檐还在滴水。";

    private DeslopCheckNode node;

    @BeforeEach
    void setUp() {
        node = new DeslopCheckNode(new DeslopRulesetProvider(), new StyleTextAnalyzer(), true, 1, 3);
    }

    private static NovelAgentState stateWithBody(String body) {
        return NovelAgentState.builder()
                .novelId("n1")
                .chapterNum(1)
                .draftContent(NovelAgentState.DraftContent.builder()
                        .content(body)
                        .wordCount(body.length())
                        .build())
                .build();
    }

    @Test
    void passesWhenNoBlockingFinding() throws Exception {
        NovelAgentState result = node.apply(stateWithBody(CLEAN_BODY));
        assertEquals("PASS", result.getDeslopDecision());
        assertEquals(0, result.getDeslopRewriteCount());
        assertNull(result.getPendingRevisionNotes());
    }

    @Test
    void requestsRewriteWhenBlockingFound() throws Exception {
        NovelAgentState result = node.apply(stateWithBody(BODY_WITH_TRAILER));
        assertEquals("REWRITE", result.getDeslopDecision());
        assertEquals(1, result.getDeslopRewriteCount());
    }

    @Test
    void exhaustsRewriteBudgetInsteadOfBlockingForever() throws Exception {
        NovelAgentState input = stateWithBody(BODY_WITH_TRAILER).toBuilder()
                .deslopRewriteCount(1)
                .build();

        NovelAgentState result = node.apply(input);
        assertEquals("REWRITE_EXHAUSTED", result.getDeslopDecision());
        assertEquals(1, result.getDeslopRewriteCount());
    }

    @Test
    void formatsFindingsIntoPendingRevisionNotes() throws Exception {
        NovelAgentState result = node.apply(stateWithBody(BODY_WITH_TRAILER));

        List<String> notes = result.getPendingRevisionNotes();
        assertNotNull(notes);
        assertTrue(notes.size() >= 1);
        String note = notes.get(0);
        assertTrue(note.contains("trailer-ending"), note);
        assertTrue(note.contains("命中："), note);
        assertTrue(note.contains("改法："), note);
    }

    @Test
    void onlyBlockingFindingsBecomeRevisionNotes() throws Exception {
        NovelAgentState result = node.apply(stateWithBody(CLEAN_BODY));
        if (result.getPendingRevisionNotes() != null) {
            assertTrue(result.getPendingRevisionNotes().isEmpty(),
                    "没有 blocking 命中就不该产生修订建议");
        }
    }

    @Test
    void enforceDisabledOnlyRecordsFindings() throws Exception {
        DeslopCheckNode passive = new DeslopCheckNode(
                new DeslopRulesetProvider(), new StyleTextAnalyzer(), false, 1, 3);

        NovelAgentState result = passive.apply(stateWithBody(BODY_WITH_TRAILER));
        assertEquals("PASS", result.getDeslopDecision());
        assertEquals(0, result.getDeslopRewriteCount());
        assertNotNull(result.getDeslopFindings());
        assertTrue(result.getDeslopFindings().stream()
                .anyMatch(f -> "mech:deslop:trailer-ending".equals(f.getRuleId())));
    }

    @Test
    void handlesMissingDraftWithoutThrowing() throws Exception {
        NovelAgentState empty = NovelAgentState.builder().novelId("n1").chapterNum(1).build();
        NovelAgentState result = node.apply(empty);
        assertEquals("PASS", result.getDeslopDecision());
    }

    @Test
    void nodeNameIsStable() {
        assertEquals("deslop_check", node.nodeName());
    }
}
