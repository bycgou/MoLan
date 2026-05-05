package com.novel.system.entity.mysql;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 小说基础信息实体
 */
@Data
public class NovelBase {
    /** 小说ID，主键 */
    private String novelId;
    /** 小说名称 */
    private String novelName;
    /** 所属用户ID */
    private String userId;
    /** 小说题材（如：玄幻、言情、悬疑等） */
    private String genre;
    /** 小说类型标签，如["爽文","穿越","重生"] */
    private String novelType;
    /** 故事基调，如["轻松","温馨","搞笑"] */
    private String tone;
    /** 禁止雷点，如["NTR","虐主","悲剧"] */
    private String tabooList;
    /** 小说总纲，包含主线剧情、卷划分、高潮节点、结局等 */
    private String totalOutline;
    /** 世界观设定，包含力量体系、地理环境、社会势力等 */
    private String worldSetting;
    /** 总字数 */
    private Integer wordCount = 0;
    /** 总章节数 */
    private Integer chapterCount = 0;
    /** 状态：0-草稿，1-连载中，2-已完结 */
    private Integer status = 0;
    /** 是否已删除（软删除） */
    private Boolean isDeleted = false;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
    
    /**
     * 故事势能摘要
     * 用于追踪故事张力变化趋势，如：上升、下降、转折、高潮前蓄力、高潮后余波
     */
    private String storyMomentum;
    
    /**
     * 待回收伏笔追踪（JSON格式）
     * 记录当前未回收的伏笔信息，包括伏笔描述和建议回收章节
     */
    private String pendingForeshadowing;
}
