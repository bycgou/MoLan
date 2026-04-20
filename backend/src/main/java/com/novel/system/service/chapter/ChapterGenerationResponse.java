package com.novel.system.service.chapter;

import java.util.List;

/**
 * 章节生成响应
 */
public class ChapterGenerationResponse {
    
    private Boolean success;
    private String chapterId;
    private String chapterTitle;
    private String chapterOutline;
    private List<String> implementationIdeas;
    private Integer selectedIdeaIndex;
    private String content;
    private Integer wordCount;
    private String plotFingerprint;
    private String errorMessage;
    private GenerationStatus status;
    
    public ChapterGenerationResponse() {
    }
    
    public ChapterGenerationResponse(Boolean success, String chapterId, String chapterTitle, String chapterOutline, List<String> implementationIdeas, Integer selectedIdeaIndex, String content, Integer wordCount, String plotFingerprint, String errorMessage, GenerationStatus status) {
        this.success = success;
        this.chapterId = chapterId;
        this.chapterTitle = chapterTitle;
        this.chapterOutline = chapterOutline;
        this.implementationIdeas = implementationIdeas;
        this.selectedIdeaIndex = selectedIdeaIndex;
        this.content = content;
        this.wordCount = wordCount;
        this.plotFingerprint = plotFingerprint;
        this.errorMessage = errorMessage;
        this.status = status;
    }
    
    public Boolean getSuccess() {
        return success;
    }
    
    public void setSuccess(Boolean success) {
        this.success = success;
    }
    
    public String getChapterId() {
        return chapterId;
    }
    
    public void setChapterId(String chapterId) {
        this.chapterId = chapterId;
    }
    
    public String getChapterTitle() {
        return chapterTitle;
    }
    
    public void setChapterTitle(String chapterTitle) {
        this.chapterTitle = chapterTitle;
    }
    
    public String getChapterOutline() {
        return chapterOutline;
    }
    
    public void setChapterOutline(String chapterOutline) {
        this.chapterOutline = chapterOutline;
    }
    
    public List<String> getImplementationIdeas() {
        return implementationIdeas;
    }
    
    public void setImplementationIdeas(List<String> implementationIdeas) {
        this.implementationIdeas = implementationIdeas;
    }
    
    public Integer getSelectedIdeaIndex() {
        return selectedIdeaIndex;
    }
    
    public void setSelectedIdeaIndex(Integer selectedIdeaIndex) {
        this.selectedIdeaIndex = selectedIdeaIndex;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public Integer getWordCount() {
        return wordCount;
    }
    
    public void setWordCount(Integer wordCount) {
        this.wordCount = wordCount;
    }
    
    public String getPlotFingerprint() {
        return plotFingerprint;
    }
    
    public void setPlotFingerprint(String plotFingerprint) {
        this.plotFingerprint = plotFingerprint;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public GenerationStatus getStatus() {
        return status;
    }
    
    public void setStatus(GenerationStatus status) {
        this.status = status;
    }
    
    public enum GenerationStatus {
        OUTLINE_GENERATED, // 大纲已生成，等待用户选择思路
        GENERATING, // 正在生成
        VALIDATING, // 正在校验
        COMPLETED, // 生成完成
        FAILED // 生成失败
    }
}
