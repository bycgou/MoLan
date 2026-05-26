package com.novel.system.ai.graph;

public enum ErrorStrategy {
    SKIP,   // 跳过该节点，使用默认值，继续执行
    RETRY,  // 重试当前节点
    FAIL    // 终止整个流程，返回错误
}
