package com.novel.system.service.chapter;

import com.novel.system.ai.memory.CharacterGraphService;
import com.novel.system.ai.rag.RagService;
import com.novel.system.entity.mongo.ChapterSummary;
import com.novel.system.entity.mongo.StoryState;
import com.novel.system.entity.mysql.NovelBase;
import com.novel.system.entity.mysql.NovelChapter;
import com.novel.system.mapper.ArcSummaryMapper;
import com.novel.system.mapper.NovelChapterMapper;
import com.novel.system.service.character.CharacterStateService;
import com.novel.system.service.clue.ClueBookService;
import com.novel.system.service.event.EventGraphService;
import com.novel.system.service.event.RuleDrivenRetrievalService;
import com.novel.system.service.memory.CompletedBeatsService;
import com.novel.system.service.memory.ConvergenceService;
import com.novel.system.service.memory.EchoRecallService;
import com.novel.system.service.memory.EmotionLedgerService;
import com.novel.system.service.memory.FactLockService;
import com.novel.system.service.orchestration.ArcOrchestrationService;
import com.novel.system.service.story.StoryStateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ContextBuilderService RAG Layer 6 注入测试
 *
 * 验证 RAG hybridSearch 结果正确注入到写作上下文和章纲上下文中，
 * 以及异常情况下的优雅降级。
 */
class ContextBuilderServiceRagTest {

    @Mock
    private ArcSummaryMapper arcSummaryMapper;
    @Mock
    private NovelChapterMapper chapterMapper;
    @Mock
    private CharacterStateService characterStateService;
    @Mock
    private ClueBookService clueBookService;
    @Mock
    private StoryStateService storyStateService;
    @Mock
    private TokenBudgetService tokenBudgetService;
    @Mock
    private CharacterGraphService characterGraphService;
    @Mock
    private ArcOrchestrationService arcOrchestrationService;
    @Mock
    private RuleDrivenRetrievalService ruleDrivenRetrievalService;
    @Mock
    private EventGraphService eventGraphService;
    @Mock
    private RagService ragService;
    @Mock
    private FactLockService factLockService;
    @Mock
    private EmotionLedgerService emotionLedgerService;
    @Mock
    private CompletedBeatsService completedBeatsService;
    @Mock
    private EchoRecallService echoRecallService;
    @Mock
    private ConvergenceService convergenceService;

    private ContextBuilderService contextBuilderService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // TokenBudgetService: return a budget with generous allocations
        TokenBudgetService.TokenBudget budget = new TokenBudgetService.TokenBudget();
        budget.setTotalTokens(8192);
        budget.getAllocation().put("arcSummary", 2000);
        budget.getAllocation().put("recentOutline", 2000);
        budget.getAllocation().put("storyState", 2000);
        budget.getAllocation().put("characterState", 2000);
        budget.getAllocation().put("clueBook", 1000);
        budget.getAllocation().put("novelOutline", 2000);
        budget.getAllocation().put("worldSetting", 2000);
        when(tokenBudgetService.calculateBudget(anyInt())).thenReturn(budget);
        when(tokenBudgetService.truncateToBudget(anyString(), anyInt())).thenAnswer(inv -> inv.getArgument(0));

        // Default: empty results for all mappers/services
        when(arcSummaryMapper.selectByNovelIdAndRange(anyString(), anyInt())).thenReturn(Collections.emptyList());
        when(chapterMapper.selectRecentOutlines(anyString(), anyInt(), anyInt())).thenReturn(Collections.emptyList());
        when(chapterMapper.selectByNovelIdAndChapterNum(anyString(), anyInt())).thenReturn(null);
        when(clueBookService.getUnrecycledClues(anyString())).thenReturn(Collections.emptyList());
        when(characterStateService.formatForPrompt(anyString())).thenReturn(null);
        when(characterGraphService.formatGraphAsString(anyString())).thenReturn(null);
        when(storyStateService.getOrCreate(anyString())).thenReturn(new StoryState());
        when(storyStateService.formatForPrompt(any())).thenReturn(null);
        when(ruleDrivenRetrievalService.autoRetrieve(anyString(), anyInt(), anyString(), anyList()))
            .thenReturn(Collections.emptyList());
        when(arcOrchestrationService.getChapterPlan(anyString(), anyInt())).thenReturn(null);

        contextBuilderService = new ContextBuilderService(
            arcSummaryMapper,
            chapterMapper,
            characterStateService,
            clueBookService,
            storyStateService,
            tokenBudgetService,
            characterGraphService,
            arcOrchestrationService,
            ruleDrivenRetrievalService,
            eventGraphService,
            ragService,
            factLockService,
            emotionLedgerService,
            completedBeatsService,
            echoRecallService,
            convergenceService
        );
    }

    /**
     * RAG 检索结果应注入到写作上下文中
     */
    @Test
    void buildWritingContext_ragResultsInjected() {
        // Arrange
        ChapterSummary cs1 = new ChapterSummary();
        cs1.setChapterNum(3);
        cs1.setContent("主角在古墓中发现了神秘玉佩，玉佩上刻着古老的符文。");

        ChapterSummary cs2 = new ChapterSummary();
        cs2.setChapterNum(7);
        cs2.setContent("主角与反派在青云山巅展开激战，最终主角险胜。");

        when(ragService.hybridSearch(eq("novel1"), eq("主角前往古墓探险"), eq(5)))
            .thenReturn(List.of(cs1, cs2));

        NovelChapter chapter = new NovelChapter();
        chapter.setChapterNum(10);
        chapter.setChapterTitle("第十章");
        chapter.setChapterOutline("主角前往古墓探险");
        chapter.setContent("正文内容");

        NovelBase novel = buildNovelBase();

        // Act
        String context = contextBuilderService.buildWritingContext(
            "novel1", 10, chapter, novel, "探索古墓");

        // Assert
        assertTrue(context.contains("【相关历史剧情（RAG 自动检索，写作时必须保持一致）】"),
            "应包含 RAG 注入标题");
        assertTrue(context.contains("- 第3章:"), "应包含第3章摘要");
        assertTrue(context.contains("- 第7章:"), "应包含第7章摘要");
        assertTrue(context.contains("神秘玉佩"), "应包含第3章内容");
        assertTrue(context.contains("青云山巅"), "应包含第7章内容");

        verify(ragService).hybridSearch("novel1", "主角前往古墓探险", 5);
    }

    /**
     * RAG 检索返回空列表或 null 时，不应注入任何内容，也不应报错
     */
    @Test
    void buildWritingContext_ragEmptyResults_noInjection() {
        // Arrange - hybridSearch returns empty list
        when(ragService.hybridSearch(anyString(), anyString(), eq(5)))
            .thenReturn(Collections.emptyList());

        NovelChapter chapter = new NovelChapter();
        chapter.setChapterNum(10);
        chapter.setChapterTitle("第十章");
        chapter.setChapterOutline("主角前往古墓探险");
        chapter.setContent("正文内容");

        NovelBase novel = buildNovelBase();

        // Act
        String context = contextBuilderService.buildWritingContext(
            "novel1", 10, chapter, novel, "探索古墓");

        // Assert
        assertFalse(context.contains("【相关历史剧情（RAG 自动检索，写作时必须保持一致）】"),
            "空结果不应注入 RAG 内容");
    }

    /**
     * RAG 检索返回 null 时，不应注入任何内容，也不应报错
     */
    @Test
    void buildWritingContext_ragNullResults_noInjection() {
        // Arrange - hybridSearch returns null
        when(ragService.hybridSearch(anyString(), anyString(), eq(5)))
            .thenReturn(null);

        NovelChapter chapter = new NovelChapter();
        chapter.setChapterNum(10);
        chapter.setChapterTitle("第十章");
        chapter.setChapterOutline("主角前往古墓探险");
        chapter.setContent("正文内容");

        NovelBase novel = buildNovelBase();

        // Act
        String context = contextBuilderService.buildWritingContext(
            "novel1", 10, chapter, novel, "探索古墓");

        // Assert
        assertFalse(context.contains("【相关历史剧情（RAG 自动检索，写作时必须保持一致）】"),
            "null 结果不应注入 RAG 内容");
    }

    /**
     * RAG 检索异常时不应阻断生成流程
     */
    @Test
    void buildWritingContext_ragSearchFailure_doesNotBlockGeneration() {
        // Arrange - hybridSearch throws exception
        when(ragService.hybridSearch(anyString(), anyString(), eq(5)))
            .thenThrow(new RuntimeException("MongoDB 连接超时"));

        NovelChapter chapter = new NovelChapter();
        chapter.setChapterNum(10);
        chapter.setChapterTitle("第十章");
        chapter.setChapterOutline("主角前往古墓探险");
        chapter.setContent("正文内容");

        NovelBase novel = buildNovelBase();

        // Act - should not throw
        String context = contextBuilderService.buildWritingContext(
            "novel1", 10, chapter, novel, "探索古墓");

        // Assert - context should still be generated (not empty)
        assertNotNull(context, "异常时上下文不应为空");
        assertFalse(context.isEmpty(), "异常时上下文不应为空字符串");
        assertFalse(context.contains("【相关历史剧情（RAG 自动检索，写作时必须保持一致）】"),
            "异常时不应注入 RAG 内容");
    }

    /**
     * 章纲上下文也应注入 RAG 检索结果
     */
    @Test
    void buildOutlineContext_ragResultsInjected() {
        // Arrange
        ChapterSummary cs1 = new ChapterSummary();
        cs1.setChapterNum(5);
        cs1.setContent("主角在青云门拜师，师父传授了基础剑法。");

        when(ragService.hybridSearch(eq("novel1"), eq("主角修炼突破"), eq(5)))
            .thenReturn(List.of(cs1));

        NovelBase novel = buildNovelBase();

        // Act
        String context = contextBuilderService.buildOutlineContext(
            "novel1", 10, novel, "修炼卷", "主角修炼突破");

        // Assert
        assertTrue(context.contains("【相关历史剧情（RAG 自动检索，写作时必须保持一致）】"),
            "章纲上下文应包含 RAG 注入标题");
        assertTrue(context.contains("- 第5章:"), "应包含第5章摘要");
        assertTrue(context.contains("青云门拜师"), "应包含第5章内容");

        verify(ragService).hybridSearch("novel1", "主角修炼突破", 5);
    }

    /**
     * 章纲上下文中 RAG 检索异常不应阻断生成
     */
    @Test
    void buildOutlineContext_ragSearchFailure_doesNotBlockGeneration() {
        // Arrange
        when(ragService.hybridSearch(anyString(), anyString(), eq(5)))
            .thenThrow(new RuntimeException("向量索引不可用"));

        NovelBase novel = buildNovelBase();

        // Act - should not throw
        String context = contextBuilderService.buildOutlineContext(
            "novel1", 10, novel, "修炼卷", "主角修炼突破");

        // Assert
        assertNotNull(context, "异常时章纲上下文不应为空");
        assertFalse(context.isEmpty(), "异常时章纲上下文不应为空字符串");
    }

    /**
     * 章纲上下文中 arcOutline 为 null 时不应调用 RAG
     */
    @Test
    void buildOutlineContext_nullArcOutline_noRagCall() {
        // Arrange
        NovelBase novel = buildNovelBase();

        // Act
        contextBuilderService.buildOutlineContext(
            "novel1", 10, novel, "修炼卷", null);

        // Assert - should not call hybridSearch at all
        verify(ragService, never()).hybridSearch(anyString(), anyString(), anyInt());
    }

    /**
     * 章纲上下文中 arcOutline 为空字符串时不应调用 RAG
     */
    @Test
    void buildOutlineContext_emptyArcOutline_noRagCall() {
        // Arrange
        NovelBase novel = buildNovelBase();

        // Act
        contextBuilderService.buildOutlineContext(
            "novel1", 10, novel, "修炼卷", "");

        // Assert
        verify(ragService, never()).hybridSearch(anyString(), anyString(), anyInt());
    }

    /**
     * 写作上下文中 chapterOutline 为 null 时不应调用 RAG
     */
    @Test
    void buildWritingContext_nullChapterOutline_noRagCall() {
        // Arrange
        NovelChapter chapter = new NovelChapter();
        chapter.setChapterNum(10);
        chapter.setChapterTitle("第十章");
        chapter.setChapterOutline(null);
        chapter.setContent("正文内容");

        NovelBase novel = buildNovelBase();

        // Act
        contextBuilderService.buildWritingContext(
            "novel1", 10, chapter, novel, "探索古墓");

        // Assert
        verify(ragService, never()).hybridSearch(anyString(), anyString(), anyInt());
    }

    /**
     * RAG 结果超过 200 字时应被截断
     */
    @Test
    void buildWritingContext_longContentIsTruncated() {
        // Arrange - content > 200 chars
        String longContent = "这是一段很长的剧情摘要".repeat(30); // ~300 chars
        ChapterSummary cs = new ChapterSummary();
        cs.setChapterNum(3);
        cs.setContent(longContent);

        when(ragService.hybridSearch(anyString(), anyString(), eq(5)))
            .thenReturn(List.of(cs));

        NovelChapter chapter = new NovelChapter();
        chapter.setChapterNum(10);
        chapter.setChapterTitle("第十章");
        chapter.setChapterOutline("测试大纲");
        chapter.setContent("正文");

        NovelBase novel = buildNovelBase();

        // Act
        String context = contextBuilderService.buildWritingContext(
            "novel1", 10, chapter, novel, null);

        // Assert
        assertTrue(context.contains("..."), "长内容应被截断并附加省略号");
        // The full long content should NOT appear
        assertFalse(context.contains(longContent), "完整长内容不应全部出现");
    }

    // === Helper ===

    private NovelBase buildNovelBase() {
        NovelBase novel = new NovelBase();
        novel.setNovelId("novel1");
        novel.setNovelName("测试小说");
        novel.setTotalOutline("这是一个关于修炼的故事");
        novel.setWorldSetting("玄幻世界，灵气复苏");
        return novel;
    }
}
