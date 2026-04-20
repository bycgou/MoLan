package com.novel.system.repository.mongo;

import com.novel.system.entity.mongo.ChapterSummary;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChapterSummaryRepository extends MongoRepository<ChapterSummary, String> {
    
    List<ChapterSummary> findByNovelIdOrderByChapterNumDesc(String novelId);
    
    Optional<ChapterSummary> findByNovelIdAndChapterNum(String novelId, Integer chapterNum);
    
    @Query("{ 'novelId': ?0, 'chapterNum': { $gte: ?1, $lt: ?2 } }")
    List<ChapterSummary> findRecentSummaries(String novelId, Integer startChapter, Integer endChapter);
    
    List<ChapterSummary> findByNovelIdAndKeywordsIn(String novelId, List<String> keywords);
}
