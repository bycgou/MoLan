package com.novel.system.ai.memory;

import com.novel.system.entity.mongo.CharacterState;
import com.novel.system.service.character.CharacterStateService;
import com.novel.system.ai.memory.CharacterGraphService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 人物关系图谱服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CharacterGraphServiceImpl implements CharacterGraphService {
    
    private final CharacterStateService characterStateService;
    
    // 内存存储关系（生产环境应使用图数据库）
    private final Map<String, List<CharacterRelationship>> relationshipStore = new HashMap<>();
    
    @Override
    public CharacterGraph getCharacterGraph(String novelId) {
        CharacterGraph graph = new CharacterGraph();
        graph.setNovelId(novelId);
        
        // 获取所有人物
        List<CharacterState> characters = characterStateService.getCharactersByNovelId(novelId);
        
        // 构建节点
        List<CharacterNode> nodes = characters.stream()
            .map(this::convertToNode)
            .collect(Collectors.toList());
        graph.setNodes(nodes);
        
        // 构建边（关系）
        List<CharacterRelationship> edges = new ArrayList<>();
        for (CharacterState character : characters) {
            String key = novelId + ":" + character.getCharacterId();
            List<CharacterRelationship> relationships = relationshipStore.getOrDefault(key, new ArrayList<>());
            edges.addAll(relationships);
        }
        graph.setEdges(edges);
        
        return graph;
    }
    
    @Override
    public void addRelationship(String novelId, String charId1, String charId2, 
                                RelationshipType type, String description) {
        CharacterRelationship relationship = new CharacterRelationship();
        relationship.setCharId1(charId1);
        relationship.setCharId2(charId2);
        relationship.setType(type);
        relationship.setDescription(description);
        relationship.setIntimacyLevel(50);
        relationship.setConflictLevel(type == RelationshipType.ENEMY ? 80 : 20);
        
        String key1 = novelId + ":" + charId1;
        relationshipStore.computeIfAbsent(key1, k -> new ArrayList<>()).add(relationship);
        
        // 双向关系
        CharacterRelationship reverseRelationship = new CharacterRelationship();
        reverseRelationship.setCharId1(charId2);
        reverseRelationship.setCharId2(charId1);
        reverseRelationship.setType(type);
        reverseRelationship.setDescription(description);
        reverseRelationship.setIntimacyLevel(50);
        reverseRelationship.setConflictLevel(type == RelationshipType.ENEMY ? 80 : 20);
        
        String key2 = novelId + ":" + charId2;
        relationshipStore.computeIfAbsent(key2, k -> new ArrayList<>()).add(reverseRelationship);
        
        log.info("添加人物关系: {} - {} ({})", charId1, charId2, type);
    }
    
    @Override
    public List<CharacterRelationship> getCharacterRelationships(String novelId, String charId) {
        String key = novelId + ":" + charId;
        return relationshipStore.getOrDefault(key, new ArrayList<>());
    }
    
    @Override
    public List<CharacterNode> getRelatedCharacters(String novelId, String charId, int depth) {
        Set<String> visited = new HashSet<>();
        List<CharacterNode> result = new ArrayList<>();
        
        Queue<String> queue = new LinkedList<>();
        queue.add(charId);
        visited.add(charId);
        
        int currentDepth = 0;
        while (!queue.isEmpty() && currentDepth < depth) {
            int levelSize = queue.size();
            
            for (int i = 0; i < levelSize; i++) {
                String currentCharId = queue.poll();
                
                // 获取当前人物
                try {
                    CharacterState character = characterStateService.getCharacter(novelId, currentCharId);
                    result.add(convertToNode(character));
                } catch (Exception e) {
                    // 人物不存在，跳过
                }
                
                // 获取关联人物
                List<CharacterRelationship> relationships = getCharacterRelationships(novelId, currentCharId);
                for (CharacterRelationship rel : relationships) {
                    String relatedCharId = rel.getCharId2();
                    if (!visited.contains(relatedCharId)) {
                        visited.add(relatedCharId);
                        queue.add(relatedCharId);
                    }
                }
            }
            
            currentDepth++;
        }
        
        return result;
    }
    
    @Override
    public String formatGraphAsString(String novelId) {
        CharacterGraph graph = getCharacterGraph(novelId);
        
        if (graph.getNodes().isEmpty()) {
            return "暂无人物关系数据";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("【人物关系图谱】\n\n");
        
        // 人物列表
        sb.append("主要人物：\n");
        for (CharacterNode node : graph.getNodes()) {
            sb.append("- ").append(node.getCharName());
            if (node.getRole() != null) {
                sb.append("（").append(node.getRole()).append("）");
            }
            if (node.getTraits() != null && !node.getTraits().isEmpty()) {
                sb.append("：").append(String.join("、", node.getTraits()));
            }
            sb.append("\n");
        }
        
        // 关系列表
        if (!graph.getEdges().isEmpty()) {
            sb.append("\n人物关系：\n");
            Set<String> printedRelations = new HashSet<>();
            
            for (CharacterRelationship edge : graph.getEdges()) {
                String relationKey = edge.getCharId1() + "-" + edge.getCharId2();
                String reverseKey = edge.getCharId2() + "-" + edge.getCharId1();
                
                if (!printedRelations.contains(relationKey) && !printedRelations.contains(reverseKey)) {
                    String charName1 = getCharName(graph, edge.getCharId1());
                    String charName2 = getCharName(graph, edge.getCharId2());
                    
                    sb.append("- ").append(charName1).append(" 与 ").append(charName2);
                    sb.append("：").append(formatRelationType(edge.getType()));
                    if (edge.getDescription() != null && !edge.getDescription().isEmpty()) {
                        sb.append("（").append(edge.getDescription()).append("）");
                    }
                    sb.append("\n");
                    
                    printedRelations.add(relationKey);
                }
            }
        }
        
        return sb.toString();
    }
    
    private CharacterNode convertToNode(CharacterState character) {
        CharacterNode node = new CharacterNode();
        node.setCharId(character.getCharacterId());
        node.setCharName(character.getCharacterName());
        node.setTraits(character.getCoreTraits());
        
        // 根据特征推断角色
        if (character.getCoreTraits() != null) {
            if (character.getCoreTraits().contains("主角")) {
                node.setRole("主角");
            } else if (character.getCoreTraits().contains("反派")) {
                node.setRole("反派");
            }
        }
        
        return node;
    }
    
    private String getCharName(CharacterGraph graph, String charId) {
        return graph.getNodes().stream()
            .filter(n -> n.getCharId().equals(charId))
            .findFirst()
            .map(CharacterNode::getCharName)
            .orElse("未知");
    }
    
    private String formatRelationType(RelationshipType type) {
        return switch (type) {
            case FAMILY -> "家人";
            case FRIEND -> "朋友";
            case LOVER -> "恋人";
            case ENEMY -> "敌人";
            case MASTER -> "师徒";
            case SUBORDINATE -> "上下级";
            case ALLY -> "盟友";
            case RIVAL -> "对手";
            case OTHER -> "其他";
        };
    }
}
