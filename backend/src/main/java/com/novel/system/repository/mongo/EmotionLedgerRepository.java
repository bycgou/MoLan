package com.novel.system.repository.mongo;

import com.novel.system.entity.mongo.EmotionLedger;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmotionLedgerRepository extends MongoRepository<EmotionLedger, String> {
    Optional<EmotionLedger> findByNovelId(String novelId);
    void deleteByNovelId(String novelId);
}
