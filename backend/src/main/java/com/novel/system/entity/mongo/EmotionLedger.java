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

@Document(collection = "emotion_ledger")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmotionLedger {

    @Id
    private String id;

    @Indexed(unique = true)
    private String novelId;

    private List<EmotionEntry> entries = new ArrayList<>();
    private int version = 0;
    private LocalDateTime updatedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmotionEntry {
        private int chapterNum;
        private EmotionalWound wound;
        private EmotionalBoon boon;
        private PowerShift powerShift;
        private List<OpenLoop> openLoops;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmotionalWound {
        private String loss;
        private String psychImpact;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmotionalBoon {
        private String gain;
        private String valueShift;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PowerShift {
        private String from;
        private String to;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OpenLoop {
        private String hook;
        private int urgency;
    }
}
