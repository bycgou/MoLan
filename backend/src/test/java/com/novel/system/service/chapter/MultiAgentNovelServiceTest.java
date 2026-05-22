package com.novel.system.service.chapter;

import com.novel.system.ai.graph.*;
import com.novel.system.entity.mysql.NovelBase;
import com.novel.system.mapper.NovelBaseMapper;
import com.novel.system.mapper.NovelArcMapper;
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
    @Mock private ValidationNode validation;
    @Mock private QualityScoringNode qualityScoring;
    @Mock private SummaryNode summary;
    @Mock private PostProcessNode postProcess;

    private MultiAgentNovelService service;

    @BeforeEach
    void setUp() {
        service = new MultiAgentNovelService(
                arcCheck, outline, cluePlan, charPlan, worldValidate,
                ideaSelect, writing, selfReview, validation,
                qualityScoring, summary, postProcess);
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
        when(validation.apply(any())).thenAnswer(inv -> {
            NovelAgentState s = inv.getArgument(0);
            return s.toBuilder()
                    .auditResult(NovelAgentState.AuditResult.builder().allPassed(true).build())
                    .build();
        });
        when(qualityScoring.apply(any())).thenAnswer(inv -> inv.getArgument(0));
        when(summary.apply(any())).thenAnswer(inv -> {
            NovelAgentState s = inv.getArgument(0);
            return s.toBuilder()
                    .finalResult(NovelAgentState.FinalResult.builder()
                            .finalContent("章节内容").wordCount(4).summary("指纹").build())
                    .build();
        });
        when(postProcess.apply(any())).thenAnswer(inv -> inv.getArgument(0));

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
