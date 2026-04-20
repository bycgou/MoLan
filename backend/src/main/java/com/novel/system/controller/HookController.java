package com.novel.system.controller;

import com.novel.system.dto.response.ApiResponse;
import com.novel.system.ai.chain.HookDetectionService;
import com.novel.system.entity.mysql.NovelChapter;
import com.novel.system.mapper.NovelChapterMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 钩子检测控制器 - 接入真实检测服务
 */
@Slf4j
@RestController
@RequestMapping("/novel/{novelId}/chapter/{chapterNum}/hook")
@RequiredArgsConstructor
public class HookController {
    
    private final HookDetectionService hookDetectionService;
    private final NovelChapterMapper novelChapterMapper;
    
    @GetMapping
    public ApiResponse<HookDetectionService.HookAnalysis> analyzeHook(
            @PathVariable String novelId,
            @PathVariable Integer chapterNum) {
        
        // 获取真实章节内容
        NovelChapter chapter = novelChapterMapper.selectByNovelIdAndChapterNum(novelId, chapterNum);
        if (chapter == null || chapter.getContent() == null || chapter.getContent().isEmpty()) {
            return ApiResponse.error(404, "章节不存在或内容为空");
        }
        
        // 调用真实检测服务
        String cleanContent = chapter.getContent()
            .replaceAll("<[^>]+>", "")  // 去除HTML标签
            .replaceAll("\\s+", " ");    // 压缩空白
        HookDetectionService.HookAnalysis analysis = hookDetectionService.analyzeHook(cleanContent);
        
        return ApiResponse.success(analysis);
    }
}
