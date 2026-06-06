package com.novel.system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.system.ai.agent.SummaryAgentResponse;
import com.novel.system.dto.request.ExtractSummaryRequest;
import com.novel.system.dto.request.UpdateChapterRequest;
import com.novel.system.dto.response.ApiResponse;
import com.novel.system.dto.response.ChapterResponse;
import com.novel.system.entity.mysql.NovelChapterVersion;
import com.novel.system.service.chapter.ChapterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * 章节管理控制器（薄包装层，业务逻辑在 ChapterService）
 */
@Slf4j
@RestController
@RequestMapping("/novel/{novelId}/chapter")
@RequiredArgsConstructor
public class ChapterController {

    private final ChapterService chapterService;
    private final ObjectMapper objectMapper;

    @PostMapping("/extract-summary")
    public ApiResponse<SummaryAgentResponse> extractSummary(
            @PathVariable String novelId,
            @RequestBody ExtractSummaryRequest request) {
        return ApiResponse.success(chapterService.extractSummary(novelId, request.getChapterNum(), request.getChapterContent()));
    }

    @PostMapping("/manual")
    public ApiResponse<ChapterResponse> saveManualChapter(
            @PathVariable String novelId,
            @RequestBody Map<String, Object> payload) {
        Integer chapterNum = (Integer) payload.get("chapterNum");
        String content = (String) payload.get("content");
        String chapterTitle = (String) payload.get("chapterTitle");
        SummaryAgentResponse summaryResponse = objectMapper.convertValue(
                payload.get("summaryData"), SummaryAgentResponse.class);

        try {
            ChapterResponse response = chapterService.saveManualChapter(
                    novelId, chapterNum, chapterTitle, content, summaryResponse);
            return ApiResponse.success("保存成功", response);
        } catch (RuntimeException e) {
            return ApiResponse.error(500, e.getMessage());
        }
    }

    @GetMapping("/list")
    public ApiResponse<List<ChapterResponse>> getChapters(
            @PathVariable String novelId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "0") int size) {
        List<ChapterResponse> all = chapterService.getChapters(novelId);
        if (size > 0) {
            int from = Math.min(page * size, all.size());
            int to = Math.min(from + size, all.size());
            return ApiResponse.success(all.subList(from, to));
        }
        return ApiResponse.success(all);
    }

    @GetMapping("/{chapterNum}")
    public ApiResponse<ChapterResponse> getChapter(
            @PathVariable String novelId,
            @PathVariable Integer chapterNum) {
        try {
            return ApiResponse.success(chapterService.getChapter(novelId, chapterNum));
        } catch (NoSuchElementException e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }

    @GetMapping("/recent")
    public ApiResponse<List<ChapterResponse>> getRecentChapters(
            @PathVariable String novelId,
            @RequestParam(defaultValue = "10") Integer count) {
        return ApiResponse.success(chapterService.getRecentChapters(novelId, count));
    }

    @PutMapping("/{chapterNum}")
    public ApiResponse<ChapterResponse> updateChapter(
            @PathVariable String novelId,
            @PathVariable Integer chapterNum,
            @RequestBody UpdateChapterRequest request) {
        try {
            return ApiResponse.success("更新成功", chapterService.updateChapter(novelId, chapterNum, request));
        } catch (NoSuchElementException e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }

    @DeleteMapping("/{chapterNum}")
    public ApiResponse<Void> deleteChapter(
            @PathVariable String novelId,
            @PathVariable Integer chapterNum) {
        try {
            chapterService.deleteChapter(novelId, chapterNum);
            return ApiResponse.success("已移入回收站", null);
        } catch (NoSuchElementException e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }

    @GetMapping("/recycle-bin")
    public ApiResponse<List<ChapterResponse>> getDeletedChapters(@PathVariable String novelId) {
        return ApiResponse.success(chapterService.getDeletedChapters(novelId));
    }

    @PostMapping("/{chapterId}/restore")
    public ApiResponse<Void> restoreChapter(
            @PathVariable String novelId,
            @PathVariable String chapterId) {
        try {
            chapterService.restoreChapter(novelId, chapterId);
            return ApiResponse.success("恢复成功", null);
        } catch (NoSuchElementException e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }

    @DeleteMapping("/{chapterId}/hard")
    public ApiResponse<Void> hardDeleteChapter(
            @PathVariable String novelId,
            @PathVariable String chapterId) {
        try {
            chapterService.hardDeleteChapter(novelId, chapterId);
            return ApiResponse.success("彻底删除成功", null);
        } catch (NoSuchElementException e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }

    @GetMapping("/{chapterNum}/versions")
    public ApiResponse<List<NovelChapterVersion>> getChapterVersions(
            @PathVariable String novelId,
            @PathVariable Integer chapterNum) {
        try {
            return ApiResponse.success(chapterService.getChapterVersions(novelId, chapterNum));
        } catch (NoSuchElementException e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }

    @PostMapping("/{chapterNum}/rollback/{versionId}")
    public ApiResponse<ChapterResponse> rollbackChapter(
            @PathVariable String novelId,
            @PathVariable Integer chapterNum,
            @PathVariable String versionId) {
        try {
            return ApiResponse.success("回滚成功", chapterService.rollbackChapter(novelId, chapterNum, versionId));
        } catch (NoSuchElementException e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }
}
