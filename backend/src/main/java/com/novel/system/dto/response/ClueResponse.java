package com.novel.system.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 伏笔信息响应DTO
 * 同时包含后端字段名和前端期望的字段名（兼容）
 */
@Data
public class ClueResponse {
    
    /** 伏笔ID */
    private String clueId;
    /** 所属小说ID */
    private String novelId;
    /** 伏笔内容描述 */
    private String clueContent;
    /** 优先级：1-高，2-中，3-低 */
    private Integer priority;
    /** 埋设章节号 */
    private Integer buryChapter;
    /** 回收章节号（null表示未回收） */
    private Integer recycleChapter;
    /** 状态：0-未回收，1-已回收 */
    private Integer status;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
    
    public ClueResponse() {}
    
    // ========== 前端兼容字段 ==========
    
    /** 前端期望 title（后端是 clueContent） */
    @JsonProperty("title")
    public String getTitle() {
        return clueContent;
    }
    
    /** 前端期望 description（后端是 clueContent） */
    @JsonProperty("description")
    public String getDescription() {
        return clueContent;
    }
    
    /** 前端期望 clueType（根据 priority 映射） */
    @JsonProperty("clueType")
    public String getClueType() {
        if (priority == null) return "支线伏笔";
        return switch (priority) {
            case 1 -> "主线伏笔";
            case 2 -> "支线伏笔";
            default -> "暗线";
        };
    }
    
    /** 前端期望 plantedChapter（后端是 buryChapter） */
    @JsonProperty("plantedChapter")
    public Integer getPlantedChapter() {
        return buryChapter;
    }
}
