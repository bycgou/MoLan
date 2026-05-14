package com.novel.system.repository.mongo;

import com.novel.system.entity.mongo.StoryState;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface StoryStateRepository extends MongoRepository<StoryState, String> {

    /**
     * 根据小说ID查询故事状态
     * 一个小说只有一条 StoryState 记录
     */
    Optional<StoryState> findByNovelId(String novelId);

    /**
     * 删除指定小说的故事状态
     */
    void deleteByNovelId(String novelId);
}
