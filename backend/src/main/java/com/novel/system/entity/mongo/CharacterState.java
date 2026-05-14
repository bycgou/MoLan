package com.novel.system.entity.mongo;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 人物状态文档 (MongoDB) — 五层记忆架构第三层：角色档案
 * 存储角色的动态状态（位置、实力、物品、关系等），生成时按需注入。
 */
@Document(collection = "character_state")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CharacterState {
    
    /** MongoDB文档ID */
    @Id
    private String id;
    
    /** 人物ID（业务主键） */
    @Indexed
    private String characterId;
    
    /** 所属小说ID */
    @Indexed
    private String novelId;
    
    /** 人物名称 */
    private String characterName;
    
    /** 核心特质（不可变，如：坚韧、善良） */
    private List<String> coreTraits;
    
    /** 说话风格（如：沉稳、幽默、毒舌） */
    private String voiceStyle;
    
    /** 人物背景故事 */
    private String background;
    
    /** 人物当前状态（健康、情绪、位置等） */
    private CurrentStatus currentStatus;
    
    /** 关系映射（key=其他人物ID, value=关系值，-100~100） */
    private Map<String, Integer> relationshipMap;
    
    /** 成长节点列表（记录人物成长轨迹） */
    private List<GrowthNode> growthNodes;
    
    /** 知识水平（人物已知的信息/秘密） */
    private List<String> knowledge;
    
    /** 行为模式（如：遇敌先防守、喜欢独处） */
    private List<String> behaviorPatterns;
    
    /** 禁止行为（硬性规则，防止OOC） */
    private List<String> forbiddenBehaviors;
    
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
    
    // Getters and Setters
    public LocalDateTime getCreateTime() {
        return createTime;
    }
    
    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
    
    public LocalDateTime getUpdateTime() {
        return updateTime;
    }
    
    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
    
    public List<String> getCoreTraits() {
        return coreTraits;
    }
    
    public void setCoreTraits(List<String> coreTraits) {
        this.coreTraits = coreTraits;
    }
    
    public List<GrowthNode> getGrowthNodes() {
        return growthNodes;
    }
    
    public void setGrowthNodes(List<GrowthNode> growthNodes) {
        this.growthNodes = growthNodes;
    }
    
    public List<String> getForbiddenBehaviors() {
        return forbiddenBehaviors;
    }
    
    public void setForbiddenBehaviors(List<String> forbiddenBehaviors) {
        this.forbiddenBehaviors = forbiddenBehaviors;
    }
    
    public Map<String, Integer> getRelationshipMap() {
        return relationshipMap;
    }
    
    public void setRelationshipMap(Map<String, Integer> relationshipMap) {
        this.relationshipMap = relationshipMap;
    }



    /**
     * 人物当前状态
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CurrentStatus {
        /** 生命值（0-100） */
        private Integer health;
        /** 当前情绪（如：愤怒、悲伤、平静） */
        private String emotion;
        /** 持有物品列表 */
        private List<String> items;
        /** 当前所在位置 */
        private String location;
        /** 状态描述文本 */
        private String status;
    }
    
    /**
     * 成长节点
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GrowthNode {
        /** 触发章节号 */
        private Integer triggerChapter;
        /** 成长后新增的特质 */
        private List<String> newTraits;
        /** 成长描述 */
        private String description;
        /** 触发事件描述 */
        private String triggerEvent;
        
        public Integer getTriggerChapter() {
            return triggerChapter;
        }
        
        public void setTriggerChapter(Integer triggerChapter) {
            this.triggerChapter = triggerChapter;
        }
        
        public List<String> getNewTraits() {
            return newTraits;
        }
        
        public void setNewTraits(List<String> newTraits) {
            this.newTraits = newTraits;
        }
        
        public String getDescription() {
            return description;
        }
        
        public void setDescription(String description) {
            this.description = description;
        }
        
        public String getTriggerEvent() {
            return triggerEvent;
        }
        
        public void setTriggerEvent(String triggerEvent) {
            this.triggerEvent = triggerEvent;
        }
    }
}
