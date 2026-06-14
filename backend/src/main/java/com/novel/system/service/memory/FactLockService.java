package com.novel.system.service.memory;

import com.novel.system.entity.mongo.FactLock;

public interface FactLockService {

    FactLock getOrCreate(String novelId);

    String buildFactLockSection(String novelId);

    void updateAfterChapter(String novelId, int chapterNum, String chapterContent);

    FactLockViolationResult detectViolations(String novelId, int chapterNum, String chapterContent);

    record FactLockViolationResult(
        boolean hasViolation,
        String severity,  // HIGH / MEDIUM / LOW
        String detail
    ) {}
}
