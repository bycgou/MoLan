package com.novel.system.ai.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.system.service.memory.ConvergenceService;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SelfReviewNodeTest {

    @Mock private ChatModelProvider chatModelProvider;
    @Mock private ChatModel chatModel;
    @Mock private ConvergenceService convergenceService;
    private SelfReviewNode node;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        when(chatModelProvider.getStructuredModel(any())).thenReturn(chatModel);
        node = new SelfReviewNode(chatModelProvider, objectMapper, convergenceService);
    }

    @Test
    void parsesSeverityHigh() throws Exception {
        when(chatModel.chat(anyString()))
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
        when(chatModel.chat(anyString()))
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
        when(chatModel.chat(anyString()))
                .thenReturn("{\"severity\":\"INVALID\",\"overallScore\":7,\"passed\":true,\"notes\":[]}");

        NovelAgentState input = NovelAgentState.builder()
                .draftContent(NovelAgentState.DraftContent.builder().content("内容").build())
                .chapterPlan(NovelAgentState.ChapterPlan.builder().summary("概要").build())
                .build();

        NovelAgentState result = node.apply(input);

        assertEquals(NovelAgentState.Severity.LOW, result.getDraftContent().getSeverity());
    }

    @Test
    void incrementsWriteRetryCountWhenRewritingIsRequired() throws Exception {
        when(chatModel.chat(anyString()))
                .thenReturn("{\"severity\":\"HIGH\",\"overallScore\":4,\"passed\":false,\"notes\":[\"逻辑矛盾\"]}");

        NovelAgentState input = NovelAgentState.builder()
                .draftContent(NovelAgentState.DraftContent.builder().content("内容").build())
                .chapterPlan(NovelAgentState.ChapterPlan.builder().summary("概要").build())
                .build();

        NovelAgentState result = node.apply(input);

        // writeRetryCount 此前从未自增，导致 self_review → writing 的条件恒真
        assertEquals(1, result.getWriteRetryCount());
    }

    @Test
    void doesNotIncrementWriteRetryCountWhenAcceptable() throws Exception {
        when(chatModel.chat(anyString()))
                .thenReturn("{\"severity\":\"LOW\",\"overallScore\":8,\"passed\":true,\"notes\":[]}");

        NovelAgentState input = NovelAgentState.builder()
                .draftContent(NovelAgentState.DraftContent.builder().content("内容").build())
                .chapterPlan(NovelAgentState.ChapterPlan.builder().summary("概要").build())
                .build();

        NovelAgentState result = node.apply(input);

        assertEquals(0, result.getWriteRetryCount());
    }

    @Test
    void appendsNotesToPendingRevisionNotes() throws Exception {
        when(chatModel.chat(anyString()))
                .thenReturn("{\"severity\":\"HIGH\",\"overallScore\":4,\"passed\":false,\"notes\":[\"补一个动作\"]}");

        NovelAgentState input = NovelAgentState.builder()
                .draftContent(NovelAgentState.DraftContent.builder().content("内容").build())
                .chapterPlan(NovelAgentState.ChapterPlan.builder().summary("概要").build())
                .build();

        NovelAgentState result = node.apply(input);

        assertNotNull(result.getPendingRevisionNotes());
        assertTrue(result.getPendingRevisionNotes().contains("补一个动作"));
    }
}
