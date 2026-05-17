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
    /** 角色分类：主角/配角/反派/龙套 */
    private String role;
    /** 人物描述 */
    private String description;
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
    private List<GrowthNodeResponse> growthNodes;
    /** 更新时间 */
    private LocalDateTime updateTime;

    public CharacterResponse() {}

    // Getters and Setters
    public String getCharacterId() { return characterId; }
    public void setCharacterId(String characterId) { this.characterId = characterId; }

    public String getNovelId() { return novelId; }
    public void setNovelId(String novelId) { this.novelId = novelId; }

    public String getCharacterName() { return characterName; }
    public void setCharacterName(String characterName) { this.characterName = characterName; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<String> getCoreTraits() { return coreTraits; }
    public void setCoreTraits(List<String> coreTraits) { this.coreTraits = coreTraits; }

    public String getVoiceStyle() { return voiceStyle; }
    public void setVoiceStyle(String voiceStyle) { this.voiceStyle = voiceStyle; }

    public String getBackground() { return background; }
    public void setBackground(String background) { this.background = background; }

    public String getCurrentLocation() { return currentLocation; }
    public void setCurrentLocation(String currentLocation) { this.currentLocation = currentLocation; }

    public String getCurrentStatus() { return currentStatus; }
    public void setCurrentStatus(String currentStatus) { this.currentStatus = currentStatus; }

    public String getPersonality() { return personality; }
    public void setPersonality(String personality) { this.personality = personality; }

    public List<GrowthNodeResponse> getGrowthNodes() { return growthNodes; }
    public void setGrowthNodes(List<GrowthNodeResponse> growthNodes) { this.growthNodes = growthNodes; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    /**
     * 成长节点响应DTO — 兼容前端 GrowthNode 类型
     */
    public static class GrowthNodeResponse {
        @JsonProperty("nodeId")
        private String nodeId;
        @JsonProperty("chapterNum")
        private Integer chapterNum;
        @JsonProperty("title")
        private String title;
        @JsonProperty("description")
        private String description;
        @JsonProperty("growthType")
        private String growthType;

        public GrowthNodeResponse() {}

        public String getNodeId() { return nodeId; }
        public void setNodeId(String nodeId) { this.nodeId = nodeId; }
        public Integer getChapterNum() { return chapterNum; }
        public void setChapterNum(Integer chapterNum) { this.chapterNum = chapterNum; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getGrowthType() { return growthType; }
        public void setGrowthType(String growthType) { this.growthType = growthType; }
    }
}
