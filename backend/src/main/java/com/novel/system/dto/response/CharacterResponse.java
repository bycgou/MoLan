package com.novel.system.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.novel.system.entity.mongo.CharacterState;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 人物信息响应DTO
 */
public class CharacterResponse {
    
    /** 人物ID */
    private String characterId;
    /** 所属小说ID */
    private String novelId;
    /** 人物名称 */
    @JsonProperty("name")
    private String characterName;
    /** 核心特质列表 */
    private List<String> coreTraits;
    /** 说话风格 */
    private String voiceStyle;
    /** 人物背景故事 */
    private String background;
    /** 当前所在位置 */
    private String currentLocation;
    /** 当前状态描述 */
    private String currentStatus;
    /** 性格特征（逗号分隔，前端兼容字段） */
    private String personality;
    /** 成长节点列表 */
    private List<CharacterState.GrowthNode> growthNodes;
    /** 更新时间 */
    private LocalDateTime updateTime;
    
    public CharacterResponse() {
    }
    
    public CharacterResponse(String characterId, String novelId, String characterName, List<String> coreTraits, String voiceStyle, String background, String currentLocation, String currentStatus, LocalDateTime updateTime) {
        this.characterId = characterId;
        this.novelId = novelId;
        this.characterName = characterName;
        this.coreTraits = coreTraits;
        this.voiceStyle = voiceStyle;
        this.background = background;
        this.currentLocation = currentLocation;
        this.currentStatus = currentStatus;
        this.updateTime = updateTime;
    }
    
    public String getCharacterId() {
        return characterId;
    }
    
    public void setCharacterId(String characterId) {
        this.characterId = characterId;
    }
    
    public String getNovelId() {
        return novelId;
    }
    
    public void setNovelId(String novelId) {
        this.novelId = novelId;
    }
    
    public String getCharacterName() {
        return characterName;
    }
    
    public void setCharacterName(String characterName) {
        this.characterName = characterName;
    }
    
    public List<String> getCoreTraits() {
        return coreTraits;
    }
    
    public void setCoreTraits(List<String> coreTraits) {
        this.coreTraits = coreTraits;
    }

    /** 角色标签（前端兼容字段，取核心特质第一项） */
    @JsonProperty("role")
    public String getRole() {
        if (coreTraits != null && !coreTraits.isEmpty()) {
            return String.join("/", coreTraits);
        }
        return voiceStyle != null ? voiceStyle : "未设置";
    }

    public String getVoiceStyle() {
        return voiceStyle;
    }
    
    public void setVoiceStyle(String voiceStyle) {
        this.voiceStyle = voiceStyle;
    }
    
    public String getBackground() {
        return background;
    }
    
    public void setBackground(String background) {
        this.background = background;
    }
    
    public String getCurrentLocation() {
        return currentLocation;
    }
    
    public void setCurrentLocation(String currentLocation) {
        this.currentLocation = currentLocation;
    }
    
    public String getCurrentStatus() {
        return currentStatus;
    }
    
    public void setCurrentStatus(String currentStatus) {
        this.currentStatus = currentStatus;
    }

    public String getPersonality() {
        return personality;
    }

    public void setPersonality(String personality) {
        this.personality = personality;
    }

    public List<CharacterState.GrowthNode> getGrowthNodes() {
        return growthNodes;
    }

    public void setGrowthNodes(List<CharacterState.GrowthNode> growthNodes) {
        this.growthNodes = growthNodes;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }
    
    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
