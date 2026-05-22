package com.novel.system.ai.graph;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentExecutionResult {
    private String content;
    private int wordCount;
    private double qualityScore;
    private String chapterId;
    private List<String> executionTrace;
    private List<String> errors;
    private long elapsedMs;
}
