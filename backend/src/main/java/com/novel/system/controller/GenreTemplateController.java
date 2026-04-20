package com.novel.system.controller;

import com.novel.system.dto.response.ApiResponse;
import com.novel.system.service.genre.GenreTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/genre-template")
@RequiredArgsConstructor
public class GenreTemplateController {
    
    private final GenreTemplateService genreTemplateService;
    
    @GetMapping("/list")
    public ApiResponse<List<GenreTemplateService.GenreInfo>> getEnabledTemplates() {
        List<GenreTemplateService.GenreInfo> templates = genreTemplateService.getGenreList();
        return ApiResponse.success(templates);
    }
    
    @GetMapping("/by-genre")
    public ApiResponse<GenreTemplateService.GenreTemplate> getTemplatesByGenre(@RequestParam String genre) {
        GenreTemplateService.GenreTemplate template = genreTemplateService.getGenreTemplate(genre);
        return ApiResponse.success(template);
    }
    
    @GetMapping("/{templateId}")
    public ApiResponse<GenreTemplateService.GenreTemplate> getTemplateById(@PathVariable String templateId) {
        GenreTemplateService.GenreTemplate template = genreTemplateService.getGenreTemplate(templateId);
        if (template == null) {
            return ApiResponse.error(404, "模板不存在");
        }
        return ApiResponse.success(template);
    }
}
