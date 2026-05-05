package com.novel.system.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

/**
 * 生成章节请求DTO - 兼容前后端字段名
 */
public class GenerateChapterRequest {
    
    /** 小说ID */
    private String novelId;
    
    /** 章节序号 */
    private Integer chapterNum;
    
    /** 用户本章意图/写作要求 */
    private String userIntent;
    
    /** 选择的实现思路索引（从AI生成的多个思路中选一个） */
    private Integer selectedIdeaIndex;
    
    /** 模型类型：claude/qwen（兼容旧接口，优先使用llmConfigId） */
    private String modelType;
    
    /** 模型配置ID（优先使用，对应LlmConfig的configId） */
    private String llmConfigId;

    /** 文风卡片ID（可选） */
    private String styleCardId;
    
    /** 前端发送 configId → 映射到 llmConfigId */
    @JsonAlias({"configId"})
    public void setConfigId(String configId) {
        this.llmConfigId = configId;
    }
    
    /** 前端发送 strandType → 映射到 userIntent */
    @JsonAlias({"strandType"})
    public void setStrandType(String strandType) {
        if (strandType != null && this.userIntent == null) {
            this.userIntent = "Strand类型: " + strandType;
        }
    }
    
    // ========== 标准 getter/setter ==========
    
    public String getNovelId() { return novelId; }
    public void setNovelId(String novelId) { this.novelId = novelId; }
    
    public Integer getChapterNum() { return chapterNum; }
    public void setChapterNum(Integer chapterNum) { this.chapterNum = chapterNum; }
    
    public String getUserIntent() { return userIntent; }
    public void setUserIntent(String userIntent) { this.userIntent = userIntent; }
    
    public Integer getSelectedIdeaIndex() { return selectedIdeaIndex; }
    public void setSelectedIdeaIndex(Integer selectedIdeaIndex) { this.selectedIdeaIndex = selectedIdeaIndex; }
    
    public String getModelType() { return modelType; }
    public void setModelType(String modelType) { this.modelType = modelType; }
    
    public String getLlmConfigId() { return llmConfigId; }
    public void setLlmConfigId(String llmConfigId) { this.llmConfigId = llmConfigId; }

    public String getStyleCardId() { return styleCardId; }
    public void setStyleCardId(String styleCardId) { this.styleCardId = styleCardId; }
}
