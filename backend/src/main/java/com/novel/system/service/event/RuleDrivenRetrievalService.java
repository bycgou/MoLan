package com.novel.system.service.event;

import com.novel.system.entity.mongo.EventNode;
import com.novel.system.entity.mysql.ClueBook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 规则驱动检索服务 — 五层记忆架构第五层：事件图谱 + 规则驱动检索
 * 根据当前章节上下文，自动触发历史事件检索。
 * 不依赖Agent元认知，通过规则判断何时需要回忆早期内容。
 *
 * 规则1：伏笔回收触发 — 伏笔deadline临近时，检索伏笔埋设事件
 * 规则2：角色重逢触发 — 角色长期未出场后重新登场时，检索最后出场事件
 * 规则3：地点关联触发 — 场景切换到某地点时，检索该地点的重要历史事件
 * 规则4：重要事件触发 — 检索近10章的重要事件
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuleDrivenRetrievalService {

    private final EventGraphService eventGraphService;

    /** 伏笔回收触发距离（deadline前N章开始触发） */
    private static final int CLUE_TRIGGER_DISTANCE = 5;

    /** 角色重逢触发阈值（超过N章未出场视为"久别"） */
    private static final int CHARACTER_REUNION_THRESHOLD = 20;

    /** 地点事件最低重要性 */
    private static final int LOCATION_MIN_IMPORTANCE = 7;

    /** 近期重要事件最低重要性 */
    private static final int RECENT_MIN_IMPORTANCE = 8;

    /** 近期重要事件查询范围 */
    private static final int RECENT_CHAPTER_RANGE = 10;

    /** 地点提取模式（从章纲中提取） */
    private static final Pattern LOCATION_IN_OUTLINE = Pattern.compile(
        "(?:在|来到|到达|进入|回到|身处|位于|前往)([\\u4e00-\\u9fa5]{2,8})"
    );

    /** 角色名提取模式（从章纲中提取重逢角色） */
    private static final Pattern CHARACTER_IN_OUTLINE = Pattern.compile(
        "([\\u4e00-\\u9fa5]{2,3}?)(?:再次|重新|归来|回归|出现|登场|现身)"
    );

    /**
     * 自动检索：综合所有规则，返回需要注入的事件
     */
    public List<EventNode> autoRetrieve(String novelId, int chapterNum,
                                         String chapterOutline, List<ClueBook> pendingClues) {
        Set<String> seenIds = new HashSet<>();
        List<EventNode> results = new ArrayList<>();

        // 规则1：伏笔回收触发
        for (EventNode event : retrieveByClueRecycle(novelId, chapterNum, pendingClues)) {
            if (seenIds.add(event.getEventId())) results.add(event);
        }

        // 规则2：角色重逢触发
        for (EventNode event : retrieveByCharacterReunion(novelId, chapterNum, chapterOutline)) {
            if (seenIds.add(event.getEventId())) results.add(event);
        }

        // 规则3：地点关联触发
        for (EventNode event : retrieveByLocation(novelId, chapterNum, chapterOutline)) {
            if (seenIds.add(event.getEventId())) results.add(event);
        }

        // 规则4：近期重要事件
        for (EventNode event : retrieveRecentImportant(novelId, chapterNum)) {
            if (seenIds.add(event.getEventId())) results.add(event);
        }

        log.info("规则驱动检索完成: novelId={}, chapter={}, 共检索{}个事件", novelId, chapterNum, results.size());
        return results;
    }

    /**
     * 规则1：伏笔回收触发
     * 当伏笔deadline临近时，自动检索伏笔埋设时的事件
     */
    public List<EventNode> retrieveByClueRecycle(String novelId, int chapterNum, List<ClueBook> clues) {
        List<EventNode> results = new ArrayList<>();
        if (clues == null) return results;

        for (ClueBook clue : clues) {
            if (clue.getDeadline() == null) continue;
            if (chapterNum < clue.getDeadline() - CLUE_TRIGGER_DISTANCE) continue;

            // 检索伏笔埋设时的事件
            List<EventNode> clueEvents = eventGraphService.getEventsByChapterAndTag(
                novelId, clue.getBuryChapter(), "伏笔埋设");
            results.addAll(clueEvents);

            // 检索伏笔相关角色的事件
            if (clue.getRelatedCharacters() != null && !clue.getRelatedCharacters().isBlank()) {
                for (String character : clue.getRelatedCharacters().split("[,，、]")) {
                    String trimmed = character.trim();
                    if (!trimmed.isEmpty()) {
                        List<EventNode> charEvents = new ArrayList<>(
                            eventGraphService.getEventsByCharacter(novelId, trimmed));
                        // 只保留伏笔埋设前后5章的事件
                        charEvents.removeIf(e ->
                            Math.abs(e.getChapter() - clue.getBuryChapter()) > 5);
                        results.addAll(charEvents);
                    }
                }
            }
        }

        return results;
    }

    /**
     * 规则2：角色重逢触发
     * 当角色很久未出场后重新登场时，自动检索该角色的最后出场事件
     */
    public List<EventNode> retrieveByCharacterReunion(String novelId, int chapterNum, String chapterOutline) {
        List<EventNode> results = new ArrayList<>();
        if (chapterOutline == null || chapterOutline.isBlank()) return results;

        // 从章纲中提取可能重逢的角色
        List<String> reunionCharacters = extractReunionCharacters(chapterOutline);

        for (String character : reunionCharacters) {
            EventNode lastAppearance = eventGraphService.getLastAppearance(novelId, character);
            if (lastAppearance != null && chapterNum - lastAppearance.getChapter() > CHARACTER_REUNION_THRESHOLD) {
                results.add(lastAppearance);
                log.info("角色重逢检索: {} 最后出场于第{}章", character, lastAppearance.getChapter());
            }
        }

        return results;
    }

    /**
     * 规则3：地点关联触发
     * 当场景切换到某个地点时，自动检索该地点的重要历史事件
     */
    public List<EventNode> retrieveByLocation(String novelId, int chapterNum, String chapterOutline) {
        List<EventNode> results = new ArrayList<>();
        if (chapterOutline == null || chapterOutline.isBlank()) return results;

        String location = extractLocationFromOutline(chapterOutline);
        if (location == null) return results;

        List<EventNode> locationEvents = new ArrayList<>(
            eventGraphService.getImportantEventsByLocation(novelId, location, LOCATION_MIN_IMPORTANCE));

        // 排除最近5章的事件（太近，不需要回忆）
        locationEvents.removeIf(e -> e.getChapter() > chapterNum - 5);
        results.addAll(locationEvents);

        return results;
    }

    /**
     * 规则4：近期重要事件
     * 检索近10章的重要事件，确保关键情节不被遗忘
     */
    public List<EventNode> retrieveRecentImportant(String novelId, int chapterNum) {
        int fromChapter = Math.max(1, chapterNum - RECENT_CHAPTER_RANGE);
        return eventGraphService.getImportantEventsInRange(
            novelId, fromChapter, chapterNum - 1, RECENT_MIN_IMPORTANCE);
    }

    /**
     * 从章纲中提取可能重逢的角色名
     */
    private List<String> extractReunionCharacters(String chapterOutline) {
        List<String> characters = new ArrayList<>();
        Matcher matcher = CHARACTER_IN_OUTLINE.matcher(chapterOutline);
        while (matcher.find()) {
            characters.add(matcher.group(1));
        }
        return characters;
    }

    /**
     * 从章纲中提取地点
     */
    private String extractLocationFromOutline(String chapterOutline) {
        Matcher matcher = LOCATION_IN_OUTLINE.matcher(chapterOutline);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
