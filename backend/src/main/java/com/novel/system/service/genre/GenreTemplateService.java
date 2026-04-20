package com.novel.system.service.genre;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 题材模板服务接口
 */
public interface GenreTemplateService {
    
    /**
     * 获取题材列表
     */
    List<GenreInfo> getGenreList();
    
    /**
     * 获取题材详情
     */
    GenreTemplate getGenreTemplate(String genreCode);
    
    /**
     * 获取题材的节奏配置
     */
    GenreRhythmConfig getGenreRhythmConfig(String genreCode);
    
    /**
     * 应用题材模板到Prompt
     */
    String applyGenreTemplate(String basePrompt, String genreCode);
    
    /**
     * 题材信息
     */
    @Data
    class GenreInfo {
        private String code;
        private String name;
        private String category;        // 男频/女频/通用
        private String description;
    }
    
    /**
     * 题材模板
     */
    @Data
    class GenreTemplate {
        private String code;
        private String name;
        private String description;
        private String writingStyle;    // 写作风格指导
        private List<String> tropes;    // 常见套路
        private List<String> pitfalls;  // 避坑指南
        private Map<String, String> characterArchetypes;  // 人物原型
        private GenreRhythmConfig rhythmConfig;
    }
    
    /**
     * 题材节奏配置
     */
    @Data
    class GenreRhythmConfig {
        private double questRatio;      // 主线占比
        private double fireRatio;       // 感情线占比
        private double constellationRatio;  // 世界观占比
        private int questMaxConsecutive;    // 主线最大连续
        private int fireMaxGap;             // 感情线最大断档
        private int constellationMaxGap;    // 世界观最大断档
    }
}
