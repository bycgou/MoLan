package com.novel.system.entity.mongo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "completed_beats")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompletedBeats {

    @Id
    private String id;

    @Indexed(unique = true)
    private String novelId;

    private List<BeatRecord> beats = new ArrayList<>();
    private LocalDateTime updatedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BeatRecord {
        private String beatId;
        private String summary;
        private int chapterNum;
        private List<String> charactersInvolved;
        private String plotThread;
    }
}
