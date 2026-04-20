package com.novel.system.ai.agent;

/**
 * 摘要Agent请求
 */
public class SummaryAgentRequest {
    
    private String novelId;
    private Integer chapterNum;
    private String chapterTitle;
    private String chapterContent;
    private String chapterOutline;
    private String novelGenre;
    private String modelType; // claude/qwen
    private String llmConfigId;
    
    public SummaryAgentRequest() {
    }
    
    public SummaryAgentRequest(String novelId, Integer chapterNum, String chapterTitle, String chapterContent, String chapterOutline, String novelGenre, String modelType, String llmConfigId) {
        this.novelId = novelId;
        this.chapterNum = chapterNum;
        this.chapterTitle = chapterTitle;
        this.chapterContent = chapterContent;
        this.chapterOutline = chapterOutline;
        this.novelGenre = novelGenre;
        this.modelType = modelType;
        this.llmConfigId = llmConfigId;
    }
    
    public String getNovelId() {
        return novelId;
    }
    
    public void setNovelId(String novelId) {
        this.novelId = novelId;
    }
    
    public Integer getChapterNum() {
        return chapterNum;
    }
    
    public void setChapterNum(Integer chapterNum) {
        this.chapterNum = chapterNum;
    }
    
    public String getChapterTitle() {
        return chapterTitle;
    }
    
    public void setChapterTitle(String chapterTitle) {
        this.chapterTitle = chapterTitle;
    }
    
    public String getChapterContent() {
        return chapterContent;
    }
    
    public void setChapterContent(String chapterContent) {
        this.chapterContent = chapterContent;
    }
    
    public String getChapterOutline() {
        return chapterOutline;
    }
    
    public void setChapterOutline(String chapterOutline) {
        this.chapterOutline = chapterOutline;
    }
    
    public String getNovelGenre() {
        return novelGenre;
    }
    
    public void setNovelGenre(String novelGenre) {
        this.novelGenre = novelGenre;
    }
    
    public String getModelType() {
        return modelType;
    }
    
    public void setModelType(String modelType) {
        this.modelType = modelType;
    }
    
    public String getLlmConfigId() {
        return llmConfigId;
    }
    
    public void setLlmConfigId(String llmConfigId) {
        this.llmConfigId = llmConfigId;
    }
}
