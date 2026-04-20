package com.novel.system.service.character;

import com.novel.system.entity.mongo.CharacterState;
import com.novel.system.repository.mongo.CharacterStateRepository;
import com.novel.system.service.character.CharacterStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 人物状态管理服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CharacterStateServiceImpl implements CharacterStateService {
    
    private final CharacterStateRepository characterStateRepository;
    
    @Override
    public CharacterState createCharacter(CharacterState characterState) {
        characterState.setCreateTime(LocalDateTime.now());
        characterState.setUpdateTime(LocalDateTime.now());
        return characterStateRepository.save(characterState);
    }
    
    @Override
    public List<CharacterState> getCharactersByNovelId(String novelId) {
        return characterStateRepository.findByNovelId(novelId);
    }
    
    @Override
    public CharacterState getCharacter(String novelId, String characterId) {
        return characterStateRepository.findByNovelIdAndCharacterId(novelId, characterId)
            .orElseThrow(() -> new RuntimeException("人物不存在: " + characterId));
    }
    
    @Override
    public CharacterState updateCharacter(CharacterState characterState) {
        characterState.setUpdateTime(LocalDateTime.now());
        return characterStateRepository.save(characterState);
    }
    
    @Override
    public List<String> getCurrentTraits(String novelId, String characterId, int currentChapterNum) {
        CharacterState state = getCharacter(novelId, characterId);
        List<String> currentTraits = new ArrayList<>(state.getCoreTraits());
        
        // 应用成长节点
        if (state.getGrowthNodes() != null) {
            for (CharacterState.GrowthNode node : state.getGrowthNodes()) {
                if (currentChapterNum >= node.getTriggerChapter()) {
                    currentTraits.addAll(node.getNewTraits());
                }
            }
        }
        
        return currentTraits;
    }
    
    @Override
    public boolean isBehaviorAllowed(String novelId, String characterId, String behavior, int currentChapterNum) {
        CharacterState state = getCharacter(novelId, characterId);
        
        // 硬性规则过滤
        if (state.getForbiddenBehaviors() != null) {
            for (String forbidden : state.getForbiddenBehaviors()) {
                if (behavior.contains(forbidden)) {
                    return false;
                }
            }
        }
        
        List<String> currentTraits = getCurrentTraits(novelId, characterId, currentChapterNum);
        
        // 示例规则：胆小角色不能主动挑衅（除非有成长节点）
        if (behavior.contains("主动挑衅") && currentTraits.contains("胆小") 
            && !hasGrowthNode(state, "勇敢", currentChapterNum)) {
            return false;
        }
        
        // 示例规则：沉默寡言角色不会长篇大论
        if (behavior.contains("长篇大论") && currentTraits.contains("沉默寡言")) {
            return false;
        }
        
        return true;
    }
    
    @Override
    public void updateRelationship(String novelId, String characterId, String targetCharacterId, int value) {
        CharacterState state = getCharacter(novelId, characterId);
        if (state.getRelationshipMap() == null) {
            state.setRelationshipMap(new java.util.HashMap<>());
        }
        state.getRelationshipMap().put(targetCharacterId, value);
        state.setUpdateTime(LocalDateTime.now());
        characterStateRepository.save(state);
    }
    
    @Override
    public void addGrowthNode(String novelId, String characterId, CharacterState.GrowthNode growthNode) {
        CharacterState state = getCharacter(novelId, characterId);
        if (state.getGrowthNodes() == null) {
            state.setGrowthNodes(new ArrayList<>());
        }
        state.getGrowthNodes().add(growthNode);
        state.setUpdateTime(LocalDateTime.now());
        characterStateRepository.save(state);
    }
    
    @Override
    public List<CharacterState> getCharactersByLocation(String novelId, String location) {
        return characterStateRepository.findByNovelIdAndCurrentStatusLocation(novelId, location);
    }
    
    private boolean hasGrowthNode(CharacterState state, String trait, int currentChapter) {
        if (state.getGrowthNodes() == null) {
            return false;
        }
        return state.getGrowthNodes().stream()
            .filter(node -> currentChapter >= node.getTriggerChapter())
            .anyMatch(node -> node.getNewTraits() != null && node.getNewTraits().contains(trait));
    }
}
