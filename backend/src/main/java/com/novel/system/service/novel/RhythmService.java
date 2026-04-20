package com.novel.system.service.novel;

import lombok.Data;
import java.util.List;

/**
 * 节奏系统服务接口 - 包括追读力系统
 */
public interface RhythmService {
    
    /**
     * 分析章节追读力
     */
    ReadPullAnalysis analyzeReadPull(String chapterContent, String novelId, int chapterNum);
    
    /**
     * 计算小说整体追读力趋势
     */
    ReadPullTrend calculateReadPullTrend(String novelId);
    
    /**
     * 生成追读力优化建议
     */
    String generateOptimizationSuggestion(String novelId);
    
    /**
     * 追读力分析结果
     */
    @Data
    class ReadPullAnalysis {
        private int hookScore;          // 钩子强度 (0-10)
        private int coolPointScore;     // 爽点强度 (0-10)
        private int microFulfillment;   // 微兑现度 (0-10)
        private int debtScore;          // 债务强度 (0-10)
        private int totalScore;         // 总追读力 (0-40)
        private String readPullLevel;   // 追读力等级：高/中/低
        private List<String> coolPoints; // 识别到的爽点
        private List<String> hooks;      // 识别到的钩子
        private List<Debt> debts;       // 识别到的债务
        private List<MicroFulfillment> fulfillments; // 识别到的微兑现
        private String suggestion;       // 改进建议
        
        /**
         * 计算总分和等级
         */
        public void calculateScore() {
            totalScore = hookScore + coolPointScore + microFulfillment + debtScore;
            
            if (totalScore >= 32) {
                readPullLevel = "高";
            } else if (totalScore >= 24) {
                readPullLevel = "中";
            } else {
                readPullLevel = "低";
            }
        }
    }
    
    /**
     * 追读力趋势
     */
    @Data
    class ReadPullTrend {
        private List<Integer> chapterNumbers;
        private List<Integer> readPullScores;
        private List<String> readPullLevels;
        private double averageScore;
        private String trendAnalysis;
    }
    
    /**
     * 债务（未解决的悬念或冲突）
     */
    @Data
    class Debt {
        private String description;      // 债务描述
        private int chapterNum;          // 出现章节
        private int priority;            // 优先级 (1-5)
        private boolean resolved;        // 是否已解决
    }
    
    /**
     * 微兑现（小目标的实现）
     */
    @Data
    class MicroFulfillment {
        private String description;      // 兑现描述
        private int chapterNum;          // 兑现章节
        private int satisfaction;        // 满意度 (0-10)
    }
}