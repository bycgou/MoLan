package com.novel.system.entity.mongo;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 事件图谱节点 (MongoDB) — 五层记忆架构第五层：事件图谱
 * 存储章节中的关键事件，永不压缩，支持跨章节精确查询。
 * 每个事件限制200字以内，确保存储效率。
 * 通过 RuleDrivenRetrievalService 规则驱动自动检索。
 */
@Document(collection = "event_graph")
@CompoundIndexes({
    @CompoundIndex(name = "novel_chapter_idx", def = "{'novelId': 1, 'chapter': 1}"),
    @CompoundIndex(name = "novel_type_idx", def = "{'novelId': 1, 'type': 1}"),
    @CompoundIndex(name = "novel_importance_idx", def = "{'novelId': 1, 'importance': -1}")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventNode {

    @Id
    private String eventId;

    /** 所属小说ID */
    private String novelId;

    /** 事件发生的章节号 */
    private Integer chapter;

    /** 事件类型: dialogue, action, discovery, foreshadow, conflict, transition */
    private String type;

    /** 参与角色名称列表 */
    private List<String> characters;

    /** 事件发生地点 */
    @Indexed
    private String location;

    /** 事件描述（200字以内） */
    private String content;

    /** 关联的后续事件ID列表 */
    private List<String> linkedEvents;

    /** 重要性评分 1-10 */
    private Integer importance;

    /** 标签列表（用于分类检索） */
    private List<String> tags;

    /** 创建时间 */
    private LocalDateTime createTime;
}
