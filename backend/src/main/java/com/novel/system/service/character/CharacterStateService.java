package com.novel.system.service.character;

import com.novel.system.entity.mongo.CharacterState;
import java.util.List;

/**
 * 人物状态管理服务接口
 */
public interface CharacterStateService {
    
    /**
     * 创建人物状态
     */
    CharacterState createCharacter(CharacterState characterState);
    
    /**
     * 获取小说所有人物
     */
    List<CharacterState> getCharactersByNovelId(String novelId);
    
    /**
     * 获取单个人物状态
     */
    CharacterState getCharacter(String novelId, String characterId);
    
    /**
     * 更新人物状态
     */
    CharacterState updateCharacter(CharacterState characterState);
    
    /**
     * 获取人物当前特质（考虑成长节点）
     */
    List<String> getCurrentTraits(String novelId, String characterId, int currentChapterNum);
    
    /**
     * 检查行为是否允许（OOC预判断）
     */
    boolean isBehaviorAllowed(String novelId, String characterId, String behavior, int currentChapterNum);
    
    /**
     * 更新人物关系
     */
    void updateRelationship(String novelId, String characterId, String targetCharacterId, int value);
    
    /**
     * 添加成长节点
     */
    void addGrowthNode(String novelId, String characterId, CharacterState.GrowthNode growthNode);
    
    /**
     * 获取出场人物（根据地点）
     */
    List<CharacterState> getCharactersByLocation(String novelId, String location);
}
