package com.novel.system.entity.mysql;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 章节版本快照实体
 * 用于存储章节的历史版本，支持版本回溯
 */
@Data
@TableName("chapter_versions")
public class ChapterVersion {
    
    /**
     * 主键ID，自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 章节ID
     */
    private String chapterId;
    
    /**
     * 版本号
     */
    private Integer version;
    
    /**
     * 章节内容
     */
    private String content;
    
    /**
     * 章节大纲
     */
    private String outline;
    
    /**
     * 章节标题
     */
    private String title;
    
    /**
     * 字数
     */
    private Integer words;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}