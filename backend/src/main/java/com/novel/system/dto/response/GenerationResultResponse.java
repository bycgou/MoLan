package com.novel.system.dto.response;

/**
 * 生成结果响应DTO
 */
public class GenerationResultResponse {
    
    /** 是否生成成功 */
    private Boolean success;
    /** 生成的章节ID */
    private String chapterId;
    /** 章节标题 */
    private String chapterTitle;
    /** 章节正文内容 */
    private String content;
    /** 章节字数 */
    private Integer wordCount;
    /** 剧情指纹（300字摘要） */
    private String plotFingerprint;
    /** 生成状态描述 */
    private String status;
    /** 错误信息（生成失败时） */
    private String errorMessage;
    
    public GenerationResultResponse() {
    }
    
    public GenerationResultResponse(Boolean success, String chapterId, String chapterTitle, String content, Integer wordCount, String plotFingerprint, String status, String errorMessage) {
        this.success = success;
        this.chapterId = chapterId;
        this.chapterTitle = chapterTitle;
        this.content = content;
        this.wordCount = wordCount;
        this.plotFingerprint = plotFingerprint;
        this.status = status;
        this.errorMessage = errorMessage;
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
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
