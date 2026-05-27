package com.novel.system.ai.graph;

public interface AuthorAgent {

    AuthorDirective beforeNode(String nodeName, NovelAgentState state);

    NovelAgentState afterNode(String nodeName, NovelAgentState state, AuthorDirective directive);
}
