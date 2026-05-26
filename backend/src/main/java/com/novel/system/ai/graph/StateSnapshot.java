package com.novel.system.ai.graph;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StateSnapshot {
    private String nodeName;
    private String stateJson;
    private Instant savedAt;
    private String novelId;
    private int chapterNum;
}
