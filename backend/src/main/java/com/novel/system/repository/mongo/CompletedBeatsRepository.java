package com.novel.system.repository.mongo;

import com.novel.system.entity.mongo.CompletedBeats;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CompletedBeatsRepository extends MongoRepository<CompletedBeats, String> {
    Optional<CompletedBeats> findByNovelId(String novelId);
    void deleteByNovelId(String novelId);
}
