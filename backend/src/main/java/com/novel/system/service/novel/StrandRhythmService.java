package com.novel.system.service.novel;

import lombok.Data;
import java.util.List;

/**
 * Strand节奏系统服务接口
 * 
 * Strand Weave节奏系统：
 * - Quest（主线）：推动核心冲突，占比60%
 * - Fire（感情线）：人物关系发展，占比20%
 * - Constellation（世界观）：背景/势力/设定扩展，占比20%
 */
public interface StrandRhythmService {
    
    /**
     * Strand类型枚举
     */
    enum StrandType {
        QUEST,          // 主线
        FIRE,           // 感情线
        CONSTELLATION   // 世界观
    }
    
    /**
     * 获取当前节奏状态
     */
    RhythmStatus getRhythmStatus(String novelId);
    
    /**
     * 检查节奏红线
     */
    RhythmViolation checkRhythmViolation(String novelId, StrandType newStrand);
    
    /**
     * 推荐下一个Strand类型
     */
    StrandType recommendNextStrand(String novelId);
    
    /**
     * 更新章节Strand记录
     */
    void updateChapterStrand(String novelId, Integer chapterNum, StrandType strandType);
    
    /**
     * 获取节奏状态字符串（用于Prompt）
     */
    String getRhythmStatusString(String novelId);
    
    /**
     * 节奏状态
     */
    @Data
    class RhythmStatus {
        private StrandType lastStrand;              // 上一章的Strand类型
        private int questConsecutiveCount;          // Quest连续章数
        private int fireGapCount;                   // Fire断档章数
        private int constellationGapCount;          // Constellation断档章数
        private double questRatio;                  // Quest占比
        private double fireRatio;                   // Fire占比
        private double constellationRatio;          // Constellation占比
        private int totalChapters;                  // 总章节数
    }
    
    /**
     * 节奏违规
     */
    @Data
    class RhythmViolation {
        private boolean violated;
        private String violationType;               // quest_overflow/fire_gap/constellation_gap
        private String message;
    }
}
