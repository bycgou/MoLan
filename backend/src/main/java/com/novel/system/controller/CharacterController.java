package com.novel.system.controller;

import com.novel.system.dto.request.CreateCharacterRequest;
import com.novel.system.dto.response.ApiResponse;
import com.novel.system.dto.response.CharacterResponse;
import com.novel.system.entity.mongo.CharacterState;
import com.novel.system.service.character.CharacterStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 人物管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/novel/{novelId}/character")
@RequiredArgsConstructor
public class CharacterController {
    
    private final CharacterStateService characterStateService;
    
    /**
     * 创建人物
     */
    @PostMapping
    public ApiResponse<CharacterResponse> createCharacter(
            @PathVariable String novelId,
            @RequestBody CreateCharacterRequest request) {
        log.info("创建人物，小说ID: {}, 人物名称: {}", novelId, request.getCharacterName());
        
        CharacterState character = new CharacterState();
        character.setCharacterId(generateId());
        character.setNovelId(novelId);
        character.setCharacterName(request.getCharacterName());
        character.setCoreTraits(request.getCoreTraits());
        character.setVoiceStyle(request.getVoiceStyle());
        character.setBackground(request.getBackground());
        character.setForbiddenBehaviors(request.getForbiddenBehaviors());
        
        // 设置初始状态
        CharacterState.CurrentStatus status = new CharacterState.CurrentStatus();
        status.setHealth(100);
        status.setEmotion("平静");
        status.setLocation(request.getCurrentLocation() != null ? request.getCurrentLocation() : "未知");
        character.setCurrentStatus(status);
        
        character = characterStateService.createCharacter(character);
        
        return ApiResponse.success("人物创建成功", convertToCharacterResponse(character));
    }
    
    /**
     * 获取小说所有人物
     */
    @GetMapping("/list")
    public ApiResponse<List<CharacterResponse>> getCharacters(@PathVariable String novelId) {
        List<CharacterState> characters = characterStateService.getCharactersByNovelId(novelId);
        List<CharacterResponse> responses = characters.stream()
            .map(this::convertToCharacterResponse)
            .collect(Collectors.toList());
        return ApiResponse.success(responses);
    }
    
    /**
     * 获取人物详情
     */
    @GetMapping("/{characterId}")
    public ApiResponse<CharacterResponse> getCharacter(
            @PathVariable String novelId,
            @PathVariable String characterId) {
        try {
            CharacterState character = characterStateService.getCharacter(novelId, characterId);
            return ApiResponse.success(convertToCharacterResponse(character));
        } catch (Exception e) {
            return ApiResponse.error(404, "人物不存在");
        }
    }
    
    /**
     * 更新人物状态
     */
    @PutMapping("/{characterId}")
    public ApiResponse<CharacterResponse> updateCharacter(
            @PathVariable String novelId,
            @PathVariable String characterId,
            @RequestBody CharacterState request) {
        try {
            CharacterState character = characterStateService.getCharacter(novelId, characterId);
            
            if (request.getCurrentStatus() != null) {
                character.setCurrentStatus(request.getCurrentStatus());
            }
            if (request.getRelationshipMap() != null) {
                character.setRelationshipMap(request.getRelationshipMap());
            }
            
            character = characterStateService.updateCharacter(character);
            return ApiResponse.success("更新成功", convertToCharacterResponse(character));
        } catch (Exception e) {
            return ApiResponse.error(404, "人物不存在");
        }
    }
    
    /**
     * 更新人物关系
     */
    @PutMapping("/{characterId}/relationship")
    public ApiResponse<Void> updateRelationship(
            @PathVariable String novelId,
            @PathVariable String characterId,
            @RequestParam String targetCharacterId,
            @RequestParam Integer value) {
        characterStateService.updateRelationship(novelId, characterId, targetCharacterId, value);
        return ApiResponse.success("关系更新成功", null);
    }
    
    /**
     * 添加成长节点
     */
    @PostMapping("/{characterId}/growth")
    public ApiResponse<Void> addGrowthNode(
            @PathVariable String novelId,
            @PathVariable String characterId,
            @RequestBody CharacterState.GrowthNode growthNode) {
        characterStateService.addGrowthNode(novelId, characterId, growthNode);
        return ApiResponse.success("成长节点添加成功", null);
    }
    
    private String generateId() {
        return java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 32);
    }
    
    private CharacterResponse convertToCharacterResponse(CharacterState character) {
        CharacterResponse response = new CharacterResponse();
        response.setCharacterId(character.getCharacterId());
        response.setNovelId(character.getNovelId());
        response.setCharacterName(character.getCharacterName());
        response.setCoreTraits(character.getCoreTraits());
        response.setVoiceStyle(character.getVoiceStyle());
        response.setBackground(character.getBackground());
        if (character.getCurrentStatus() != null) {
            response.setCurrentLocation(character.getCurrentStatus().getLocation());
            response.setCurrentStatus(character.getCurrentStatus().getStatus());
        }
        // personality = coreTraits 逗号拼接
        if (character.getCoreTraits() != null && !character.getCoreTraits().isEmpty()) {
            response.setPersonality(String.join("、", character.getCoreTraits()));
        }
        response.setGrowthNodes(character.getGrowthNodes());
        response.setUpdateTime(character.getUpdateTime());
        return response;
    }
}
