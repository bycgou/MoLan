package com.novel.system.ai.agent;

import java.util.List;
import java.util.Map;

/**
 * 摘要Agent响应
 * 扩展为 Super-Ma 风格的结构化摘要输出
 */
public class SummaryAgentResponse {
    
    /**
     * 剧情线类型：quest/fire/constellation
     */
    private String strandType;
    
    /**
     * 一句话描述（剧情指纹）
     */
    private String fingerprint;
    
    /**
     * 200-300字的叙事摘要
     */
    private String narrativeSummary;
    
    /**
     * 人物更新信息
     * 格式：{"人物名": {"职务": "当前职务", "处境": "当前处境", "关键变化": "本章变化"}}
     */
    private Map<String, Map<String, String>> characterUpdates;
    
    /**
     * 本章最关键的单一事件
     */
    private String keyEvent;
    
    /**
     * 本章使用的意象/场景
     */
    private List<String> usedTropes;
    
    /**
     * 新埋下的伏笔
     * 格式：[{"desc": "新伏笔描述", "deadline": "建议回收章节"}]
     */
    private List<Map<String, String>> newForeshadowing;
    
    /**
     * 已回收的伏笔描述
     */
    private List<String> resolvedForeshadowing;
    
    /**
     * 当前故事势能
     * 如：上升、下降、转折、高潮前蓄力、高潮后余波
     */
    private String storyMomentum;
    
    /**
     * 关键词
     */
    private List<String> keywords;
    
    /**
     * 出场人物
     */
    private List<String> characters;
    
    /**
     * 地点
     */
    private List<String> locations;
    
    /**
     * 实体关系图谱
     */
    private List<Map<String, String>> relations;
    
    /**
     * 钩子等级：低/中/高
     */
    private String hookLevel;
    
    public SummaryAgentResponse() {
    }
    
    // Getter 和 Setter 方法
    public String getStrandType() {
        return strandType;
    }
    
    public void setStrandType(String strandType) {
        this.strandType = strandType;
    }
    
    public String getFingerprint() {
        return fingerprint;
    }
    
    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
    }
    
    public String getNarrativeSummary() {
        return narrativeSummary;
    }
    
    public void setNarrativeSummary(String narrativeSummary) {
        this.narrativeSummary = narrativeSummary;
    }
    
    public Map<String, Map<String, String>> getCharacterUpdates() {
        return characterUpdates;
    }
    
    public void setCharacterUpdates(Map<String, Map<String, String>> characterUpdates) {
        this.characterUpdates = characterUpdates;
    }
    
    public String getKeyEvent() {
        return keyEvent;
    }
    
    public void setKeyEvent(String keyEvent) {
        this.keyEvent = keyEvent;
    }
    
    public List<String> getUsedTropes() {
        return usedTropes;
    }
    
    public void setUsedTropes(List<String> usedTropes) {
        this.usedTropes = usedTropes;
    }
    
    public List<Map<String, String>> getNewForeshadowing() {
        return newForeshadowing;
    }
    
    public void setNewForeshadowing(List<Map<String, String>> newForeshadowing) {
        this.newForeshadowing = newForeshadowing;
    }
    
    public List<String> getResolvedForeshadowing() {
        return resolvedForeshadowing;
    }
    
    public void setResolvedForeshadowing(List<String> resolvedForeshadowing) {
        this.resolvedForeshadowing = resolvedForeshadowing;
    }
    
    public String getStoryMomentum() {
        return storyMomentum;
    }
    
    public void setStoryMomentum(String storyMomentum) {
        this.storyMomentum = storyMomentum;
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
    
    public List<Map<String, String>> getRelations() {
        return relations;
    }
    
    public void setRelations(List<Map<String, String>> relations) {
        this.relations = relations;
    }
    
    public String getHookLevel() {
        return hookLevel;
    }
    
    public void setHookLevel(String hookLevel) {
        this.hookLevel = hookLevel;
    }
}