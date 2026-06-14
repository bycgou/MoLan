package com.novel.system.repository.mongo;

import com.novel.system.entity.mongo.CharacterVoiceSample;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CharacterVoiceSampleRepository extends MongoRepository<CharacterVoiceSample, String> {
    List<CharacterVoiceSample> findByNovelIdAndCharacterName(String novelId, String characterName);
    List<CharacterVoiceSample> findByNovelIdAndCharacterNameAndChapterNumLessThan(
            String novelId, String characterName, int chapterNum);
    void deleteByNovelId(String novelId);
}
