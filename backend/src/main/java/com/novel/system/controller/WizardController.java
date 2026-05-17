package com.novel.system.controller;

import com.novel.system.dto.request.WizardSaveRequest;
import com.novel.system.dto.request.WizardSettingsRequest;
import com.novel.system.dto.response.*;
import com.novel.system.service.wizard.WizardService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 小说创建向导控制器
 */
@Slf4j
@RestController
@RequestMapping("/wizard")
@RequiredArgsConstructor
public class WizardController {

    private final WizardService wizardService;

    @Qualifier("ioTaskExecutor")
    private final Executor ioTaskExecutor;

    /**
     * Step 1: 根据设定生成卷纲
     */
    @PostMapping("/generate-volume-outlines")
    public ApiResponse<List<ArcOutlineDTO>> generateVolumeOutlines(
            @Valid @RequestBody WizardSettingsRequest request) {
        try {
            List<ArcOutlineDTO> arcs = wizardService.generateVolumeOutlines(request);
            return ApiResponse.success("卷纲生成成功", arcs);
        } catch (Exception e) {
            log.error("卷纲生成失败", e);
            return ApiResponse.error(500, "卷纲生成失败: " + e.getMessage());
        }
    }

    /**
     * Step 3: 保存小说及卷纲
     */
    @PostMapping("/save")
    public ApiResponse<NovelResponse> saveNovel(
            @Valid @RequestBody WizardSaveRequest request,
            HttpServletRequest httpRequest) {
        try {
            String userId = (String) httpRequest.getAttribute("userId");
            NovelResponse response = wizardService.saveNovelWithArcs(request, userId);
            return ApiResponse.success("小说创建成功", response);
        } catch (Exception e) {
            log.error("保存小说失败", e);
            return ApiResponse.error(500, "保存小说失败: " + e.getMessage());
        }
    }

    /**
     * Step 4: 生成指定卷的所有章纲
     */
    @PostMapping("/novel/{novelId}/arc/{arcIndex}/chapter-outlines")
    public ApiResponse<List<ArcChapterOutlineDTO>> generateArcChapterOutlines(
            @PathVariable String novelId,
            @PathVariable int arcIndex) {
        try {
            List<ArcChapterOutlineDTO> outlines = wizardService.generateArcChapterOutlines(novelId, arcIndex);
            return ApiResponse.success("章纲生成成功", outlines);
        } catch (Exception e) {
            log.error("章纲生成失败", e);
            return ApiResponse.error(500, "章纲生成失败: " + e.getMessage());
        }
    }

    /**
     * Step 4: 生成指定卷的所有章纲（SSE 流式推送）
     */
    @PostMapping(value = "/novel/{novelId}/arc/{arcIndex}/chapter-outlines/stream",
                 produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generateArcChapterOutlinesStream(
            @PathVariable String novelId,
            @PathVariable int arcIndex) {
        SseEmitter emitter = new SseEmitter(600_000L); // 10min 超时

        // 异步执行，避免阻塞请求线程（超时/错误回调由 WizardServiceImpl 注册）
        CompletableFuture.runAsync(() -> {
            wizardService.generateArcChapterOutlinesStream(novelId, arcIndex, emitter);
        }, ioTaskExecutor);

        return emitter;
    }

    /**
     * Step 4: 确认/编辑章纲
     */
    @PutMapping("/novel/{novelId}/arc/{arcIndex}/chapter-outlines")
    public ApiResponse<Void> confirmChapterOutlines(
            @PathVariable String novelId,
            @PathVariable int arcIndex,
            @RequestBody List<ArcChapterOutlineDTO> outlines) {
        try {
            wizardService.confirmChapterOutlines(novelId, arcIndex, outlines);
            return ApiResponse.success("章纲已保存", null);
        } catch (Exception e) {
            log.error("保存章纲失败", e);
            return ApiResponse.error(500, "保存章纲失败: " + e.getMessage());
        }
    }

    /**
     * Step 5: 批量生成正文
     */
    @PostMapping("/novel/{novelId}/batch-generate")
    public ApiResponse<String> batchGenerate(@PathVariable String novelId) {
        try {
            String taskId = wizardService.batchGenerateChapters(novelId);
            return ApiResponse.success("批量生成任务已提交", taskId);
        } catch (Exception e) {
            log.error("批量生成启动失败", e);
            return ApiResponse.error(500, "批量生成启动失败: " + e.getMessage());
        }
    }

    /**
     * Step 5: 查询批量生成进度
     */
    @GetMapping("/novel/{novelId}/batch-status/{taskId}")
    public ApiResponse<BatchGenerationProgress> getBatchStatus(
            @PathVariable String novelId,
            @PathVariable String taskId) {
        return ApiResponse.success(wizardService.getBatchProgress(novelId, taskId));
    }

    /**
     * 取消批量生成任务
     */
    @PostMapping("/novel/{novelId}/batch-cancel/{taskId}")
    public ApiResponse<Void> batchCancel(@PathVariable String novelId, @PathVariable String taskId) {
        try {
            wizardService.cancelBatchGeneration(novelId, taskId);
            return ApiResponse.success("已请求取消", null);
        } catch (Exception e) {
            log.error("取消批量生成失败", e);
            return ApiResponse.error(500, "取消失败: " + e.getMessage());
        }
    }
}
