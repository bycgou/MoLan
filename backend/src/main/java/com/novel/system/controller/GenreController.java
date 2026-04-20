package com.novel.system.controller;

import com.novel.system.dto.response.ApiResponse;
import com.novel.system.service.genre.GenreTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 题材模板控制器
 */
@Slf4j
@RestController
@RequestMapping("/genre")
@RequiredArgsConstructor
public class GenreController {
    
    private final GenreTemplateService genreTemplateService;
    
    @GetMapping("/list")
    public ApiResponse<?> getGenreList() {
        return ApiResponse.success(genreTemplateService.getGenreList());
    }
    
    @GetMapping("/{genreCode}")
    public ApiResponse<?> getGenreTemplate(@PathVariable String genreCode) {
        return ApiResponse.success(genreTemplateService.getGenreTemplate(genreCode));
    }
    
    @GetMapping("/{genreCode}/rhythm")
    public ApiResponse<?> getGenreRhythm(@PathVariable String genreCode) {
        return ApiResponse.success(genreTemplateService.getGenreRhythmConfig(genreCode));
    }
}
