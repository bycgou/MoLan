package com.novel.system.ai.graph;

import com.novel.system.entity.mongo.ChapterSummary;
import com.novel.system.service.chapter.ChapterPostProcessService;
import com.novel.system.service.chapter.ChapterSaveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class PostProcessNode implements NovelGraphNode {

    private static final long SAVE_WAIT_TIMEOUT_SECONDS = 60;

    private final ChapterSaveService chapterSaveService;
    private final ChapterPostProcessService postProcessService;
    private final StringRedisTemplate redisTemplate;

    @Override
    public String nodeName() { return "post_process"; }

    @Override
    public boolean shouldSkip(NovelAgentState state) {
        return state.getFinalResult() != null && state.getFinalResult().getChapterId() != null;
    }

    @Override
    public NovelAgentState apply(NovelAgentState state) throws Exception {
        log.info("[{}] 保存 + 后处理", nodeName());

        NovelAgentState.FinalResult result = state.getFinalResult();
        if (result == null || result.getFinalContent() == null) {
            log.warn("[{}] finalResult 为空，跳过保存", nodeName());
            return state.toBuilder()
                    .currentPhase(NovelAgentState.GenerationPhase.POST_PROCESS.name())
                    .build();
        }
        NovelAgentState.ChapterPlan plan = state.getChapterPlan();

        ChapterSummary chapterSummary = buildChapterSummary(state, plan);
        String plotFingerprint = result.getSummary();
        String hook = plan.getHook() != null ? plan.getHook() : "";
        String content = result.getFinalContent();
        String actualEndingHook = content != null && content.length() > 200
                ? content.substring(content.length() - 200)
                : (content != null ? content : "");

        boolean saved = false;
        CompletableFuture<Void> memoryCommitFuture = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                memoryCommitFuture = chapterSaveService.saveGenerationResultAsync(
                        state.getNovelId(), state.getChapterNum(),
                        "第" + state.getChapterNum() + "章" + plan.getTitle(),
                        plan.getSummary(), result.getFinalContent(),
                        result.getWordCount(),
                        state.getUserIntent(), plotFingerprint, chapterSummary,
                        hook, actualEndingHook);
                saved = true;
                break;
            } catch (Exception e) {
                log.error("章节保存失败（第{}次尝试）: {}", attempt, e.getMessage());
                if (attempt == 3) {
                    state = StateHelper.appendError(state, "章节保存失败: " + e.getMessage());
                }
            }
        }

        if (saved) {
            waitForMemoryCommit(memoryCommitFuture);
            try {
                CompletableFuture<Void> postProcessFuture =
                        postProcessService.postProcess(
                                state.getNovelId(), state.getChapterNum(),
                                result.getFinalContent(), plan.getSummary());
                try {
                    postProcessFuture.get(120, TimeUnit.SECONDS);
                } catch (java.util.concurrent.TimeoutException e) {
                    log.warn("后处理超时，继续: chapter={}", state.getChapterNum());
                } catch (Exception e) {
                    log.warn("后处理异常: chapter={}, error={}", state.getChapterNum(), e.getMessage());
                }
            } catch (Exception e) {
                log.error("后处理失败: {}", e.getMessage());
            }
        }

        try {
            String cacheKey = "novel:context:" + state.getNovelId() + ":" + state.getChapterNum();
            redisTemplate.delete(cacheKey);
        } catch (Exception e) {
            log.warn("清除缓存失败（不影响结果）: {}", e.getMessage());
        }

        NovelAgentState updated = state.toBuilder()
                .currentPhase(NovelAgentState.GenerationPhase.POST_PROCESS.name())
                .build();

        return StateHelper.appendTrace(updated, nodeName());
    }

    private ChapterSummary buildChapterSummary(NovelAgentState state, NovelAgentState.ChapterPlan plan) {
        try {
            ChapterSummary summary = new ChapterSummary();
            summary.setNovelId(state.getNovelId());
            summary.setChapterNum(state.getChapterNum());
            summary.setContent(plan.getSummary());
            summary.setKeywords(Collections.emptyList());
            summary.setCharacters(Collections.emptyList());
            summary.setLocations(Collections.emptyList());
            summary.setRelations(Collections.emptyList());
            return summary;
        } catch (Exception e) {
            log.warn("构建 ChapterSummary 失败，回退到 null: {}", e.getMessage());
            return null;
        }
    }

    private void waitForMemoryCommit(CompletableFuture<Void> future) {
        if (future == null) {
            return;
        }
        try {
            future.get(SAVE_WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("等待记忆提交超时或中断: {}", e.getMessage());
        }
    }
}