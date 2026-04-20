package com.novel.system.entity.mysql;

import lombok.Data;
import java.time.LocalDateTime;

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
}
