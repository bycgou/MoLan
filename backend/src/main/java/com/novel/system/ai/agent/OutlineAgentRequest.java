package com.novel.system.ai.agent;

import lombok.Data;
import java.util.List;

/**
 * 大纲Agent请求
 */
@Data
public class OutlineAgentRequest {
    
    private String novelId;
    private String novelGenre;
    private Integer chapterNum;
    private String totalOutline;
    private String arcOutline;
    private String arcName; // 卷名
    private Integer arcStartChapter; // 卷起始章节
    private Integer arcEndChapter; // 卷结束章节
    private String worldClock;
    private String season;
    private String weather;
    private String worldSetting; // 世界观设定
    private String characterStates;
    private String lastChapterFingerprint;
    private String userIntent;
    private String rhythmTag;
    private Integer rhythmIntensity;
    private List<String> unrecycledClues;
    private String modelType;
    private String llmConfigId;
}
