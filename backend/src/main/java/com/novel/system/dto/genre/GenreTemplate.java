package com.novel.system.dto.genre;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class GenreTemplate {
    private String code;
    private String name;
    private String category;
    private String description;
    private String writingStyle;
    private List<String> tropes;
    private List<String> pitfalls;
    private Map<String, String> characterArchetypes;
    private GenreRhythmConfig rhythmConfig;
}
