package com.novel.system.repository.mongo;

import com.novel.system.entity.mongo.AuditLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AuditLogRepository extends MongoRepository<AuditLog, String> {
    
    List<AuditLog> findByNovelIdOrderByCreateTimeDesc(String novelId);
    
    List<AuditLog> findByNovelIdAndChapterNumOrderByCreateTimeDesc(String novelId, Integer chapterNum);
    
    List<AuditLog> findByNovelIdAndResult(String novelId, String result);
}
