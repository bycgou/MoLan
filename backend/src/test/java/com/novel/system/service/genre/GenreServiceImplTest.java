package com.novel.system.service.genre;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GenreServiceImplTest {

    private final GenreServiceImpl service = new GenreServiceImpl();

    @Test
    void getGenreTemplate_directCode_returnsTemplate() {
        assertNotNull(service.getGenreTemplate("xuanhuan"));
        assertEquals("玄幻", service.getGenreTemplate("xuanhuan").getName());
    }

    @Test
    void getGenreTemplate_legacyCode_mapsCorrectly() {
        assertNotNull(service.getGenreTemplate("yanqing"));
        assertEquals("dog-blood-romance", service.getGenreTemplate("yanqing").getCode());
    }

    @Test
    void getGenreTemplate_unknownCode_returnsNull() {
        assertNull(service.getGenreTemplate("nonexistent"));
    }

    @Test
    void getGenreList_returnsAllGenres() {
        assertTrue(service.getGenreList().size() >= 10);
    }
}
