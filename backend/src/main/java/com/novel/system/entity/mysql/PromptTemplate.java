package com.novel.system.entity.mysql;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * Prompt模板配置实体
 */
@Data
public class PromptTemplate {
    /** 模板ID，主键 */
    private String templateId;
    /** Agent类型：outline/writing/summary/audit/ooc_validator */
    private String agentType;
    /** 模板名称 */
    private String templateName;
    /** 系统指令内容（Prompt正文） */
    private String systemInstruction;
    /** JSON格式的few-shot示例 */
    private String fewShotExamples;
    /** 模板版本号 */
    private Integer version = 1;
    /** 是否激活：0-禁用，1-激活 */
    private Integer isActive = 1;
    /** 模板描述 */
    private String description;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
}
