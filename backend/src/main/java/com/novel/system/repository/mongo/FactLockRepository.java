package com.novel.system.repository.mongo;

import com.novel.system.entity.mongo.FactLock;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FactLockRepository extends MongoRepository<FactLock, String> {
    Optional<FactLock> findByNovelId(String novelId);
    void deleteByNovelId(String novelId);
}
