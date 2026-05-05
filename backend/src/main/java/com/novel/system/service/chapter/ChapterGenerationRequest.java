package com.novel.system.service.chapter;

/**
 * 章节生成请求
 */
public class ChapterGenerationRequest {
    
    private String novelId;
    private Integer chapterNum;
    private String chapterTitle; // 章节标题（可选，手动撰写时使用）
    private String userIntent; // 用户本章意图（可选）
    private Integer selectedIdeaIndex; // 用户选择的实现思路索引
    private String modelType; // 模型类型（兼容旧接口）
    private String llmConfigId; // 模型配置ID（优先使用）
    private String styleCardId; // 文风卡片ID（可选，用于注入文风风格）

    public ChapterGenerationRequest() {
    }
    
    public ChapterGenerationRequest(String novelId, Integer chapterNum, String userIntent, Integer selectedIdeaIndex, String modelType) {
        this.novelId = novelId;
        this.chapterNum = chapterNum;
        this.userIntent = userIntent;
        this.selectedIdeaIndex = selectedIdeaIndex;
        this.modelType = modelType;
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
    
    public String getUserIntent() {
        return userIntent;
    }
    
    public void setUserIntent(String userIntent) {
        this.userIntent = userIntent;
    }
    
    public Integer getSelectedIdeaIndex() {
        return selectedIdeaIndex;
    }
    
    public void setSelectedIdeaIndex(Integer selectedIdeaIndex) {
        this.selectedIdeaIndex = selectedIdeaIndex;
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

    public String getStyleCardId() {
        return styleCardId;
    }

    public void setStyleCardId(String styleCardId) {
        this.styleCardId = styleCardId;
    }
}
