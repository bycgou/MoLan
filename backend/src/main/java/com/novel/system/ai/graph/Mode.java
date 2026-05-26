package com.novel.system.ai.graph;

public enum Mode {
    FAST,   // 流式写作：跳过审计节点
    FULL    // 完整多 Agent：全部节点 + AuthorAgent
}
