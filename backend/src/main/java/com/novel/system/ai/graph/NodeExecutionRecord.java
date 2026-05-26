package com.novel.system.ai.graph;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

public record NodeExecutionRecord(
    String nodeName,
    Mode mode,
    Instant startTime,
    Instant endTime,
    long durationMs,
    boolean skipped,
    ErrorStrategy errorStrategy,
    StructuredError error,
    AuthorDirective directive,
    Map<String, Object> metadata
) {
    public static NodeExecutionRecord success(String nodeName, Mode mode,
                                               Instant start, Instant end,
                                               AuthorDirective directive,
                                               Map<String, Object> metadata) {
        return new NodeExecutionRecord(nodeName, mode, start, end,
                Duration.between(start, end).toMillis(), false, null, null, directive, metadata);
    }
}
