package com.novel.system.ai.agent;

import java.util.List;

/**
 * 摘要Agent响应
 */
public class SummaryAgentResponse {
    
    private String fingerprint; // 章节指纹
    private List<String> keywords; // 关键词
    private List<String> characters; // 出场人物
    private List<String> locations; // 地点
    
    /**
     * 实体关系图谱 (新增)
     */
    private List<java.util.Map<String, String>> relations;
    
    public SummaryAgentResponse() {
    }
    
    public SummaryAgentResponse(String fingerprint, List<String> keywords, List<String> characters, List<String> locations) {
        this.fingerprint = fingerprint;
        this.keywords = keywords;
        this.characters = characters;
        this.locations = locations;
    }
    
    public String getFingerprint() {
        return fingerprint;
    }
    
    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
    }
    
    public List<String> getKeywords() {
        return keywords;
    }
    
    public void setKeywords(List<String> keywords) {
        this.keywords = keywords;
    }
    
    public List<String> getCharacters() {
        return characters;
    }
    
    public void setCharacters(List<String> characters) {
        this.characters = characters;
    }
    
    public List<String> getLocations() {
        return locations;
    }

    public void setLocations(List<String> locations) {
        this.locations = locations;
    }
    
    public List<java.util.Map<String, String>> getRelations() {
        return relations;
    }
    
    public void setRelations(List<java.util.Map<String, String>> relations) {
        this.relations = relations;
    }
}