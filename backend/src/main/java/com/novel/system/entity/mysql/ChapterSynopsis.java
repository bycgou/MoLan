package com.novel.system.entity.mysql;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 章节简介蓝图实体
 * 介于大纲和正文的中间层，提供详细的写作指导
 */
@Data
@TableName("chapter_synopses")
public class ChapterSynopsis {
    
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
     * 小说ID
     */
    private String novelId;
    
    /**
     * 章节序号
     */
    private Integer chapterNum;
    
    /**
     * 章节简介内容
     */
    private String synopsis;
    
    /**
     * 场景分解（JSON格式）
     */
    private String sceneBreakdown;
    
    /**
     * 对话要点（JSON格式）
     */
    private String dialoguePoints;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}