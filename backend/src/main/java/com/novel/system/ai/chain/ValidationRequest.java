package com.novel.system.ai.chain;

/**
 * 校验请求
 */
public class ValidationRequest {
    
    private String novelId;
    private Integer chapterNum;
    private String chapterContent;
    private String chapterOutline;
    private String totalOutline;
    private String arcOutline;
    private String characterStates;
    private String worldClock;
    private String worldSetting;
    private String rhythmTag;
    private Integer rhythmIntensity;
    private String clueBook;
    
    public ValidationRequest() {
    }
    
    public ValidationRequest(String novelId, Integer chapterNum, String chapterContent, String chapterOutline, String totalOutline, String arcOutline, String characterStates, String worldClock, String worldSetting, String rhythmTag, Integer rhythmIntensity) {
        this.novelId = novelId;
        this.chapterNum = chapterNum;
        this.chapterContent = chapterContent;
        this.chapterOutline = chapterOutline;
        this.totalOutline = totalOutline;
        this.arcOutline = arcOutline;
        this.characterStates = characterStates;
        this.worldClock = worldClock;
        this.worldSetting = worldSetting;
        this.rhythmTag = rhythmTag;
        this.rhythmIntensity = rhythmIntensity;
    }
    
    public String getNovelId() {
        return novelId;
    }
    
    public void setNovelId(String novelId) {
        this.novelId = novelId;
    }
    
    public Integer getChapterNum() {
        return chapterNum;
    }
    
    public void setChapterNum(Integer chapterNum) {
        this.chapterNum = chapterNum;
    }
    
    public String getChapterContent() {
        return chapterContent;
    }
    
    public void setChapterContent(String chapterContent) {
        this.chapterContent = chapterContent;
    }
    
    public String getChapterOutline() {
        return chapterOutline;
    }
    
    public void setChapterOutline(String chapterOutline) {
        this.chapterOutline = chapterOutline;
    }
    
    public String getTotalOutline() {
        return totalOutline;
    }
    
    public void setTotalOutline(String totalOutline) {
        this.totalOutline = totalOutline;
    }
    
    public String getArcOutline() {
        return arcOutline;
    }
    
    public void setArcOutline(String arcOutline) {
        this.arcOutline = arcOutline;
    }
    
    public String getCharacterStates() {
        return characterStates;
    }
    
    public void setCharacterStates(String characterStates) {
        this.characterStates = characterStates;
    }
    
    public String getWorldClock() {
        return worldClock;
    }
    
    public void setWorldClock(String worldClock) {
        this.worldClock = worldClock;
    }
    
    public String getWorldSetting() {
        return worldSetting;
    }
    
    public void setWorldSetting(String worldSetting) {
        this.worldSetting = worldSetting;
    }
    
    public String getRhythmTag() {
        return rhythmTag;
    }
    
    public void setRhythmTag(String rhythmTag) {
        this.rhythmTag = rhythmTag;
    }
    
    public Integer getRhythmIntensity() {
        return rhythmIntensity;
    }
    
    public void setRhythmIntensity(Integer rhythmIntensity) {
        this.rhythmIntensity = rhythmIntensity;
    }
    
    public String getClueBook() {
        return clueBook;
    }
    
    public void setClueBook(String clueBook) {
        this.clueBook = clueBook;
    }
}
