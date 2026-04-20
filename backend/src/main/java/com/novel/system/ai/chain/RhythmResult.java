package com.novel.system.ai.chain;

/**
 * 节奏合理性验证结果
 */
public class RhythmResult {
    
    private boolean passed;
    private String expectedRhythm; // 预期节奏
    private String actualRhythm; // 实际节奏
    private String emotionCurve; // 情绪曲线
    private String suggestion; // 修复建议
    
    public RhythmResult() {
    }
    
    public RhythmResult(boolean passed, String expectedRhythm, String actualRhythm, String emotionCurve, String suggestion) {
        this.passed = passed;
        this.expectedRhythm = expectedRhythm;
        this.actualRhythm = actualRhythm;
        this.emotionCurve = emotionCurve;
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
    
    public String getExpectedRhythm() {
        return expectedRhythm;
    }
    
    public void setExpectedRhythm(String expectedRhythm) {
        this.expectedRhythm = expectedRhythm;
    }
    
    public String getActualRhythm() {
        return actualRhythm;
    }
    
    public void setActualRhythm(String actualRhythm) {
        this.actualRhythm = actualRhythm;
    }
    
    public String getEmotionCurve() {
        return emotionCurve;
    }
    
    public void setEmotionCurve(String emotionCurve) {
        this.emotionCurve = emotionCurve;
    }
    
    public String getSuggestion() {
        return suggestion;
    }
    
    public void setSuggestion(String suggestion) {
        this.suggestion = suggestion;
    }
}