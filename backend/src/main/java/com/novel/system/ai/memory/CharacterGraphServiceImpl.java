package com.novel.system.ai.memory;

import com.novel.system.entity.mongo.CharacterState;
import com.novel.system.entity.mongo.NovelGraph;
import com.novel.system.repository.mongo.NovelGraphRepository;
import com.novel.system.service.character.CharacterStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 人物关系图谱服务实现 - 基于MongoDB
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CharacterGraphServiceImpl implements CharacterGraphService {

    private final CharacterStateService characterStateService;
    private final NovelGraphRepository novelGraphRepository;

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

        // 从MongoDB获取关系
        NovelGraph novelGraph = novelGraphRepository.findById(novelId).orElse(null);
        List<CharacterRelationship> edges = new ArrayList<>();
        if (novelGraph != null && novelGraph.getEdges() != null) {
            for (NovelGraph.Edge edge : novelGraph.getEdges()) {
                CharacterRelationship rel = convertEdgeToRelationship(edge);
                if (rel != null) {
                    edges.add(rel);
                }
            }
        }
        graph.setEdges(edges);

        return graph;
    }

    @Override
    public void addRelationship(String novelId, String charId1, String charId2,
                                RelationshipType type, String description) {
        NovelGraph novelGraph = novelGraphRepository.findById(novelId).orElseGet(() -> {
            NovelGraph g = new NovelGraph();
            g.setNovelId(novelId);
            g.setNodes(new ArrayList<>());
            g.setEdges(new ArrayList<>());
            return g;
        });

        // 确保两个节点都存在
        ensureNodeExists(novelGraph, charId1);
        ensureNodeExists(novelGraph, charId2);

        // 检查是否已存在该关系
        boolean edgeExists = false;
        for (NovelGraph.Edge edge : novelGraph.getEdges()) {
            if (edge.getSource().equals(charId1) && edge.getTarget().equals(charId2)) {
                edge.setRelation(description);
                edge.setRelationType(type.name());
                edge.setIntimacyLevel(50);
                edge.setConflictLevel(type == RelationshipType.ENEMY ? 80 : 20);
                edgeExists = true;
                break;
            }
        }

        if (!edgeExists) {
            NovelGraph.Edge edge = new NovelGraph.Edge();
            edge.setSource(charId1);
            edge.setTarget(charId2);
            edge.setRelation(description);
            edge.setRelationType(type.name());
            edge.setIntimacyLevel(50);
            edge.setConflictLevel(type == RelationshipType.ENEMY ? 80 : 20);
            novelGraph.getEdges().add(edge);
        }

        novelGraphRepository.save(novelGraph);
        log.info("添加人物关系: {} - {} ({})", charId1, charId2, type);
    }

    @Override
    public List<CharacterRelationship> getCharacterRelationships(String novelId, String charId) {
        NovelGraph novelGraph = novelGraphRepository.findById(novelId).orElse(null);
        if (novelGraph == null || novelGraph.getEdges() == null) {
            return new ArrayList<>();
        }

        return novelGraph.getEdges().stream()
            .filter(e -> e.getSource().equals(charId) || e.getTarget().equals(charId))
            .map(this::convertEdgeToRelationship)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
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
                    String relatedCharId = rel.getCharId1().equals(currentCharId) ? rel.getCharId2() : rel.getCharId1();
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

    private void ensureNodeExists(NovelGraph novelGraph, String charId) {
        boolean nodeExists = novelGraph.getNodes().stream()
            .anyMatch(n -> n.getId().equals(charId));
        if (!nodeExists) {
            NovelGraph.Node node = new NovelGraph.Node();
            node.setId(charId);
            node.setName(charId);
            node.setType("person");
            novelGraph.getNodes().add(node);
        }
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

    private CharacterRelationship convertEdgeToRelationship(NovelGraph.Edge edge) {
        if (edge.getRelationType() == null) {
            return null;
        }
        CharacterRelationship rel = new CharacterRelationship();
        rel.setCharId1(edge.getSource());
        rel.setCharId2(edge.getTarget());
        rel.setDescription(edge.getRelation());
        rel.setType(RelationshipType.valueOf(edge.getRelationType()));
        rel.setIntimacyLevel(edge.getIntimacyLevel() != null ? edge.getIntimacyLevel() : 50);
        rel.setConflictLevel(edge.getConflictLevel() != null ? edge.getConflictLevel() : 20);
        return rel;
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
