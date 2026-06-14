package com.novel.system.entity.mongo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.*;

@Document(collection = "fact_lock")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FactLock {

    @Id
    private String id;

    @Indexed(unique = true)
    private String novelId;

    private Set<String> knownCharacters = new LinkedHashSet<>();
    private List<DeathRecord> deathRecords = new ArrayList<>();
    private Map<String, RelationshipLock> relationshipLocks = new LinkedHashMap<>();
    private List<TimelineAnchor> timelineAnchors = new ArrayList<>();
    private List<String> worldRuleLocks = new ArrayList<>();
    private LocalDateTime updatedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeathRecord {
        private String characterName;
        private String causeOfDeath;
        private int chapterNum;
        private String description;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RelationshipLock {
        private String fromCharacter;
        private String toCharacter;
        private String relationType;
        private int establishedChapter;
        private boolean canChange;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimelineAnchor {
        private int chapterNum;
        private String event;
        private String worldDate;
    }
}
