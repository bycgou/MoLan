package com.novel.system.ai.graph;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;

@Slf4j
public class TracingChatModel implements ChatModel {
    private final ChatModel delegate;
    private final Consumer<LlmCallRecord> recorder;
    private final String nodeName;

    public TracingChatModel(ChatModel delegate, Consumer<LlmCallRecord> recorder, String nodeName) {
        this.delegate = delegate;
        this.recorder = recorder;
        this.nodeName = nodeName;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        var start = Instant.now();
        ChatResponse response = delegate.chat(request);
        var end = Instant.now();

        var usage = response.tokenUsage();
        if (usage != null) {
            recorder.accept(new LlmCallRecord(
                nodeName,
                null,
                response.modelName(),
                usage.inputTokenCount(),
                usage.outputTokenCount(),
                usage.totalTokenCount(),
                Duration.between(start, end).toMillis(),
                start
            ));
        }

        return response;
    }

}
