package com.novel.system.ai.agent;

/**
 * 写作Agent响应
 */
public class WritingAgentResponse {
    
    private String content; // 生成的正文
    private Integer wordCount; // 实际字数
    
    public WritingAgentResponse() {
    }
    
    public WritingAgentResponse(String content, Integer wordCount) {
        this.content = content;
        this.wordCount = wordCount;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public Integer getWordCount() {
        return wordCount;
    }
    
    public void setWordCount(Integer wordCount) {
        this.wordCount = wordCount;
    }
}