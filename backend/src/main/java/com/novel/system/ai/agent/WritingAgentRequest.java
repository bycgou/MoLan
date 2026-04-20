package com.novel.system.ai.agent;

import lombok.Data;

/**
 * 写作Agent请求
 */
@Data
public class WritingAgentRequest {
    
    private String novelId;
    private String novelGenre;
    private Integer chapterNum;
    private String chapterOutline;
    private String selectedIdea;
    private String context;
    private String characterStates;
    private String userIntent;
    private String rhythmTag;
    private Integer rhythmIntensity;
    private Integer targetWordCount;
    private String modelType;
    private String llmConfigId;
    
    // 新增字段
    private String totalOutline; // 小说总纲
    private String arcOutline; // 卷纲
    private String arcName; // 卷名
    private String worldSetting; // 世界观设定
    private String lastChapterFingerprint; // 上一章剧情
}
