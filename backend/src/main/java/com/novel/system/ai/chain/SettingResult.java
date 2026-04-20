package com.novel.system.ai.chain;

/**
 * 设定一致性验证结果
 */
public class SettingResult {
    
    private boolean passed;
    private String message;
    private String worldRuleStatus;
    private String worldClockStatus;
    private String conflicts;
    
    public SettingResult() {
    }
    
    public SettingResult(boolean passed, String message, String worldRuleStatus, String worldClockStatus, String conflicts) {
        this.passed = passed;
        this.message = message;
        this.worldRuleStatus = worldRuleStatus;
        this.worldClockStatus = worldClockStatus;
        this.conflicts = conflicts;
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
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public String getWorldRuleStatus() {
        return worldRuleStatus;
    }
    
    public void setWorldRuleStatus(String worldRuleStatus) {
        this.worldRuleStatus = worldRuleStatus;
    }
    
    public String getWorldClockStatus() {
        return worldClockStatus;
    }
    
    public void setWorldClockStatus(String worldClockStatus) {
        this.worldClockStatus = worldClockStatus;
    }
    
    public String getConflicts() {
        return conflicts;
    }
    
    public void setConflicts(String conflicts) {
        this.conflicts = conflicts;
    }
}