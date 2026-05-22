package com.novel.system.ai.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.system.entity.mongo.ArcOrchestration;
import com.novel.system.entity.mysql.ClueBook;
import com.novel.system.service.clue.ClueBookService;
import com.novel.system.service.orchestration.ArcOrchestrationService;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CluePlanningNodeTest {

    @Mock private ChatModel plannerModel;
    @Mock private ClueBookService clueBookService;
    @Mock private ArcOrchestrationService arcOrchestrationService;

    private CluePlanningNode node;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        node = new CluePlanningNode(plannerModel, clueBookService,
                arcOrchestrationService, objectMapper);
    }

    @Test
    void mergesOrchestrationConstraints_withLLMSupplement() throws Exception {
        // 卷级编排指定埋设一个伏笔
        ArcOrchestration.ChapterPlan chapterPlan = new ArcOrchestration.ChapterPlan();
        ArcOrchestration.CluePlan cluePlan = new ArcOrchestration.CluePlan();
        cluePlan.setBury(List.of("密信伏笔"));
        cluePlan.setRecycle(List.of());
        chapterPlan.setCluePlan(cluePlan);
        when(arcOrchestrationService.getChapterPlan("n1", 1)).thenReturn(chapterPlan);
        when(clueBookService.getUnrecycledClues("n1")).thenReturn(List.of());

        // LLM 返回补充的伏笔操作
        when(plannerModel.chat(anyString()))
                .thenReturn("[{\"clueId\":\"旧伏笔\",\"action\":\"ADVANCE\",\"description\":\"推进旧伏笔\"}]");

        NovelAgentState input = NovelAgentState.builder()
                .novelId("n1").chapterNum(1)
                .chapterPlan(NovelAgentState.ChapterPlan.builder().summary("概要").build())
                .build();

        NovelAgentState result = node.apply(input);

        List<NovelAgentState.ClueAction> actions = result.getChapterPlan().getClueActions();
        assertEquals(2, actions.size());
        // 第一个是卷级编排硬约束
        assertTrue(actions.get(0).isFromOrchestration());
        assertEquals("PLANT", actions.get(0).getAction());
        assertEquals("密信伏笔", actions.get(0).getClueId());
        // 第二个是 LLM 补充
        assertFalse(actions.get(1).isFromOrchestration());
        assertEquals("ADVANCE", actions.get(1).getAction());
    }
}
