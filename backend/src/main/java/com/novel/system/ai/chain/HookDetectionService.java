package com.novel.system.ai.chain;

import lombok.Data;

/**
 * 钩子强度检测服务接口
 */
public interface HookDetectionService {
    
    /**
     * 检测章节钩子强度
     */
    HookAnalysis analyzeHook(String chapterContent);
    
    /**
     * 钩子分析结果
     */
    @Data
    class HookAnalysis {
        private int openingAttraction;      // 开头吸引力 (0-10)
        private int suspenseSetup;          // 悬念设置 (0-10)
        private int emotionTrigger;         // 情绪调动 (0-10)
        private int endingHook;             // 结尾钩子 (0-10)
        private int coolPointDensity;       // 爽点密度 (0-10)
        private int totalScore;             // 总分 (0-50)
        private String pullLevel;           // 追读力等级：高/中/低
        private String suggestion;          // 改进建议
        
        /**
         * 计算总分和等级
         */
        public void calculateScore() {
            totalScore = openingAttraction + suspenseSetup + emotionTrigger + endingHook + coolPointDensity;
            
            if (totalScore >= 40) {
                pullLevel = "高";
            } else if (totalScore >= 30) {
                pullLevel = "中";
            } else {
                pullLevel = "低";
            }
        }
    }
}
