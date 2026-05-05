package com.novel.system.entity.mysql;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 伏笔账本实体
 */
@Data
public class ClueBook {
    /** 伏笔ID，主键 */
    private String clueId;
    /** 所属小说ID */
    private String novelId;
    /** 伏笔内容描述 */
    private String clueContent;
    /** 优先级：1-高，2-中，3-低 */
    private Integer priority = 2;
    /** 埋设章节号 */
    private Integer buryChapter;
    /** 回收章节号（null表示未回收） */
    private Integer recycleChapter;
    /** 状态：0-未回收，1-已回收 */
    private Integer status = 0;
    /** 关联人物ID，逗号分隔 */
    private String relatedCharacters;
    /** 关联地点ID，逗号分隔 */
    private String relatedLocations;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
    
    /**
     * 伏笔回收截止章节
     * 建议在该章节前回收伏笔，用于 deadline 紧急提示
     */
    private Integer deadline;
}
