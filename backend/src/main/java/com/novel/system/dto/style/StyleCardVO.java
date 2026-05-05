package com.novel.system.dto.style;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 文风卡片VO
 *
 * 统一前后端数据格式，解决以下问题：
 * 1. LocalDateTime格式化为字符串
 * 2. JSON字段统一为驼峰命名
 * 3. samplesJson结构统一为 {type, text, annotation}
 * 4. null值提供默认值
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StyleCardVO {

    private String cardId;
    private String name;
    private String sourceNovel;
    private String authorName;
    private String createdBy;

    private Map<String, Object> rhythmRules;
    private Map<String, Object> dialogueRules;
    private Map<String, Object> descriptionRules;
    private Map<String, Object> emotionRules;
    private Map<String, Object> structureRules;
    private Map<String, Object> forbiddenRules;
    private Map<String, Object> preferredRules;

    private String styleDescription;
    private String generationTemplate;

    private List<SampleItem> samplesJson;
    private StyleMetrics statsJson;

    private String tags;
    private boolean isPublic;
    private int usageCount;
    private BigDecimal avgRating;

    private int status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SampleItem {
        private String type;
        private String text;
        private String annotation;
    }
}
