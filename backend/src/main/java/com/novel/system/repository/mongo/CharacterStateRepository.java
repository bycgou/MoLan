package com.novel.system.repository.mongo;

import com.novel.system.entity.mongo.CharacterState;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CharacterStateRepository extends MongoRepository<CharacterState, String> {
    
    List<CharacterState> findByNovelId(String novelId);
    
    Optional<CharacterState> findByNovelIdAndCharacterId(String novelId, String characterId);
    
    List<CharacterState> findByNovelIdAndCurrentStatusLocation(String novelId, String location);
}
