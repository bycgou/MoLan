package com.novel.system.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

/**
 * 章节信息响应DTO
 * 同时包含后端字段名和前端期望的字段名（兼容）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChapterResponse {
    
    /** 章节ID */
    private String chapterId;
    /** 所属小说ID */
    private String novelId;
    /** 章节序号 */
    private Integer chapterNum;
    /** 章节标题 */
    private String chapterTitle;
    /** 章节大纲/章纲内容 */
    private String chapterOutline;
    /** 章节正文内容 */
    private String content;
    /** 章节字数 */
    private Integer wordCount;
    /** 节奏标签：quest-主线/fire-感情线/constellation-世界观线 */
    private String rhythmTag;
    /** 剧情指纹（300字摘要） */
    private String plotFingerprint;
    /** 创建时间 */
    private LocalDateTime createTime;
    
    // ========== 前端兼容字段 ==========
    
    /** 前端期望 strandType（后端是 rhythmTag） */
    @JsonProperty("strandType")
    public String getStrandType() {
        return rhythmTag;
    }
    
    /** 前端期望 summary（后端是 plotFingerprint） */
    @JsonProperty("summary")
    public String getSummary() {
        return plotFingerprint;
    }
    
    /** 前端期望 status（后端是 generationStatus，这里默认 2=已生成） */
    @JsonProperty("status")
    public Integer getStatus() {
        return 2;
    }
    
    /** 前端期望 updateTime（使用 createTime 兜底） */
    @JsonProperty("updateTime")
    public LocalDateTime getUpdateTime() {
        return createTime;
    }
}
