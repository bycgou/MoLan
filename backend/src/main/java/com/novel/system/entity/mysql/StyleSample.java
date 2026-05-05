package com.novel.system.entity.mysql;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文风样本片段实体
 * 
 * 【核心作用】
 * 存储从上传小说中抽取的代表性段落，用于few-shot学习
 * 
 * 【样本类型】
 * - 悬疑/紧张氛围
 * - 日常对话
 * - 战斗场景
 * - 心理描写
 * - 环境描写
 * 
 * @see StyleCard
 */
@Data
@TableName("style_sample")
public class StyleSample {
    
    /**
     * 样本ID
     */
    @TableId
    private String sampleId;
    
    /**
     * 关联的文风卡片ID
     */
    private String cardId;
    
    /**
     * 样本类型：悬疑/对话/战斗/心理/描写
     */
    private String sampleType;
    
    /**
     * 样本内容（原文片段）
     */
    private String content;
    
    /**
     * AI对这段的风格注释
     * 例如："短句堆叠制造紧张感，感官先行，不解释原因"
     */
    private String annotation;
    
    /**
     * 排序顺序
     */
    private Integer sortOrder;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
