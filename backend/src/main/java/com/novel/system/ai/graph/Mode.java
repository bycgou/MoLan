package com.novel.system.ai.graph;

public enum Mode {
    FAST,       // 快速写作：跳过所有校验
    STREAMING,  // 流式写作：轻量校验（连续性 + OOC + 逻辑）
    FULL        // 完整多 Agent：全部节点 + AuthorAgent
}
