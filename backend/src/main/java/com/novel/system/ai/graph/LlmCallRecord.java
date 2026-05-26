package com.novel.system.ai.graph;

import java.time.Instant;

public record LlmCallRecord(
    String nodeName,
    String usageType,
    String modelName,
    int inputTokens,
    int outputTokens,
    int totalTokens,
    long durationMs,
    Instant timestamp
) {}
