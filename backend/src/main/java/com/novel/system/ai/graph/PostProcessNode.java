package com.novel.system.ai.graph;

import com.novel.system.service.chapter.ChapterPostProcessService;
import com.novel.system.service.chapter.ChapterSaveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 后处理节点 — 保存章节 + 人物/伏笔/时间线提取 + 清除缓存
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostProcessNode implements NovelGraphNode {

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

        // 保存章节（带重试）
        boolean saved = false;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                chapterSaveService.saveGenerationResult(
                        state.getNovelId(), state.getChapterNum(),
                        "第" + state.getChapterNum() + "章 " + plan.getTitle(),
                        plan.getSummary(), result.getFinalContent(),
                        result.getWordCount(),
                        state.getUserIntent(), result.getSummary(), null);
                saved = true;
                break;
            } catch (Exception e) {
                log.error("章节保存失败（第{}次尝试）: {}", attempt, e.getMessage());
                if (attempt == 3) {
                    state = StateHelper.appendError(state, "章节保存失败: " + e.getMessage());
                }
            }
        }

        // 后处理（非关键，失败不阻断）
        if (saved) {
            try {
                postProcessService.postProcess(
                        state.getNovelId(), state.getChapterNum(),
                        result.getFinalContent(), plan.getSummary());
            } catch (Exception e) {
                log.error("后处理失败: {}", e.getMessage());
            }
        }

        // 清除缓存（非关键，失败不阻断）
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
}
