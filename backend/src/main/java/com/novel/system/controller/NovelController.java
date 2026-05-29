package com.novel.system.controller;

import com.novel.system.dto.request.CreateNovelRequest;
import com.novel.system.dto.request.GenerateChapterRequest;
import com.novel.system.dto.request.UpdateNovelRequest;
import com.novel.system.dto.request.UpdateTimelineRequest;
import com.novel.system.dto.response.ApiResponse;
import com.novel.system.dto.response.GenerationResultResponse;
import com.novel.system.dto.response.NovelResponse;
import com.novel.system.dto.response.OutlineResponse;
import com.novel.system.entity.mongo.NovelGraph;
import com.novel.system.entity.mysql.WorldClock;
import com.novel.system.service.novel.NovelService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * 小说管理控制器（薄包装层，业务逻辑在 NovelService）
 */
@Slf4j
@RestController
@RequestMapping("/novel")
@RequiredArgsConstructor
public class NovelController {

    private final NovelService novelService;

    @PostMapping
    public ApiResponse<NovelResponse> createNovel(@Valid @RequestBody CreateNovelRequest request,
                                                   HttpServletRequest httpRequest) {
        String userId = (String) httpRequest.getAttribute("userId");
        NovelResponse response = novelService.createNovel(request, userId);
        return ApiResponse.success("小说创建成功", response);
    }

    @PostMapping("/auto-generate-outline")
    public ApiResponse<Map<String, String>> autoGenerateOutline(@RequestBody Map<String, String> request) {
        try {
            Map<String, String> result = novelService.autoGenerateOutline(
                    request.get("novelName"),
                    request.get("genre"),
                    request.get("prompt"));
            return ApiResponse.success("生成成功", result);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("自动生成大纲失败", e);
            return ApiResponse.error(500, "生成大纲失败，请稍后重试或尝试手动输入");
        }
    }

    @GetMapping("/list")
    public ApiResponse<List<NovelResponse>> getNovelList(HttpServletRequest httpRequest,
                                                          @RequestParam(required = false) Boolean isDeleted) {
        String userId = (String) httpRequest.getAttribute("userId");
        if (isDeleted == null) {
            isDeleted = false;
        }
        return ApiResponse.success(novelService.getNovelList(userId, isDeleted));
    }

    @GetMapping("/{novelId}")
    public ApiResponse<NovelResponse> getNovel(@PathVariable String novelId) {
        try {
            return ApiResponse.success(novelService.getNovel(novelId));
        } catch (NoSuchElementException e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }

    @PutMapping("/{novelId}")
    public ApiResponse<NovelResponse> updateNovel(@PathVariable String novelId,
                                                   @RequestBody UpdateNovelRequest request) {
        try {
            return ApiResponse.success("更新成功", novelService.updateNovel(novelId, request));
        } catch (NoSuchElementException e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }

    @DeleteMapping("/{novelId}")
    public ApiResponse<Void> deleteNovel(@PathVariable String novelId) {
        try {
            novelService.deleteNovel(novelId);
            return ApiResponse.success("已移入回收站", null);
        } catch (NoSuchElementException e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }

    @PostMapping("/{novelId}/restore")
    public ApiResponse<Void> restoreNovel(@PathVariable String novelId) {
        try {
            novelService.restoreNovel(novelId);
            return ApiResponse.success("恢复成功", null);
        } catch (NoSuchElementException e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }

    @DeleteMapping("/{novelId}/hard")
    public ApiResponse<Void> hardDeleteNovel(@PathVariable String novelId) {
        novelService.hardDeleteNovel(novelId);
        return ApiResponse.success("彻底删除成功", null);
    }

    @GetMapping("/{novelId}/export")
    public ResponseEntity<byte[]> exportNovelTxt(@PathVariable String novelId) {
        return novelService.exportNovelTxt(novelId);
    }

    @GetMapping("/{novelId}/novel-graph")
    public ApiResponse<NovelGraph> getNovelGraph(@PathVariable String novelId) {
        return ApiResponse.success(novelService.getNovelGraph(novelId));
    }

    @GetMapping("/{novelId}/timeline")
    public ApiResponse<WorldClock> getTimeline(@PathVariable String novelId) {
        return ApiResponse.success(novelService.getTimeline(novelId));
    }

    @PutMapping("/{novelId}/timeline")
    public ApiResponse<WorldClock> updateTimeline(@PathVariable String novelId,
                                                   @RequestBody UpdateTimelineRequest request) {
        return ApiResponse.success("更新时间线成功", novelService.updateTimeline(novelId, request));
    }

    @PostMapping("/{novelId}/outline")
    public ApiResponse<OutlineResponse> generateOutline(
            @PathVariable String novelId,
            @RequestParam(required = false) Integer chapterNum,
            @RequestParam(required = false) String userIntent,
            @RequestParam(required = false) String llmConfigId,
            @RequestParam(required = false, defaultValue = "qwen") String modelType) {
        try {
            return ApiResponse.success(novelService.generateOutline(novelId, chapterNum, userIntent, llmConfigId, modelType));
        } catch (RuntimeException e) {
            return ApiResponse.error(500, e.getMessage());
        }
    }

    @PostMapping("/{novelId}/chapter")
    public ApiResponse<GenerationResultResponse> generateChapter(
            @PathVariable String novelId,
            @RequestBody GenerateChapterRequest request) {
        try {
            return ApiResponse.success("章节生成成功", novelService.generateChapter(novelId, request));
        } catch (RuntimeException e) {
            return ApiResponse.error(500, e.getMessage());
        }
    }

    @PostMapping("/{novelId}/chapter/async")
    public ApiResponse<String> generateChapterAsync(
            @PathVariable String novelId,
            @RequestBody GenerateChapterRequest request) {
        String taskId = novelService.generateChapterAsync(novelId, request);
        return ApiResponse.success("章节生成任务已提交", taskId);
    }

    @GetMapping("/{novelId}/chapter/status/{taskId}")
    public ApiResponse<GenerationResultResponse> getGenerationStatus(
            @PathVariable String novelId,
            @PathVariable String taskId) {
        return ApiResponse.success("获取状态成功", novelService.getGenerationStatus(novelId, taskId));
    }
}
