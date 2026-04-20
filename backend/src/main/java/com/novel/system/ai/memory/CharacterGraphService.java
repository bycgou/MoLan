package com.novel.system.ai.memory;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 人物关系图谱服务接口
 */
public interface CharacterGraphService {
    
    /**
     * 获取人物关系图谱
     */
    CharacterGraph getCharacterGraph(String novelId);
    
    /**
     * 添加人物关系
     */
    void addRelationship(String novelId, String charId1, String charId2, RelationshipType type, String description);
    
    /**
     * 获取人物的关系列表
     */
    List<CharacterRelationship> getCharacterRelationships(String novelId, String charId);
    
    /**
     * 获取与某人物相关的人物列表
     */
    List<CharacterNode> getRelatedCharacters(String novelId, String charId, int depth);
    
    /**
     * 格式化关系图谱为文本（用于Prompt）
     */
    String formatGraphAsString(String novelId);
    
    /**
     * 关系类型
     */
    enum RelationshipType {
        FAMILY,         // 家人
        FRIEND,         // 朋友
        LOVER,          // 恋人
        ENEMY,          // 敌人
        MASTER,         // 师徒
        SUBORDINATE,    // 上下级
        ALLY,           // 盟友
        RIVAL,          // 对手
        OTHER           // 其他
    }
    
    /**
     * 人物节点
     */
    @Data
    class CharacterNode {
        private String charId;
        private String charName;
        private String role;            // 主角/女主/反派等
        private List<String> traits;    // 特质
        private Map<String, String> attributes;  // 其他属性
    }
    
    /**
     * 人物关系
     */
    @Data
    class CharacterRelationship {
        private String charId1;
        private String charId2;
        private RelationshipType type;
        private String description;
        private int intimacyLevel;      // 亲密度 0-100
        private int conflictLevel;      // 冲突度 0-100
    }
    
    /**
     * 人物关系图谱
     */
    @Data
    class CharacterGraph {
        private String novelId;
        private List<CharacterNode> nodes;
        private List<CharacterRelationship> edges;
    }
}
