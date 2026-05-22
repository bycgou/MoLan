package com.novel.system.ai.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QualityScoringNodeTest {

    @Mock private ChatModel editorModel;
    private QualityScoringNode node;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        node = new QualityScoringNode(editorModel, objectMapper);
    }

    @Test
    void parsesQualityAndClueLanding() throws Exception {
        when(editorModel.chat(anyString()))
                .thenReturn("{\"totalScore\":82,\"qualityPassed\":true," +
                        "\"overallAssessment\":\"优秀\",\"clueLandingPassed\":true," +
                        "\"clueLandingIssues\":[],\"suggestions\":[\"节奏可以更快\"]}");

        NovelAgentState.AuditResult audit = NovelAgentState.AuditResult.builder()
                .oocPassed(true).logicPassed(true).cluePassed(true)
                .settingPassed(true).rhythmPassed(true).build();

        NovelAgentState input = NovelAgentState.builder()
                .auditResult(audit)
                .draftContent(NovelAgentState.DraftContent.builder().content("内容").build())
                .chapterPlan(NovelAgentState.ChapterPlan.builder()
                        .climaxPoint("高潮").emotionalArc("弧线")
                        .clueActions(List.of(
                                NovelAgentState.ClueAction.builder()
                                        .clueId("密信").action("RESOLVE").description("回收密信").build()
                        )).build())
                .build();

        NovelAgentState result = node.apply(input);

        assertEquals(82.0, result.getAuditResult().getQualityScore());
        assertTrue(result.getAuditResult().isQualityPassed());
        assertTrue(result.getAuditResult().isClueLandingPassed());
        assertTrue(result.getAuditResult().isAllPassed());
        assertEquals(1, result.getAuditResult().getSuggestions().size());
    }

    @Test
    void setsAllPassedFalse_whenClueLandingFails() throws Exception {
        when(editorModel.chat(anyString()))
                .thenReturn("{\"totalScore\":70,\"qualityPassed\":true," +
                        "\"overallAssessment\":\"\",\"clueLandingPassed\":false," +
                        "\"clueLandingIssues\":[\"密信未体现\"],\"suggestions\":[]}");

        NovelAgentState.AuditResult audit = NovelAgentState.AuditResult.builder()
                .oocPassed(true).logicPassed(true).cluePassed(true)
                .settingPassed(true).rhythmPassed(true).build();

        NovelAgentState input = NovelAgentState.builder()
                .auditResult(audit)
                .draftContent(NovelAgentState.DraftContent.builder().content("内容").build())
                .chapterPlan(NovelAgentState.ChapterPlan.builder().build())
                .build();

        NovelAgentState result = node.apply(input);

        assertFalse(result.getAuditResult().isClueLandingPassed());
        assertFalse(result.getAuditResult().isAllPassed());
    }
}
