package com.novel.system.ai.graph;

import java.time.Instant;

public record StructuredError(
    String nodeName,
    ErrorStrategy strategy,
    String message,
    Throwable cause,
    Instant timestamp,
    boolean recovered
) {}
