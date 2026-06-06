package com.novel.system.controller;

import com.novel.system.dto.response.ApiResponse;
import com.novel.system.service.genre.GenreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/genre")
@RequiredArgsConstructor
public class GenreController {

    private final GenreService genreService;

    @GetMapping("/list")
    public ApiResponse<?> getGenreList() {
        return ApiResponse.success(genreService.getGenreList());
    }

    @GetMapping("/{genreCode}")
    public ApiResponse<?> getGenreTemplate(@PathVariable String genreCode) {
        return ApiResponse.success(genreService.getGenreTemplate(genreCode));
    }

    @GetMapping("/{genreCode}/rhythm")
    public ApiResponse<?> getGenreRhythm(@PathVariable String genreCode) {
        return ApiResponse.success(genreService.getGenreRhythmConfig(genreCode));
    }

    @GetMapping("/{genreCode}/guides")
    public ApiResponse<?> getGenreGuides(@PathVariable String genreCode) {
        return ApiResponse.success(genreService.getGenreDetailFiles(genreCode));
    }

    @GetMapping("/{genreCode}/guides/{filename}")
    public ApiResponse<?> getGenreGuideFile(@PathVariable String genreCode, @PathVariable String filename) {
        String content = genreService.getGenreGuideFile(genreCode, filename);
        if (content == null) return ApiResponse.error("文件不存在");
        return ApiResponse.success(content);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/reload")
    public ApiResponse<?> reloadGenres() {
        genreService.reload();
        return ApiResponse.success("题材数据已刷新");
    }
}
