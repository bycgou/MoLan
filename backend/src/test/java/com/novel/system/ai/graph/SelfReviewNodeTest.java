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
class SelfReviewNodeTest {

    @Mock private ChatModel reviewerModel;
    private SelfReviewNode node;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        node = new SelfReviewNode(reviewerModel, objectMapper);
    }

    @Test
    void parsesSeverityHigh() throws Exception {
        when(reviewerModel.chat(anyString()))
                .thenReturn("{\"severity\":\"HIGH\",\"overallScore\":4,\"passed\":false," +
                        "\"notes\":[\"逻辑矛盾\",\"角色OOC\"]}");

        NovelAgentState input = NovelAgentState.builder()
                .draftContent(NovelAgentState.DraftContent.builder().content("内容").build())
                .chapterPlan(NovelAgentState.ChapterPlan.builder().summary("概要").build())
                .build();

        NovelAgentState result = node.apply(input);

        assertEquals(NovelAgentState.Severity.HIGH, result.getDraftContent().getSeverity());
        assertEquals(2, result.getDraftContent().getRevisionNotes().size());
        assertEquals("逻辑矛盾", result.getDraftContent().getRevisionNotes().get(0));
    }

    @Test
    void parsesSeverityLow() throws Exception {
        when(reviewerModel.chat(anyString()))
                .thenReturn("{\"severity\":\"LOW\",\"overallScore\":8,\"passed\":true,\"notes\":[]}");

        NovelAgentState input = NovelAgentState.builder()
                .draftContent(NovelAgentState.DraftContent.builder().content("内容").build())
                .chapterPlan(NovelAgentState.ChapterPlan.builder().summary("概要").build())
                .build();

        NovelAgentState result = node.apply(input);

        assertEquals(NovelAgentState.Severity.LOW, result.getDraftContent().getSeverity());
    }

    @Test
    void handlesInvalidSeverityGracefully() throws Exception {
        when(reviewerModel.chat(anyString()))
                .thenReturn("{\"severity\":\"INVALID\",\"overallScore\":7,\"passed\":true,\"notes\":[]}");

        NovelAgentState input = NovelAgentState.builder()
                .draftContent(NovelAgentState.DraftContent.builder().content("内容").build())
                .chapterPlan(NovelAgentState.ChapterPlan.builder().summary("概要").build())
                .build();

        NovelAgentState result = node.apply(input);

        assertEquals(NovelAgentState.Severity.LOW, result.getDraftContent().getSeverity());
    }
}
