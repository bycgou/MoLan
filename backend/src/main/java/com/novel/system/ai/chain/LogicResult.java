package com.novel.system.ai.chain;

/**
 * 逻辑连贯性验证结果
 */
public class LogicResult {
    
    private boolean passed;
    private String message;
    private String conflictDescription;
    private String causalChainStatus;
    private String consistencyStatus;
    
    public LogicResult() {
    }
    
    public LogicResult(boolean passed, String message, String conflictDescription, String causalChainStatus, String consistencyStatus) {
        this.passed = passed;
        this.message = message;
        this.conflictDescription = conflictDescription;
        this.causalChainStatus = causalChainStatus;
        this.consistencyStatus = consistencyStatus;
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
    
    public String getConflictDescription() {
        return conflictDescription;
    }
    
    public void setConflictDescription(String conflictDescription) {
        this.conflictDescription = conflictDescription;
    }
    
    public String getCausalChainStatus() {
        return causalChainStatus;
    }
    
    public void setCausalChainStatus(String causalChainStatus) {
        this.causalChainStatus = causalChainStatus;
    }
    
    public String getConsistencyStatus() {
        return consistencyStatus;
    }
    
    public void setConsistencyStatus(String consistencyStatus) {
        this.consistencyStatus = consistencyStatus;
    }
}