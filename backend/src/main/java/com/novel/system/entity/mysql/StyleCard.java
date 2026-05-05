package com.novel.system.entity.mysql;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 文风卡片实体
 * 
 * 【核心作用】
 * 存储从上传小说中提取的文风特征，可复用
 * 
 * 【设计理念】
 * - 独立可复用的"风格资产"，而非绑定在小说上的"属性"
 * - 结构化规则（机器可读）+ 自然语言描述（prompt注入）+ 原文样本（few-shot）
 * 
 * @see com.novel.system.service.style.StyleCardService
 */
@Data
@TableName("style_card")
public class StyleCard {
    
    /**
     * 卡片ID
     */
    @TableId
    private String cardId;
    
    /**
     * 卡片名称（如：诡秘之主风格）
     */
    private String name;
    
    /**
     * 来源小说名称
     */
    private String sourceNovel;
    
    /**
     * 作者名称
     */
    private String authorName;
    
    /**
     * 创建者用户ID
     */
    private String createdBy;
    
    /**
     * 节奏规则（JSON）
     * {
     *   "avg_sentence_length": 18,
     *   "short_sentence_ratio": 0.35,
     *   "long_sentence_max": 45,
     *   "avg_paragraph_length": 80,
     *   "short_paragraph_ratio": 0.25
     * }
     */
    @TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private Object rhythmRules;
    
    /**
     * 对话规则（JSON）
     * {
     *   "ratio": 0.30,
     *   "avg_line_length": 12,
     *   "style": "简洁留白型，角色话少，潜台词多",
     *   "quirks": ["常用反问句", "对话中夹杂内心独白"]
     * }
     */
    @TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private Object dialogueRules;
    
    /**
     * 描写规则（JSON）
     * {
     *   "dominant_sense": "视觉",
     *   "sense_distribution": {"visual": 0.50, "auditory": 0.20, ...},
     *   "metaphor_type": "偏好用神秘学相关的比喻",
     *   "adjective_density": "低"
     * }
     */
    @TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private Object descriptionRules;
    
    /**
     * 情绪表达规则（JSON）
     * {
     *   "anger": "通过克制的外部反应而非爆发",
     *   "fear": "环境描写先行，再写心理",
     *   "forbidden": ["禁止'心中涌起一股...'", ...]
     * }
     */
    @TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private Object emotionRules;
    
    /**
     * 段落结构规则（JSON）
     * {
     *   "scene_transition": "硬切，不解释",
     *   "chapter_ending": "停在悬念处，不总结",
     *   "pov_discipline": "严格第三人称限知"
     * }
     */
    @TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private Object structureRules;
    
    /**
     * 禁用词汇和句式（JSON）
     * {
     *   "words": ["不禁", "一股暖流", "嘴角微微上扬"],
     *   "structures": ["XX心想：...", "他感到一种...的感觉"]
     * }
     */
    @TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private Object forbiddenRules;
    
    /**
     * 偏好词汇和句式（JSON）
     * {
     *   "words": ["沉吟", "念头", "注视"],
     *   "structures": ["短句独立成段制造停顿感", "用环境描写收尾"]
     * }
     */
    @TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private Object preferredRules;
    
    /**
     * 文风的自然语言描述（用于prompt注入）
     */
    private String styleDescription;
    
    /**
     * 生成指令模板
     */
    private String generationTemplate;
    
    /**
     * 原文样本片段（JSON）
     * [
     *   {"type": "悬疑氛围", "text": "...", "annotation": "短句堆叠制造紧张感"},
     *   {"type": "日常对话", "text": "...", "annotation": "极简对话，用环境细节填充沉默"}
     * ]
     */
    @TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private Object samplesJson;
    
    /**
     * 程序化提取的统计数据（JSON）
     * {
     *   "avg_sentence_length": 18.5,
     *   "short_sentence_ratio": 0.35,
     *   "dialogue_ratio": 0.30,
     *   "ellipsis_count": 15,
     *   "em_dash_count": 8
     * }
     */
    @TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private Object statsJson;
    
    /**
     * 标签，逗号分隔（如：克苏鲁,蒸汽朋克,悬疑）
     */
    private String tags;
    
    /**
     * 是否公开
     */
    private Boolean isPublic;
    
    /**
     * 使用次数
     */
    private Integer usageCount;
    
    /**
     * 平均评分
     */
    private BigDecimal avgRating;
    
    /**
     * 状态：0=分析中, 1=可用, 2=分析失败
     */
    private Integer status;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
