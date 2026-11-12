package com.novel.system.service.chapter;

import com.novel.system.config.RedisKeyProvider;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.system.ai.agent.SummaryAgentResponse;
import com.novel.system.ai.graph.AgentExecutionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * 多Agent编排章节生成服务 — 实现 ChapterGenerationService 接口
 *
 * generateChapter 委托给 MultiAgentNovelService（新编排器）
 * 其他方法（generateOutline、extractSummaryOnly、saveManualChapter）委托给旧实现
 *
 * 通过 novel.generation.engine 配置切换：
 * - multi-agent（默认）：使用新编排器
 * - legacy：使用旧 ChapterGenerationServiceImpl
 */
@Slf4j
@Service
@Primary
@ConditionalOnProperty(name = "novel.generation.engine", havingValue = "multi-agent", matchIfMissing = true)
public class MultiAgentChapterGenerationService implements ChapterGenerationService {

    private final MultiAgentNovelService multiAgent;
    private final ChapterGenerationServiceImpl legacyService;
    private final StringRedisTemplate redisTemplate;
    private final RedisKeyProvider redisKeys;
    private final ObjectMapper objectMapper;
    private final Executor ioTaskExecutor;

    public MultiAgentChapterGenerationService(
            MultiAgentNovelService multiAgent,
            ChapterGenerationServiceImpl legacyService,
            StringRedisTemplate redisTemplate,
            RedisKeyProvider redisKeys,
            ObjectMapper objectMapper,
            @Qualifier("ioTaskExecutor") Executor ioTaskExecutor) {
        this.multiAgent = multiAgent;
        this.legacyService = legacyService;
        this.redisTemplate = redisTemplate;
        this.redisKeys = redisKeys;
        this.objectMapper = objectMapper;
        this.ioTaskExecutor = ioTaskExecutor;
    }

    @Override
    public ChapterGenerationResponse generateChapter(ChapterGenerationRequest request) {
        log.info("[MultiAgent] 生成章节, novelId={}, chapter={}",
                request.getNovelId(), request.getChapterNum());

        try {
            AgentExecutionResult result = multiAgent.writeChapter(
                    request.getNovelId(),
                    request.getChapterNum(),
                    request.getUserIntent(),
                    request.getStyleCardId(),
                    request.getLlmConfigId());

            ChapterGenerationResponse response = toResponse(result);
            log.info("[MultiAgent] 章节生成完成, 字数={}, 质量={}, 耗时={}ms",
                    result.getWordCount(), result.getQualityScore(), result.getElapsedMs());
            return response;

        } catch (Exception e) {
            log.error("[MultiAgent] 章节生成异常: {}", e.getMessage(), e);
            ChapterGenerationResponse response = new ChapterGenerationResponse();
            response.setSuccess(false);
            response.setStatus(ChapterGenerationResponse.GenerationStatus.FAILED);
            response.setErrorMessage("章节生成失败: " + e.getMessage());
            return response;
        }
    }

    @Override
    public ChapterGenerationResponse generateOutline(ChapterGenerationRequest request) {
        return legacyService.generateOutline(request);
    }

    @Override
    public ChapterGenerationResponse regenerateChapter(ChapterGenerationRequest request) {
        return generateChapter(request);
    }

    @Override
    public String generateChapterAsync(ChapterGenerationRequest request) {
        String taskId = UUID.randomUUID().toString();
        log.info("[MultiAgent] 启动异步章节生成, taskId={}", taskId);

        updateTaskStatus(taskId, "GENERATING", "章节生成中...");

        SecurityContext securityContext = SecurityContextHolder.getContext();
        CompletableFuture.runAsync(() -> {
            SecurityContextHolder.setContext(securityContext);
            try {
                ChapterGenerationResponse response = generateChapter(request);
                updateTaskStatus(taskId, response);
                log.info("[MultiAgent] 异步章节生成完成, taskId={}", taskId);
            } catch (Exception e) {
                log.error("[MultiAgent] 异步章节生成失败, taskId={}", taskId, e);
                updateTaskStatus(taskId, "FAILED", "章节生成失败: " + e.getMessage());
            } finally {
                SecurityContextHolder.clearContext();
            }
        }, ioTaskExecutor);

        return taskId;
    }

    @Override
    public ChapterGenerationResponse getGenerationStatus(String taskId) {
        return legacyService.getGenerationStatus(taskId);
    }

    @Override
    public SummaryAgentResponse extractSummaryOnly(ChapterGenerationRequest request, String content) {
        return legacyService.extractSummaryOnly(request, content);
    }

    @Override
    public ChapterGenerationResponse saveManualChapter(ChapterGenerationRequest request, String content, SummaryAgentResponse summaryResponse) {
        return legacyService.saveManualChapter(request, content, summaryResponse);
    }

    @Override
    public void waitForPostProcessing() {
        legacyService.waitForPostProcessing();
    }

    private ChapterGenerationResponse toResponse(AgentExecutionResult result) {
        ChapterGenerationResponse response = new ChapterGenerationResponse();
        boolean hasErrors = result.getErrors() != null && !result.getErrors().isEmpty();

        response.setSuccess(!hasErrors || result.getContent() != null && !result.getContent().isEmpty());
        response.setContent(result.getContent());
        response.setWordCount(result.getWordCount());
        response.setChapterId(result.getChapterId());
        response.setPlotFingerprint(result.getExecutionTrace() != null
                ? String.join(" → ", result.getExecutionTrace()) : "");
        response.setValidationSummary(String.format("质量评分: %.1f", result.getQualityScore()));
        response.setStatus(hasErrors
                ? ChapterGenerationResponse.GenerationStatus.COMPLETED
                : ChapterGenerationResponse.GenerationStatus.COMPLETED);

        if (hasErrors) {
            response.setErrorMessage(String.join("; ", result.getErrors()));
        }

        return response;
    }

    private void updateTaskStatus(String taskId, String status, String message) {
        try {
            String statusKey = redisKeys.taskStatus() + taskId;
            ChapterGenerationResponse response = new ChapterGenerationResponse();
            response.setSuccess("COMPLETED".equals(status));
            response.setStatus(ChapterGenerationResponse.GenerationStatus.valueOf(status));
            response.setErrorMessage(message);
            redisTemplate.opsForValue().set(statusKey, objectMapper.writeValueAsString(response), 24, TimeUnit.HOURS);
        } catch (Exception e) {
            log.error("保存任务状态到Redis失败", e);
        }
    }

    private void updateTaskStatus(String taskId, ChapterGenerationResponse response) {
        try {
            String statusKey = redisKeys.taskStatus() + taskId;
            redisTemplate.opsForValue().set(statusKey, objectMapper.writeValueAsString(response), 24, TimeUnit.HOURS);
        } catch (JsonProcessingException e) {
            log.error("保存任务状态到Redis失败", e);
        }
    }
}
