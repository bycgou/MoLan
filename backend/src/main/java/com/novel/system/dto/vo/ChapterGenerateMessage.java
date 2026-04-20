package com.novel.system.dto.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 章节生成消息DTO - RabbitMQ消息体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChapterGenerateMessage implements Serializable {
    /** 异步任务ID */
    private String taskId;
    /** 小说ID */
    private String novelId;
    /** 章节序号 */
    private Integer chapterNum;
    /** 模型配置ID */
    private String llmConfigId;
    /** 用户意图/写作要求 */
    private String userIntent;
    /** 选择的实现思路索引 */
    private Integer selectedIdeaIndex;
}
