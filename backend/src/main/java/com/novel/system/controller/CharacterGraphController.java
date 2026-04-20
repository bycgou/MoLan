package com.novel.system.controller;

import com.novel.system.dto.response.ApiResponse;
import com.novel.system.ai.memory.CharacterGraphService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 人物关系图谱控制器
 */
@Slf4j
@RestController
@RequestMapping("/novel/{novelId}/graph")
@RequiredArgsConstructor
public class CharacterGraphController {
    
    private final CharacterGraphService characterGraphService;
    
    @GetMapping
    public ApiResponse<CharacterGraphService.CharacterGraph> getCharacterGraph(@PathVariable String novelId) {
        return ApiResponse.success(characterGraphService.getCharacterGraph(novelId));
    }
    
    @PostMapping("/relationship")
    public ApiResponse<Void> addRelationship(
            @PathVariable String novelId,
            @RequestBody AddRelationshipRequest request) {
        characterGraphService.addRelationship(
            novelId, 
            request.getCharId1(), 
            request.getCharId2(),
            CharacterGraphService.RelationshipType.valueOf(request.getType().toUpperCase()),
            request.getDescription()
        );
        return ApiResponse.success("关系添加成功", null);
    }
    
    @GetMapping("/character/{charId}")
    public ApiResponse<?> getCharacterRelationships(
            @PathVariable String novelId,
            @PathVariable String charId) {
        return ApiResponse.success(characterGraphService.getCharacterRelationships(novelId, charId));
    }
    
    @Data
    static class AddRelationshipRequest {
        private String charId1;
        private String charId2;
        private String type;
        private String description;
    }
}
