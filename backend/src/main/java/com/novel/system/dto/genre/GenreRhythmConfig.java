package com.novel.system.dto.genre;

import lombok.Data;

@Data
public class GenreRhythmConfig {
    private double questRatio;
    private double fireRatio;
    private double constellationRatio;
    private int questMaxConsecutive;
    private int fireMaxGap;
    private int constellationMaxGap;
}
