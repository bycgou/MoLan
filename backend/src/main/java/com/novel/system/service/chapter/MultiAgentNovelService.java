package com.novel.system.service.chapter;

import com.novel.system.ai.graph.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class MultiAgentNovelService {

    private final GraphRunner runner;
    private final ArcOrchestrationNode arcCheck;
    private final OutlineNode outline;
    private final CluePlanningNode cluePlan;
    private final CharacterPlanningNode charPlan;
    private final WorldValidatorNode worldValidate;

    public MultiAgentNovelService(
            ArcOrchestrationNode arcCheck,
            OutlineNode outline,
            CluePlanningNode cluePlan,
            CharacterPlanningNode charPlan,
            WorldValidatorNode worldValidate,
            IdeaSelectionNode ideaSelect,
            WritingNode writing,
            SelfReviewNode selfReview,
            ValidationNode validation,
            QualityScoringNode qualityScoring,
            AuditRouterNode auditRouter,
            SummaryNode summary,
            PostProcessNode postProcess,
            AuthorAgent authorAgent) {

        this.arcCheck = arcCheck;
        this.outline = outline;
        this.cluePlan = cluePlan;
        this.charPlan = charPlan;
        this.worldValidate = worldValidate;

        this.runner = new GraphRunner();
        this.runner.register(arcCheck);
        this.runner.register(outline);
        this.runner.register(cluePlan);
        this.runner.register(charPlan);
        this.runner.register(worldValidate);
        this.runner.register(ideaSelect);
        this.runner.register(writing);
        this.runner.register(selfReview);
        this.runner.register(validation);
        this.runner.register(qualityScoring);
        this.runner.register(auditRouter);
        this.runner.register(summary);
        this.runner.register(postProcess);
        this.runner.setAuthorAgent(authorAgent);
    }

    public AgentExecutionResult writeChapter(String novelId, int chapterNum,
                                              String userIntent, String styleCardId,
                                              String llmConfigId) throws Exception {
        long startTime = System.currentTimeMillis();

        NovelAgentState state = NovelAgentState.builder()
                .novelId(novelId)
                .chapterNum(chapterNum)
                .userIntent(userIntent)
                .styleCardId(styleCardId)
                .llmConfigId(llmConfigId)
                .build();

        // Phase 1: Planning (with parallel cluePlan + charPlan)
        state = runPhase1(state);

        // Phase 2+: idea_select → post_process (index 5 in NODE_ORDER)
        // audit_router 的 REPLAN 路由仍能正常工作，因为 Phase 1 节点仍在 runner 中注册
        NovelAgentState finalState = runner.executeFrom(state, 5);

        long elapsed = System.currentTimeMillis() - startTime;

        NovelAgentState.FinalResult result = finalState.getFinalResult();
        return AgentExecutionResult.builder()
                .content(result != null ? result.getFinalContent() : "")
                .wordCount(result != null ? result.getWordCount() : 0)
                .qualityScore(result != null ? result.getQualityScore() : 0)
                .chapterId(result != null ? result.getChapterId() : null)
                .executionTrace(finalState.getExecutionTrace().stream()
                        .map(r -> r.nodeName() + (r.metadata() != null && r.metadata().containsKey("message")
                                ? "(" + r.metadata().get("message") + ")" : ""))
                        .collect(Collectors.toList()))
                .errors(finalState.getErrors().stream()
                        .map(e -> e.nodeName() != null ? e.nodeName() + ": " + e.message() : e.message())
                        .collect(Collectors.toList()))
                .elapsedMs(elapsed)
                .build();
    }

    /**
     * Phase 1: arcCheck -> outline -> [cluePlan || charPlan] -> worldValidate
     * cluePlan 和 charPlan 互不依赖，并行执行以节省 ~30s
     */
    private NovelAgentState runPhase1(NovelAgentState state) throws Exception {
        state = arcCheck.apply(state);
        state = outline.apply(state);

        // cluePlan 和 charPlan 互不依赖，并行执行
        final NovelAgentState afterOutline = state;
        CompletableFuture<NovelAgentState> clueFuture =
                CompletableFuture.supplyAsync(() -> {
                    try { return cluePlan.apply(afterOutline); }
                    catch (Exception e) { throw new RuntimeException(e); }
                });
        CompletableFuture<NovelAgentState> charFuture =
                CompletableFuture.supplyAsync(() -> {
                    try { return charPlan.apply(afterOutline); }
                    catch (Exception e) { throw new RuntimeException(e); }
                });

        try {
            NovelAgentState clueState = clueFuture.get(60, TimeUnit.SECONDS);
            NovelAgentState charState = charFuture.get(60, TimeUnit.SECONDS);
            // 合并两个结果：取 clueState 的 clueActions + charState 的 characterActions
            state = clueState.toBuilder()
                    .chapterPlan(clueState.getChapterPlan().toBuilder()
                            .characterActions(charState.getChapterPlan().getCharacterActions())
                            .build())
                    .build();
        } catch (Exception e) {
            log.error("并行策划失败，回退到串行: {}", e.getMessage());
            state = cluePlan.apply(afterOutline);
            state = charPlan.apply(state);
        }

        state = worldValidate.apply(state);
        return state;
    }
}
