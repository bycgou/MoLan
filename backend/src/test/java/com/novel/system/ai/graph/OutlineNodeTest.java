package com.novel.system.ai.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.system.ai.chain.ChapterGenerationChain;
import com.novel.system.entity.mysql.NovelArc;
import com.novel.system.entity.mysql.NovelBase;
import com.novel.system.mapper.NovelArcMapper;
import com.novel.system.mapper.NovelBaseMapper;
import com.novel.system.service.chapter.ContextBuilderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutlineNodeTest {

    @Mock private ChapterGenerationChain chain;
    @Mock private ContextBuilderService contextBuilderService;
    @Mock private NovelBaseMapper novelBaseMapper;
    @Mock private NovelArcMapper novelArcMapper;

    private OutlineNode node;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        node = new OutlineNode(chain, contextBuilderService, novelBaseMapper, novelArcMapper, objectMapper);
    }

    @Test
    void buildsOutlineContext_andStoresInState() throws Exception {
        NovelBase novel = new NovelBase();
        novel.setNovelId("n1");
        when(novelBaseMapper.selectById("n1")).thenReturn(novel);
        when(novelArcMapper.findByChapterNum("n1", 1)).thenReturn(null);
        when(contextBuilderService.buildOutlineContext(eq("n1"), eq(1), eq(novel), isNull(), isNull()))
                .thenReturn("built-context");
        when(chain.executeOutlineChain("n1", 1, "built-context", null))
                .thenReturn("{\"title\":\"第一章\",\"summary\":\"概要\",\"hook\":\"钩子\",\"key_points\":[\"点1\"],\"ideas\":[\"思路1\"]}");

        NovelAgentState input = NovelAgentState.builder().novelId("n1").chapterNum(1).build();
        NovelAgentState result = node.apply(input);

        assertEquals("built-context", result.getOutlineContext());
        assertNotNull(result.getChapterPlan());
        assertEquals("第一章", result.getChapterPlan().getTitle());
        assertEquals("概要", result.getChapterPlan().getSummary());
        assertEquals("钩子", result.getChapterPlan().getHook());
        assertEquals(1, result.getChapterPlan().getKeyPoints().size());
        assertEquals(1, result.getChapterPlan().getImplementationIdeas().size());
        assertEquals("outline", result.getExecutionTrace().get(0));
    }

    @Test
    void handlesParseFailureGracefully() throws Exception {
        NovelBase novel = new NovelBase();
        when(novelBaseMapper.selectById("n1")).thenReturn(novel);
        when(novelArcMapper.findByChapterNum("n1", 1)).thenReturn(null);
        when(contextBuilderService.buildOutlineContext(any(), anyInt(), any(), any(), any()))
                .thenReturn("ctx");
        when(chain.executeOutlineChain(any(), anyInt(), any(), any()))
                .thenReturn("not valid json at all but has some text");

        NovelAgentState input = NovelAgentState.builder().novelId("n1").chapterNum(1).build();
        NovelAgentState result = node.apply(input);

        assertNotNull(result.getChapterPlan());
        assertNotNull(result.getChapterPlan().getSummary());
    }
}
