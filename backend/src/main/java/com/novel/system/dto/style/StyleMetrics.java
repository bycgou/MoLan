package com.novel.system.dto.style;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 文风量化指标DTO
 * 
 * 【核心作用】
 * 程序化提取的文本统计指标，用于：
 * 1. 量化描述文风特征
 * 2. 作为LLM分析的输入数据
 * 3. 质量检查的对比基准
 * 
 * @see com.novel.system.service.style.StyleTextAnalyzer
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StyleMetrics {
    
    /**
     * 平均句长（字）
     */
    private double avgSentenceLength;
    
    /**
     * 短句占比（<15字的句子）
     */
    private double shortSentenceRatio;
    
    /**
     * 最长句子长度
     */
    private int maxSentenceLength;
    
    /**
     * 平均段落长度（字）
     */
    private double avgParagraphLength;
    
    /**
     * 短段落占比（<60字的段落）
     */
    private double shortParagraphRatio;
    
    /**
     * 对话占全文比例
     */
    private double dialogueRatio;
    
    /**
     * 平均对白长度（字）
     */
    private double avgDialogueLength;
    
    /**
     * 省略号数量
     */
    private int ellipsisCount;
    
    /**
     * 破折号数量
     */
    private int emDashCount;
    
    /**
     * 感叹号数量
     */
    private int exclamationCount;
    
    /**
     * 问号数量
     */
    private int questionMarkCount;
    
    /**
     * 视觉词密度（每千字）
     */
    private double visualDensity;
    
    /**
     * 听觉词密度（每千字）
     */
    private double auditoryDensity;
    
    /**
     * 触觉词密度（每千字）
     */
    private double tactileDensity;
    
    /**
     * 高频特色词（TF-IDF提取）
     */
    private List<String> distinctiveWords;
    
    /**
     * 总字数
     */
    private int totalWords;
    
    /**
     * 总句数
     */
    private int totalSentences;
    
    /**
     * 总段落数
     */
    private int totalParagraphs;
    
    /**
     * 对话段落数
     */
    private int dialogueParagraphs;
}
