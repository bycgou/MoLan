package com.novel.system.entity.mongo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "character_voice_samples")
@CompoundIndex(name = "novel_chapter_idx", def = "{'novelId': 1, 'chapterNum': -1}")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CharacterVoiceSample {

    @Id
    private String id;

    @Indexed
    private String novelId;

    @Indexed
    private String characterName;

    private String dialogue;
    private String context;
    private int chapterNum;
    private String emotion;
    private String situation;
    private LocalDateTime createdAt;
}
