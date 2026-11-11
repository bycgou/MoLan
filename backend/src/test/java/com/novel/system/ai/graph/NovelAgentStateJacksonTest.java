package com.novel.system.ai.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.system.ai.deslop.DeslopFinding;
import com.novel.system.ai.deslop.DeslopReview;
import com.novel.system.ai.deslop.DeslopSeverity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 检查点会把 state 序列化后再反序列化回来 —— 新增字段必须扛得住这一轮。
 */
class NovelAgentStateJacksonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deslopFieldsSurviveRoundTrip() throws Exception {
        NovelAgentState original = NovelAgentState.builder()
                .novelId("n1")
                .chapterNum(3)
                .deslopRewriteCount(1)
                .deslopDecision("REWRITE")
                .deslopFindings(List.of(DeslopFinding.builder()
                        .ruleId("mech:deslop:trailer-ending")
                        .severity(DeslopSeverity.BLOCKING)
                        .review(DeslopReview.MECHANICAL)
                        .quote("这一刻，")
                        .message("章末总结体")
                        .suggestion("用动作收束")
                        .build()))
                .pendingRevisionNotes(List.of("- 「trailer-ending」章末总结体｜命中：这一切，"))
                .build();

        String json = objectMapper.writeValueAsString(original);
        NovelAgentState restored = objectMapper.readValue(json, NovelAgentState.class);

        assertEquals("n1", restored.getNovelId());
        assertEquals(3, restored.getChapterNum());
        assertEquals(1, restored.getDeslopRewriteCount());
        assertEquals("REWRITE", restored.getDeslopDecision());
        assertNotNull(restored.getDeslopFindings());
        assertEquals(1, restored.getDeslopFindings().size());
        DeslopFinding finding = restored.getDeslopFindings().get(0);
        assertEquals("mech:deslop:trailer-ending", finding.getRuleId());
        assertEquals(DeslopSeverity.BLOCKING, finding.getSeverity());
        assertEquals(DeslopReview.MECHANICAL, finding.getReview());
        assertEquals("这一刻，", finding.getQuote());
        assertNotNull(restored.getPendingRevisionNotes());
        assertEquals(1, restored.getPendingRevisionNotes().size());
    }

    @Test
    void unsetListFieldsStayNullAndAreNotFatal() throws Exception {
        NovelAgentState original = NovelAgentState.builder()
                .novelId("n2")
                .chapterNum(1)
                .build();

        String json = objectMapper.writeValueAsString(original);
        NovelAgentState restored = objectMapper.readValue(json, NovelAgentState.class);

        assertNull(restored.getDeslopFindings());
        assertNull(restored.getPendingRevisionNotes());
        assertEquals(0, restored.getDeslopRewriteCount());
    }
}
