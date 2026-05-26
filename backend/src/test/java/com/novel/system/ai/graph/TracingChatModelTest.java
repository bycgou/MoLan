package com.novel.system.ai.graph;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TracingChatModelTest {

    @Test
    void shouldRecordLlmCallOnSuccess() {
        ChatModel delegate = mock(ChatModel.class);
        ChatResponse mockResponse = mock(ChatResponse.class);
        TokenUsage usage = new TokenUsage(100, 50, 150);

        when(delegate.chat(any(ChatRequest.class))).thenReturn(mockResponse);
        when(mockResponse.tokenUsage()).thenReturn(usage);
        when(mockResponse.modelName()).thenReturn("test-model");

        List<LlmCallRecord> records = new ArrayList<>();
        TracingChatModel tracing = new TracingChatModel(delegate, records::add, "writing");

        tracing.chat(ChatRequest.builder().messages(UserMessage.from("test")).build());

        assertEquals(1, records.size());
        LlmCallRecord record = records.get(0);
        assertEquals("writing", record.nodeName());
        assertEquals("test-model", record.modelName());
        assertEquals(100, record.inputTokens());
        assertEquals(50, record.outputTokens());
        assertEquals(150, record.totalTokens());
        assertTrue(record.durationMs() >= 0);
    }

    @Test
    void shouldNotRecordWhenTokenUsageIsNull() {
        ChatModel delegate = mock(ChatModel.class);
        ChatResponse mockResponse = mock(ChatResponse.class);

        when(delegate.chat(any(ChatRequest.class))).thenReturn(mockResponse);
        when(mockResponse.tokenUsage()).thenReturn(null);

        List<LlmCallRecord> records = new ArrayList<>();
        TracingChatModel tracing = new TracingChatModel(delegate, records::add, "outline");

        tracing.chat(ChatRequest.builder().messages(UserMessage.from("test")).build());

        assertTrue(records.isEmpty());
    }
}
