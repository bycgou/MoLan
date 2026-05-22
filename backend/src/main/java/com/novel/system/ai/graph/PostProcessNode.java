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
    public NovelAgentState apply(NovelAgentState state) throws Exception {
        log.info("[{}] 保存 + 后处理", nodeName());

        NovelAgentState.FinalResult result = state.getFinalResult();
        NovelAgentState.ChapterPlan plan = state.getChapterPlan();

        chapterSaveService.saveGenerationResult(
                state.getNovelId(), state.getChapterNum(),
                "第" + state.getChapterNum() + "章 " + plan.getTitle(),
                plan.getSummary(), result.getFinalContent(),
                result.getWordCount(),
                state.getUserIntent(), result.getSummary(), null);

        try {
            postProcessService.postProcess(
                    state.getNovelId(), state.getChapterNum(),
                    result.getFinalContent(), plan.getSummary());
        } catch (Exception e) {
            log.error("后处理失败: {}", e.getMessage());
        }

        String cacheKey = "novel:context:" + state.getNovelId() + ":" + state.getChapterNum();
        redisTemplate.delete(cacheKey);

        NovelAgentState updated = state.toBuilder()
                .currentPhase(NovelAgentState.GenerationPhase.POST_PROCESS.name())
                .build();

        return StateHelper.appendTrace(updated, nodeName());
    }
}
