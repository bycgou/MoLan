package com.novel.system.entity.mongo;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.List;

/**
 * 小说知识图谱文档 (MongoDB) — 五层记忆架构第五层：事件图谱的关联数据
 * 存储实体节点（人物、地点、物品等）和关系边，支持关系推理。
 */
@Data
@Document(collection = "novel_graph")
public class NovelGraph {
    
    /** 小说ID，同时作为MongoDB文档ID */
    @Id
    private String novelId;
    
    /** 实体节点列表（人物、地点、物品等） */
    private List<Node> nodes;
    
    /** 关系边列表（节点间的关系） */
    private List<Edge> edges;
    
    /**
     * 图谱节点
     */
    @Data
    public static class Node {
        /** 节点ID */
        private String id;
        /** 节点名称 */
        private String name;
        /** 节点类型：person-人物，location-地点，item-物品等 */
        private String type;
    }
    
    /**
     * 图谱边（关系）
     */
    @Data
    public static class Edge {
        /** 源节点ID */
        private String source;
        /** 目标节点ID */
        private String target;
        /** 关系描述（如：师徒、敌对、持有） */
        private String relation;
        /** 最后更新该关系的章节号 */
        private Integer lastUpdateChapter;
        /** 关系类型（用于人物关系） */
        private String relationType;
        /** 亲密度 0-100 */
        private Integer intimacyLevel;
        /** 冲突度 0-100 */
        private Integer conflictLevel;
    }
}
