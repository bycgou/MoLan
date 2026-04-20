package com.novel.system.ai.chain;

/**
 * 人物行为一致性验证结果
 */
public class OOCResult {
    
    private boolean passed;
    private Integer totalScore; // 总分 (0-100)
    private Integer languageStyleScore; // 语言风格得分
    private Integer behaviorMotivationScore; // 行为动机得分
    private Integer knowledgeLevelScore; // 知识水平得分
    private Integer growthAdaptationScore; // 成长适配得分
    private String problemDescription; // 问题描述
    private String suggestion; // 修复建议
    
    public OOCResult() {
    }
    
    public OOCResult(boolean passed, Integer totalScore, Integer languageStyleScore, Integer behaviorMotivationScore, Integer knowledgeLevelScore, Integer growthAdaptationScore, String problemDescription, String suggestion) {
        this.passed = passed;
        this.totalScore = totalScore;
        this.languageStyleScore = languageStyleScore;
        this.behaviorMotivationScore = behaviorMotivationScore;
        this.knowledgeLevelScore = knowledgeLevelScore;
        this.growthAdaptationScore = growthAdaptationScore;
        this.problemDescription = problemDescription;
        this.suggestion = suggestion;
    }
    
    public boolean getPassed() {
        return passed;
    }
    
    public boolean isPassed() {
        return passed;
    }
    
    public void setPassed(boolean passed) {
        this.passed = passed;
    }
    
    public Integer getTotalScore() {
        return totalScore;
    }
    
    public void setTotalScore(Integer totalScore) {
        this.totalScore = totalScore;
    }
    
    public Integer getLanguageStyleScore() {
        return languageStyleScore;
    }
    
    public void setLanguageStyleScore(Integer languageStyleScore) {
        this.languageStyleScore = languageStyleScore;
    }
    
    public Integer getBehaviorMotivationScore() {
        return behaviorMotivationScore;
    }
    
    public void setBehaviorMotivationScore(Integer behaviorMotivationScore) {
        this.behaviorMotivationScore = behaviorMotivationScore;
    }
    
    public Integer getKnowledgeLevelScore() {
        return knowledgeLevelScore;
    }
    
    public void setKnowledgeLevelScore(Integer knowledgeLevelScore) {
        this.knowledgeLevelScore = knowledgeLevelScore;
    }
    
    public Integer getGrowthAdaptationScore() {
        return growthAdaptationScore;
    }
    
    public void setGrowthAdaptationScore(Integer growthAdaptationScore) {
        this.growthAdaptationScore = growthAdaptationScore;
    }
    
    public String getProblemDescription() {
        return problemDescription;
    }
    
    public void setProblemDescription(String problemDescription) {
        this.problemDescription = problemDescription;
    }
    
    public String getSuggestion() {
        return suggestion;
    }
    
    public void setSuggestion(String suggestion) {
        this.suggestion = suggestion;
    }
}