package com.novel.system.service.chapter;

import com.novel.system.ai.graph.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MultiAgentNovelServiceTest {

    @Mock private ArcOrchestrationNode arcCheck;
    @Mock private OutlineNode outline;
    @Mock private CluePlanningNode cluePlan;
    @Mock private CharacterPlanningNode charPlan;
    @Mock private WorldValidatorNode worldValidate;
    @Mock private IdeaSelectionNode ideaSelect;
    @Mock private WritingNode writing;
    @Mock private SelfReviewNode selfReview;
    @Mock private DeslopCheckNode deslopCheck;
    @Mock private ValidationNode validation;
    @Mock private QualityScoringNode qualityScoring;
    @Mock private AuditRouterNode auditRouter;
    @Mock private SummaryNode summary;
    @Mock private PostProcessNode postProcess;
    @Mock private AuthorAgent authorAgent;

    private MultiAgentNovelService service;

    @BeforeEach
    void setUp() {
        // Stub nodeName() for all mocks — ConcurrentHashMap rejects null keys
        when(arcCheck.nodeName()).thenReturn("arc_check");
        when(outline.nodeName()).thenReturn("outline");
        when(cluePlan.nodeName()).thenReturn("clue_planning");
        when(charPlan.nodeName()).thenReturn("character_planning");
        when(worldValidate.nodeName()).thenReturn("world_validate");
        when(ideaSelect.nodeName()).thenReturn("idea_select");
        when(writing.nodeName()).thenReturn("writing");
        when(selfReview.nodeName()).thenReturn("self_review");
        when(deslopCheck.nodeName()).thenReturn("deslop_check");
        when(validation.nodeName()).thenReturn("validation");
        when(qualityScoring.nodeName()).thenReturn("quality_scoring");
        when(auditRouter.nodeName()).thenReturn("audit_router");
        when(summary.nodeName()).thenReturn("summary");
        when(postProcess.nodeName()).thenReturn("post_process");
        ContinuityValidationNode continuityValidation = mock(ContinuityValidationNode.class);
        when(continuityValidation.nodeName()).thenReturn("continuity_validation");
        FactLockViolationNode factLockViolation = mock(FactLockViolationNode.class);
        when(factLockViolation.nodeName()).thenReturn("fact_lock_violation");

        service = new MultiAgentNovelService(
                arcCheck, outline, cluePlan, charPlan, worldValidate,
                ideaSelect, writing, selfReview, deslopCheck, continuityValidation, validation,
                qualityScoring, auditRouter, summary, postProcess, authorAgent, factLockViolation, null);
    }

    @Test
    void happyPath_executesAllNodes() throws Exception {
        NovelAgentState base = NovelAgentState.builder().novelId("n1").chapterNum(1).build();

        when(arcCheck.apply(any())).thenAnswer(inv -> {
            NovelAgentState s = inv.getArgument(0);
            return s.toBuilder().currentPhase("ARC_CHECK").build();
        });
        when(outline.apply(any())).thenAnswer(inv -> {
            NovelAgentState s = inv.getArgument(0);
            return s.toBuilder()
                    .outlineContext("ctx")
                    .chapterPlan(NovelAgentState.ChapterPlan.builder().title("T").summary("S").build())
                    .currentPhase("OUTLINE").build();
        });
        when(cluePlan.apply(any())).thenAnswer(inv -> inv.getArgument(0));
        when(charPlan.apply(any())).thenAnswer(inv -> inv.getArgument(0));
        when(worldValidate.apply(any())).thenAnswer(inv -> {
            NovelAgentState s = inv.getArgument(0);
            return s.toBuilder()
                    .preWriteValidation(NovelAgentState.PreWriteValidation.builder().passed(true).build())
                    .build();
        });
        when(ideaSelect.apply(any())).thenAnswer(inv -> inv.getArgument(0));
        when(writing.apply(any())).thenAnswer(inv -> {
            NovelAgentState s = inv.getArgument(0);
            return s.toBuilder()
                    .draftContent(NovelAgentState.DraftContent.builder()
                            .content("章节内容").wordCount(4).build())
                    .build();
        });
        when(selfReview.apply(any())).thenAnswer(inv -> inv.getArgument(0));
        when(deslopCheck.apply(any())).thenAnswer(inv -> inv.getArgument(0));
        when(validation.apply(any())).thenAnswer(inv -> {
            NovelAgentState s = inv.getArgument(0);
            return s.toBuilder()
                    .auditResult(NovelAgentState.AuditResult.builder().allPassed(true).build())
                    .build();
        });
        when(qualityScoring.apply(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditRouter.apply(any())).thenAnswer(inv -> {
            NovelAgentState s = inv.getArgument(0);
            return s.toBuilder().auditDecision("PASS").build();
        });
        when(summary.apply(any())).thenAnswer(inv -> {
            NovelAgentState s = inv.getArgument(0);
            return s.toBuilder()
                    .finalResult(NovelAgentState.FinalResult.builder()
                            .finalContent("章节内容").wordCount(4).summary("指纹").build())
                    .build();
        });
        when(postProcess.apply(any())).thenAnswer(inv -> inv.getArgument(0));

        // AuthorAgent returns APPROVE for all checkpoints
        when(authorAgent.beforeNode(any(), any())).thenReturn(
                AuthorDirective.builder().action(AuthorDirective.Action.APPROVE).build());

        AgentExecutionResult result = service.writeChapter("n1", 1, null, null, null);

        assertEquals("章节内容", result.getContent());
        assertEquals(4, result.getWordCount());
        assertNotNull(result.getExecutionTrace());

        verify(arcCheck).apply(any());
        verify(outline).apply(any());
        verify(cluePlan).apply(any());
        verify(charPlan).apply(any());
        verify(worldValidate).apply(any());
        verify(ideaSelect).apply(any());
        verify(writing).apply(any());
        verify(selfReview).apply(any());
        verify(validation).apply(any());
        verify(qualityScoring).apply(any());
        verify(summary).apply(any());
        verify(postProcess).apply(any());
    }
}
