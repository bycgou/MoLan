package com.novel.system.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

/**
 * 创建伏笔请求DTO - 兼容前后端字段名
 */
public class CreateClueRequest {
    
    /** 伏笔内容描述 */
    @NotBlank(message = "伏笔内容不能为空")
    private String clueContent;
    
    /** 优先级：1-高，2-中，3-低 */
    private Integer priority;
    
    /** 埋设章节号 */
    private Integer buryChapter;

    /**
     * 伏笔回收截止章节（deadline）
     * 不传时由后端按默认期限策略自动推导：埋设章节 + DEFAULT_DEADLINE_CHAPTERS
     */
    private Integer deadline;

    /** 关联人物ID，逗号分隔 */
    private String relatedCharacters;
    
    /** 关联地点ID，逗号分隔 */
    private String relatedLocations;
    
    // ========== 前端兼容字段 ==========
    
    /** 前端发送 title → 映射到 clueContent */
    @JsonAlias({"title"})
    public void setTitle(String title) {
        if (title != null && this.clueContent == null) {
            this.clueContent = title;
        }
    }
    
    /** 前端发送 description → 拼接到 clueContent */
    @JsonAlias({"description"})
    public void setDescription(String description) {
        if (description != null) {
            if (this.clueContent == null) {
                this.clueContent = description;
            } else {
                this.clueContent = this.clueContent + "\n" + description;
            }
        }
    }
    
    /** 前端发送 plantedChapter → 映射到 buryChapter */
    @JsonAlias({"plantedChapter"})
    public void setPlantedChapter(Integer chapter) {
        this.buryChapter = chapter;
    }

    /** 前端发送 deadlineChapter → 映射到 deadline */
    @JsonAlias({"deadlineChapter"})
    public void setDeadlineChapter(Integer chapter) {
        this.deadline = chapter;
    }

    // ========== 标准 getter/setter ==========
    
    public String getClueContent() { return clueContent; }
    public void setClueContent(String clueContent) { this.clueContent = clueContent; }
    
    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
    
    public Integer getBuryChapter() { return buryChapter; }
    public void setBuryChapter(Integer buryChapter) { this.buryChapter = buryChapter; }

    public Integer getDeadline() { return deadline; }
    public void setDeadline(Integer deadline) { this.deadline = deadline; }

    public String getRelatedCharacters() { return relatedCharacters; }
    public void setRelatedCharacters(String relatedCharacters) { this.relatedCharacters = relatedCharacters; }
    
    public String getRelatedLocations() { return relatedLocations; }
    public void setRelatedLocations(String relatedLocations) { this.relatedLocations = relatedLocations; }
}
