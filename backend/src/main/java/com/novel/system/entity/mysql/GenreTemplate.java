package com.novel.system.entity.mysql;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 题材模板实体
 */
@Data
public class GenreTemplate {
    /** 模板ID，主键 */
    private Long templateId;
    /** 模板名称 */
    private String templateName;
    /** 题材编码（如：xuanhuan、dog-blood-romance等） */
    private String genre;
    /** 题材描述 */
    private String description;
    /** 推荐标签，逗号分隔 */
    private String recommendedTags;
    /** 剧情结构模板 */
    private String plotStructure;
    /** 人物设定模板 */
    private String characterSetting;
    /** 风格特征描述 */
    private String styleFeatures;
    /** 开篇示例文本 */
    private String sampleOpening;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
    /** 状态：0-禁用，1-启用 */
    private Integer status;
}
