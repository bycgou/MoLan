package com.novel.system.service.genre;

import com.novel.system.dto.genre.GenreInfo;
import com.novel.system.dto.genre.GenreRhythmConfig;
import com.novel.system.dto.genre.GenreTemplate;

import java.util.List;

public interface GenreService {

    List<GenreInfo> getGenreList();

    GenreTemplate getGenreTemplate(String code);

    GenreRhythmConfig getGenreRhythmConfig(String code);

    String getGenreGuide(String code);

    List<String> getGenreDetailFiles(String code);
}
