package com.novel.system.controller;

import com.novel.system.ai.chain.ValidationService;
import com.novel.system.ai.chain.ValidationRequest;
import com.novel.system.ai.chain.ValidationResult;
import com.novel.system.entity.mysql.NovelBase;
import com.novel.system.entity.mysql.NovelChapter;
import com.novel.system.entity.mysql.NovelStyleBinding;
import com.novel.system.mapper.NovelBaseMapper;
import com.novel.system.mapper.NovelChapterMapper;
import com.novel.system.mapper.NovelStyleBindingMapper;
import com.novel.system.service.chapter.ChapterPostProcessService;
import com.novel.system.service.chapter.ChapterSaveService;
import com.novel.system.service.chapter.ChapterVersionService;
import com.novel.system.service.chapter.MultiAgentNovelService;
import com.novel.system.service.story.StoryStateService;
import com.novel.system.ai.chain.ChapterGenerationChain;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * SSE 流式写作控制器
 * 通过 GraphRunner(FAST) 执行 arc_check → outline → writing → self_review → summary → post_process
 * WritingNode 通过 SseEmitter 逐token输出
 */
@Slf4j
@RestController
@RequestMapping("/chapter")
@RequiredArgsConstructor
public class StreamingChapterController {

    private final StringRedisTemplate redisTemplate;
    private final NovelBaseMapper novelBaseMapper;
    private final NovelChapterMapper novelChapterMapper;
    private final NovelStyleBindingMapper novelStyleBindingMapper;
    private final ChapterSaveService chapterSaveService;
    private final ValidationService validationService;
    private final ChapterPostProcessService chapterPostProcessService;
    private final ChapterVersionService chapterVersionService;
    private final StoryStateService storyStateService;
    private final ChapterGenerationChain chapterGenerationChain;
    private final MultiAgentNovelService multiAgentNovelService;
    private final ObjectMapper objectMapper;
    private final Executor ioTaskExecutor;

    /**
     * 流式写作请求
     */
    @Data
    public static class StreamWriteRequest {
        private String novelId;
        private Integer chapterNum;
        private String outline;
        private String contextPrompt;
        private String llmConfigId;
        private String styleCardId;
        private String userInstruction;
    }

    /**
     * SSE 流式写作 — 通过 GraphRunner(FAST) 执行
     */
    @PostMapping(value = "/stream-write", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamWrite(@RequestBody StreamWriteRequest request) {
        SseEmitter emitter = new SseEmitter(600_000L); // 10min 超时

        emitter.onTimeout(() -> {
            log.warn("SSE流式写作超时: novelId={}, chapter={}", request.getNovelId(), request.getChapterNum());
            try {
                emitter.send(SseEmitter.event().name("error").data("生成超时，已生成的内容可能已保存，请刷新查看"));
            } catch (Exception ignored) {}
            emitter.complete();
        });

        emitter.onError(e -> {
            log.warn("SSE流式写作连接异常: novelId={}, error={}", request.getNovelId(), e.getMessage());
        });

        String cancelKey = "novel:cancel:" + request.getNovelId();
        SecurityContext securityContext = SecurityContextHolder.getContext();

        CompletableFuture.runAsync(() -> {
            try {
                redisTemplate.delete(cancelKey);

                // 重写场景 — 保存版本快照
                try {
                    NovelChapter existingChapter = novelChapterMapper.selectByNovelIdAndChapterNum(
                            request.getNovelId(), request.getChapterNum());
                    if (existingChapter != null && existingChapter.getContent() != null
                            && existingChapter.getWordCount() != null && existingChapter.getWordCount() > 0) {
                        chapterVersionService.saveSnapshot(existingChapter);
                        log.info("已保存章节版本快照: novelId={}, chapterNum={}", request.getNovelId(), request.getChapterNum());
                    }
                } catch (Exception e) {
                    log.warn("保存版本快照失败，继续生成: {}", e.getMessage());
                }

                // 获取小说信息（用于后续验证）
                NovelBase novel = novelBaseMapper.selectById(request.getNovelId());
                if (novel == null) {
                    emitter.completeWithError(new RuntimeException("小说不存在: " + request.getNovelId()));
                    return;
                }

                // 解析 styleCardId（如果前端未提供）
                String effectiveStyleCardId = request.getStyleCardId();
                if (effectiveStyleCardId == null || effectiveStyleCardId.isEmpty()) {
                    NovelStyleBinding binding = novelStyleBindingMapper.selectByNovelId(request.getNovelId());
                    if (binding != null && binding.getCardId() != null) {
                        effectiveStyleCardId = binding.getCardId();
                    }
                }

                // 通过 GraphRunner(FAST) 执行，WritingNode 会流式输出到 emitter
                String finalStyleCardId = effectiveStyleCardId;
                multiAgentNovelService.writeChapterStreaming(
                        request.getNovelId(),
                        request.getChapterNum(),
                        request.getUserInstruction(),
                        finalStyleCardId,
                        request.getLlmConfigId(),
                        emitter);

                // Graph 执行完成（WritingNode 流式已结束），获取结果
                // 注意：GraphRunner 是同步的，writeChapterStreaming 返回时流式已完成
                // 此时需要做验证、保存、后处理

                // 获取 novel 最新状态（GraphRunner 可能已更新）
                novel = novelBaseMapper.selectById(request.getNovelId());

                // 发送 complete 事件
                try {
                    emitter.send(SseEmitter.event().name("complete").data(""));
                    emitter.complete();
                } catch (Exception sendEx) {
                    log.warn("发送complete事件失败: {}", sendEx.getMessage());
                    try { emitter.complete(); } catch (Exception ignored) {}
                }

            } catch (Exception e) {
                log.error("流式写作失败: {}", e.getMessage(), e);
                try {
                    emitter.send(SseEmitter.event().name("error").data("生成失败: " + e.getMessage()));
                } catch (Exception ignored) {}
                try { emitter.complete(); } catch (Exception ignored) {}
            } finally {
                SecurityContextHolder.clearContext();
            }
        }, ioTaskExecutor);

        return emitter;
    }

    /**
     * 取消写作
     */
    @PostMapping("/cancel")
    public ResponseEntity<?> cancelWriting(@RequestParam String novelId) {
        redisTemplate.opsForValue().set("novel:cancel:" + novelId, "1", 10, TimeUnit.MINUTES);
        return ResponseEntity.ok().build();
    }

    /**
     * 保存流式生成的内容（取消后或校验失败后手动保存）
     */
    @PostMapping("/stream-save")
    public ResponseEntity<?> streamSave(@RequestBody Map<String, String> payload) {
        String novelId = payload.get("novelId");
        Integer chapterNum = Integer.parseInt(payload.get("chapterNum"));
        String content = payload.get("content");
        String outline = payload.getOrDefault("outline", "");
        String llmConfigId = payload.get("llmConfigId");

        if (novelId == null || content == null || content.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "内容不能为空"));
        }

        // 生成摘要
        String summaryContent = "";
        try {
            String summaryJson = chapterGenerationChain.executeSummaryChain(
                novelId, chapterNum, content, outline, llmConfigId);
            if (summaryJson != null && !summaryJson.isEmpty()) {
                summaryContent = summaryJson.length() > 300
                    ? summaryJson.substring(0, 300) : summaryJson;
            }
        } catch (Exception e) {
            log.warn("生成摘要失败: {}", e.getMessage());
        }

        int wordCount = content.replaceAll("\\s+", "").length();
        chapterSaveService.saveGenerationResult(
                novelId, chapterNum,
                "第" + chapterNum + "章",
                outline, content, wordCount,
                "", summaryContent, null);

        log.info("流式内容手动保存成功, 小说ID: {}, 章节号: {}, 字数: {}", novelId, chapterNum, wordCount);
        return ResponseEntity.ok(Map.of("success", true, "wordCount", wordCount));
    }
}
