package com.novel.system.repository.mongo;

import com.novel.system.entity.mongo.EventNode;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * 事件图谱Repository
 * 支持按章节、角色、地点、重要性等多维度查询
 */
@Repository
public interface EventNodeRepository extends MongoRepository<EventNode, String> {

    /** 按小说ID查询所有事件 */
    List<EventNode> findByNovelId(String novelId);

    /** 按小说+章节查询 */
    List<EventNode> findByNovelIdAndChapter(String novelId, Integer chapter);

    /** 按小说+章节范围查询 */
    List<EventNode> findByNovelIdAndChapterBetween(String novelId, Integer from, Integer to);

    /** 按角色查询（角色名在characters列表中） */
    @Query("{'novelId': ?0, 'characters': ?1}")
    List<EventNode> findByNovelIdAndCharacter(String novelId, String characterName);

    /** 按地点查询 */
    List<EventNode> findByNovelIdAndLocation(String novelId, String location);

    /** 按地点+重要性查询 */
    List<EventNode> findByNovelIdAndLocationAndImportanceGreaterThanEqual(
            String novelId, String location, Integer importance);

    /** 按重要性阈值查询 */
    List<EventNode> findByNovelIdAndImportanceGreaterThanEqual(String novelId, Integer importance);

    /** 按标签查询 */
    @Query("{'novelId': ?0, 'tags': ?1}")
    List<EventNode> findByNovelIdAndTag(String novelId, String tag);

    /** 按章节范围+重要性查询 */
    List<EventNode> findByNovelIdAndChapterBetweenAndImportanceGreaterThanEqual(
            String novelId, Integer from, Integer to, Integer importance);

    /** 查询角色最后出场的事件 */
    @Query(value = "{'novelId': ?0, 'characters': ?1}", sort = "{'chapter': -1}")
    EventNode findLastAppearanceByCharacter(String novelId, String characterName);

    /** 按小说+章节+标签查询 */
    @Query("{'novelId': ?0, 'chapter': ?1, 'tags': ?2}")
    List<EventNode> findByNovelIdAndChapterAndTag(String novelId, Integer chapter, String tag);

    /** 删除小说的所有事件 */
    void deleteByNovelId(String novelId);
}
