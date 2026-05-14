package com.novel.system.ai.agent;

import java.util.List;

/**
 * 大纲Agent响应
 */
public class OutlineAgentResponse {

    private String outline; // 章纲内容
    private List<String> implementationIdeas; // 实现思路

    // 新增：与 OutlineAgent @SystemMessage 输出格式对齐
    private String title; // 章节标题
    private String summary; // 概要
    private List<String> keyPoints; // 关键要点
    private String hook; // 结尾钩子

    public OutlineAgentResponse() {
    }

    public OutlineAgentResponse(String outline, List<String> implementationIdeas) {
        this.outline = outline;
        this.implementationIdeas = implementationIdeas;
    }

    public String getOutline() {
        return outline;
    }

    public void setOutline(String outline) {
        this.outline = outline;
    }

    public List<String> getImplementationIdeas() {
        return implementationIdeas;
    }

    public void setImplementationIdeas(List<String> implementationIdeas) {
        this.implementationIdeas = implementationIdeas;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<String> getKeyPoints() {
        return keyPoints;
    }

    public void setKeyPoints(List<String> keyPoints) {
        this.keyPoints = keyPoints;
    }

    public String getHook() {
        return hook;
    }

    public void setHook(String hook) {
        this.hook = hook;
    }
    
    /**
     * 实现思路内部类
     */
    public static class ImplementationIdea {
        private String id;
        private String content;
        private Integer score;
        
        public ImplementationIdea() {
        }
        
        public ImplementationIdea(String id, String content, Integer score) {
            this.id = id;
            this.content = content;
            this.score = score;
        }
        
        public String getId() {
            return id;
        }
        
        public void setId(String id) {
            this.id = id;
        }
        
        public String getContent() {
            return content;
        }
        
        public void setContent(String content) {
            this.content = content;
        }
        
        public Integer getScore() {
            return score;
        }
        
        public void setScore(Integer score) {
            this.score = score;
        }
    }
}