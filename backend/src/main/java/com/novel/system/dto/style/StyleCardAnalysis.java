package com.novel.system.dto.style;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 文风卡片分析结果DTO
 * 
 * 【核心作用】
 * LLM分析后返回的结构化结果，用于填充StyleCard实体
 * 
 * @see com.novel.system.service.style.StyleCardService
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StyleCardAnalysis {
    
    /**
     * 节奏规则
     */
    private RhythmRule rhythm;
    
    /**
     * 对话规则
     */
    private DialogueRule dialogue;
    
    /**
     * 描写规则
     */
    private DescriptionRule description;
    
    /**
     * 情绪表达规则
     */
    private EmotionRule emotion;
    
    /**
     * 段落结构规则
     */
    private StructureRule structure;
    
    /**
     * 禁用规则
     */
    private ForbiddenRule forbidden;
    
    /**
     * 偏好规则
     */
    private PreferredRule preferred;
    
    /**
     * 原文样本
     */
    private List<StyleSampleItem> samples;
    
    /**
     * 文风的自然语言描述
     */
    @JsonProperty("style_description")
    private String styleDescription;
    
    /**
     * 生成指令模板
     */
    @JsonProperty("generation_template")
    private String generationTemplate;

    /**
     * 风格一致性报告
     */
    private ConsistencyReport consistency;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RhythmRule {
        private String description;
        private String rule;
        @JsonProperty("avg_sentence_length")
        private Integer avgSentenceLength;
        @JsonProperty("short_sentence_ratio")
        private Double shortSentenceRatio;
        @JsonProperty("avg_paragraph_length")
        private Integer avgParagraphLength;
        @JsonProperty("short_paragraph_ratio")
        private Double shortParagraphRatio;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DialogueRule {
        private String style;
        private List<String> quirks;
        private String rule;
        private Double ratio;
        @JsonProperty("avg_line_length")
        private Integer avgLineLength;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DescriptionRule {
        @JsonProperty("dominant_sense")
        private String dominantSense;
        @JsonProperty("metaphor_type")
        private String metaphorType;
        private String rule;
        @JsonProperty("sense_distribution")
        private Map<String, Double> senseDistribution;
        @JsonProperty("adjective_density")
        private String adjectiveDensity;
        @JsonProperty("detail_approach")
        private String detailApproach;
        @JsonProperty("rhetoric_preferences")
        private List<String> rhetoricPreferences;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmotionRule {
        private String anger;
        private String fear;
        private String joy;
        private String sadness;
        private List<String> forbidden;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StructureRule {
        @JsonProperty("scene_transition")
        private String sceneTransition;
        @JsonProperty("chapter_ending")
        private String chapterEnding;
        @JsonProperty("pov_discipline")
        private String povDiscipline;
        @JsonProperty("pov_shifts")
        private List<String> povShifts;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ForbiddenRule {
        private List<String> words;
        private List<String> structures;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PreferredRule {
        private List<String> words;
        private List<String> structures;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConsistencyReport {
        @JsonProperty("overall_score")
        private Double overallScore;
        @JsonProperty("drift_description")
        private String driftDescription;
        @JsonProperty("early_style")
        private List<String> earlyStyle;
        @JsonProperty("late_style")
        private List<String> lateStyle;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StyleSampleItem {
        private String type;
        private String text;
        private String annotation;
    }
}
