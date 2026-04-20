package com.novel.system.ai.chain;

/**
 * 验证结果
 */
public class ValidationResult {
    
    private boolean passed; // 是否通过
    private OOCResult oocResult; // 人物行为一致性验证结果
    private LogicResult logicResult; // 逻辑连贯性验证结果
    private ClueResult clueResult; // 伏笔回收验证结果
    private SettingResult settingResult; // 设定一致性验证结果
    private RhythmResult rhythmResult; // 节奏合理性验证结果
    private String summary; // 总结
    
    public ValidationResult() {
    }
    
    public ValidationResult(boolean passed, OOCResult oocResult, LogicResult logicResult, ClueResult clueResult, SettingResult settingResult, RhythmResult rhythmResult, String summary) {
        this.passed = passed;
        this.oocResult = oocResult;
        this.logicResult = logicResult;
        this.clueResult = clueResult;
        this.settingResult = settingResult;
        this.rhythmResult = rhythmResult;
        this.summary = summary;
    }
    
    public boolean getPassed() {
        return passed;
    }
    
    public void setPassed(boolean passed) {
        this.passed = passed;
    }
    
    public OOCResult getOocResult() {
        return oocResult;
    }
    
    public void setOocResult(OOCResult oocResult) {
        this.oocResult = oocResult;
    }
    
    public LogicResult getLogicResult() {
        return logicResult;
    }
    
    public void setLogicResult(LogicResult logicResult) {
        this.logicResult = logicResult;
    }
    
    public ClueResult getClueResult() {
        return clueResult;
    }
    
    public void setClueResult(ClueResult clueResult) {
        this.clueResult = clueResult;
    }
    
    public SettingResult getSettingResult() {
        return settingResult;
    }
    
    public void setSettingResult(SettingResult settingResult) {
        this.settingResult = settingResult;
    }
    
    public RhythmResult getRhythmResult() {
        return rhythmResult;
    }
    
    public void setRhythmResult(RhythmResult rhythmResult) {
        this.rhythmResult = rhythmResult;
    }
    
    public String getSummary() {
        return summary;
    }
    
    public void setSummary(String summary) {
        this.summary = summary;
    }
}