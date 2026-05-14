package com.novel.system.entity.mysql;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 弧段摘要实体
 * 用于存储小说弧段的摘要信息，属于第四层滑动窗口 — 子层4a：弧段摘要（全局历史记忆）
 */
@Data
@TableName("arc_summaries")
public class ArcSummary {
    
    /**
     * 主键ID，自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 小说ID
     */
    private String novelId;
    
    /**
     * 弧段起始章节号
     */
    private Integer chapterFrom;
    
    /**
     * 弧段结束章节号
     */
    private Integer chapterTo;
    
    /**
     * 弧段摘要内容
     */
    private String summary;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}