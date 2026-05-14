package com.novel.system.service.event;

import com.novel.system.entity.mongo.EventNode;
import com.novel.system.repository.mongo.EventNodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 事件图谱服务
 * 提供事件的CRUD操作和多维度查询能力。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventGraphService {

    private final EventNodeRepository eventNodeRepository;

    /**
     * 批量保存事件节点
     */
    public List<EventNode> saveAll(List<EventNode> events) {
        if (events == null || events.isEmpty()) return events;
        return eventNodeRepository.saveAll(events);
    }

    /**
     * 按小说+章节查询事件
     */
    public List<EventNode> getEventsByChapter(String novelId, int chapterNum) {
        return eventNodeRepository.findByNovelIdAndChapter(novelId, chapterNum);
    }

    /**
     * 按角色查询事件
     */
    public List<EventNode> getEventsByCharacter(String novelId, String characterName) {
        return eventNodeRepository.findByNovelIdAndCharacter(novelId, characterName);
    }

    /**
     * 按地点查询重要事件
     */
    public List<EventNode> getImportantEventsByLocation(String novelId, String location, int minImportance) {
        return eventNodeRepository.findByNovelIdAndLocationAndImportanceGreaterThanEqual(
            novelId, location, minImportance);
    }

    /**
     * 按章节范围+重要性查询
     */
    public List<EventNode> getImportantEventsInRange(String novelId, int fromChapter, int toChapter, int minImportance) {
        return eventNodeRepository.findByNovelIdAndChapterBetweenAndImportanceGreaterThanEqual(
            novelId, fromChapter, toChapter, minImportance);
    }

    /**
     * 查询角色最后出场事件
     */
    public EventNode getLastAppearance(String novelId, String characterName) {
        return eventNodeRepository.findLastAppearanceByCharacter(novelId, characterName);
    }

    /**
     * 按标签查询事件
     */
    public List<EventNode> getEventsByTag(String novelId, String tag) {
        return eventNodeRepository.findByNovelIdAndTag(novelId, tag);
    }

    /**
     * 按伏笔埋设章节查询事件
     */
    public List<EventNode> getEventsByChapterAndTag(String novelId, int chapter, String tag) {
        return eventNodeRepository.findByNovelIdAndChapterAndTag(novelId, chapter, tag);
    }

    /**
     * 删除小说的所有事件
     */
    public void deleteAllByNovelId(String novelId) {
        eventNodeRepository.deleteByNovelId(novelId);
    }

    /**
     * 将事件列表格式化为Prompt注入文本
     */
    public String formatEventsForPrompt(List<EventNode> events, String sectionTitle) {
        if (events == null || events.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("【").append(sectionTitle).append("】\n");
        for (EventNode event : events) {
            sb.append(String.format("第%d章[%s]", event.getChapter(), event.getType()));
            if (event.getLocation() != null && !event.getLocation().isBlank()) {
                sb.append("(").append(event.getLocation()).append(")");
            }
            sb.append("：").append(event.getContent());
            if (event.getCharacters() != null && !event.getCharacters().isEmpty()) {
                sb.append(" [角色:").append(String.join(",", event.getCharacters())).append("]");
            }
            sb.append("\n");
        }
        sb.append("\n");
        return sb.toString();
    }
}
