package com.novel.system.service.chapter;

import com.novel.system.ai.graph.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MultiAgentNovelService {

    private final GraphRunner runner;

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
            AuthorAgent authorAgent,
            @org.springframework.beans.factory.annotation.Autowired(required = false) StateCheckpointService checkpointService) {

        GraphDefinition definition = GraphDefinition.builder()
                .edge("arc_check", "outline")
                .edge("outline", "clue_planning", Mode.FULL)
                .edge("outline", "writing", Mode.FAST)
                .fanOut("outline", List.of("clue_planning", "character_planning"), Mode.FULL, MergeStrategy.chapterPlanMerge())
                .fanIn(List.of("clue_planning", "character_planning"), "world_validate", Mode.FULL)
                .edge("world_validate", "idea_select", Mode.FULL)
                .edge("idea_select", "writing", Mode.FULL)
                .edge("writing", "self_review")
                .conditionalEdge("self_review", "writing",
                        s -> s.getDraftContent() != null
                                && s.getDraftContent().getSeverity() == NovelAgentState.Severity.HIGH
                                && s.getWriteRetryCount() < 2)
                .conditionalEdge("self_review", "summary", s -> s.getMode() == Mode.FAST)
                .conditionalEdge("self_review", "validation", s -> s.getMode() == Mode.FULL)
                .edge("validation", "quality_scoring", Mode.FULL)
                .edge("quality_scoring", "audit_router", Mode.FULL)
                .conditionalEdge("audit_router", "summary",
                        s -> "PASS".equals(s.getAuditDecision()), Mode.FULL)
                .conditionalEdge("audit_router", "outline",
                        s -> "REPLAN".equals(s.getAuditDecision()), Mode.FULL)
                .conditionalEdge("audit_router", "writing",
                        s -> "REWRITE".equals(s.getAuditDecision()), Mode.FULL)
                .edge("summary", "post_process")
                .checkpoint("outline")
                .checkpoint("writing")
                .checkpoint("audit_router")
                .retryPolicy("outline", 2, "summary")
                .retryPolicy("writing", 3, "summary")
                .retryPolicy("audit_router", 2, "summary")
                .build();

        this.runner = new GraphRunner(definition);
        runner.register(arcCheck);
        runner.register(outline);
        runner.register(cluePlan);
        runner.register(charPlan);
        runner.register(worldValidate);
        runner.register(ideaSelect);
        runner.register(writing);
        runner.register(selfReview);
        runner.register(validation);
        runner.register(qualityScoring);
        runner.register(auditRouter);
        runner.register(summary);
        runner.register(postProcess);

        // NodeExecutor 统一管理 AuthorAgent 拦截 + 检查点
        NodeExecutor nodeExecutor = new NodeExecutor(authorAgent, checkpointService, definition);
        runner.setNodeExecutor(nodeExecutor);
        runner.setCheckpointService(checkpointService);
    }

    public AgentExecutionResult writeChapter(String novelId, int chapterNum,
                                              String userIntent, String styleCardId,
                                              String llmConfigId) throws Exception {
        return doWriteChapter(novelId, chapterNum, userIntent, styleCardId, llmConfigId, null);
    }

    /**
     * 流式写章节：通过 SseEmitter 逐token输出，使用 Mode.FAST 跳过 clue/character planning。
     */
    public AgentExecutionResult writeChapterStreaming(String novelId, int chapterNum,
                                                       String userIntent, String styleCardId,
                                                       String llmConfigId,
                                                       SseEmitter sseEmitter) throws Exception {
        return doWriteChapter(novelId, chapterNum, userIntent, styleCardId, llmConfigId, sseEmitter);
    }

    private AgentExecutionResult doWriteChapter(String novelId, int chapterNum,
                                                 String userIntent, String styleCardId,
                                                 String llmConfigId,
                                                 SseEmitter sseEmitter) throws Exception {
        long startTime = System.currentTimeMillis();

        NovelAgentState.NovelAgentStateBuilder stateBuilder = NovelAgentState.builder()
                .novelId(novelId)
                .chapterNum(chapterNum)
                .userIntent(userIntent)
                .styleCardId(styleCardId)
                .llmConfigId(llmConfigId);
        if (sseEmitter != null) {
            stateBuilder.sseEmitter(sseEmitter);
        }
        NovelAgentState state = stateBuilder.build();

        // FAST mode: arc_check -> outline -> writing -> self_review -> summary -> post_process
        // FULL mode: adds clue_planning + character_planning + world_validate + idea_select + validation + quality_scoring + audit_router
        Mode mode = sseEmitter != null ? Mode.FAST : Mode.FULL;
        NovelAgentState finalState = runner.execute(state, mode);

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
}
