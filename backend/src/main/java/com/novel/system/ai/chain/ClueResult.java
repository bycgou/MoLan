package com.novel.system.ai.chain;

/**
 * 伏笔回收验证结果
 */
public class ClueResult {
    
    private boolean passed;
    private String message;
    private int buriedCount;
    private int recycledCount;
    private String missingClues;
    private String unnaturalRecycle;
    
    public ClueResult() {
    }
    
    public ClueResult(boolean passed, String message, int buriedCount, int recycledCount, String missingClues, String unnaturalRecycle) {
        this.passed = passed;
        this.message = message;
        this.buriedCount = buriedCount;
        this.recycledCount = recycledCount;
        this.missingClues = missingClues;
        this.unnaturalRecycle = unnaturalRecycle;
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
    
    public int getBuriedCount() {
        return buriedCount;
    }
    
    public void setBuriedCount(int buriedCount) {
        this.buriedCount = buriedCount;
    }
    
    public int getRecycledCount() {
        return recycledCount;
    }
    
    public void setRecycledCount(int recycledCount) {
        this.recycledCount = recycledCount;
    }
    
    public String getMissingClues() {
        return missingClues;
    }
    
    public void setMissingClues(String missingClues) {
        this.missingClues = missingClues;
    }
    
    public String getUnnaturalRecycle() {
        return unnaturalRecycle;
    }
    
    public void setUnnaturalRecycle(String unnaturalRecycle) {
        this.unnaturalRecycle = unnaturalRecycle;
    }
}