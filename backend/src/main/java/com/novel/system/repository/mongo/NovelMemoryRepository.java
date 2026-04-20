package com.novel.system.repository.mongo;

import com.novel.system.entity.mongo.NovelMemory;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface NovelMemoryRepository extends MongoRepository<NovelMemory, String> {
    List<NovelMemory> findByNovelIdOrderByEndChapterDesc(String novelId);
}