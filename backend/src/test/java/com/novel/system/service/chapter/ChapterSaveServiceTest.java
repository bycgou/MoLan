package com.novel.system.service.chapter;

import com.novel.system.ai.rag.RagService;
import com.novel.system.entity.mysql.NovelChapter;
import com.novel.system.mapper.NovelBaseMapper;
import com.novel.system.mapper.NovelChapterMapper;
import com.novel.system.repository.mongo.NovelGraphRepository;
import com.novel.system.service.memory.ArcSummaryService;
import com.novel.system.service.story.StoryStateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 章节保存服务单元测试
 *
 * 覆盖 arc_summaries 的写入触发点：章节落库后必须触发一次弧摘要刷新
 * （第四层滑动窗口 子层4a），且弧摘要失败不能影响章节本身保存。
 */
@ExtendWith(MockitoExtension.class)
class ChapterSaveServiceTest {

    @Mock
    private NovelBaseMapper novelBaseMapper;
    @Mock
    private NovelChapterMapper novelChapterMapper;
    @Mock
    private RagService ragService;
    @Mock
    private NovelGraphRepository novelGraphRepository;
    @Mock
    private StoryStateService storyStateService;
    @Mock
    private ArcSummaryService arcSummaryService;
    @Mock
    private Executor ioTaskExecutor;

    private ChapterSaveService service;

    @BeforeEach
    void setUp() {
        service = new ChapterSaveService(novelBaseMapper, novelChapterMapper, ragService,
                novelGraphRepository, storyStateService, arcSummaryService, ioTaskExecutor);
    }

    @Test
    void saveToMySQL_triggersArcSummaryRefresh() {
        when(novelChapterMapper.selectByNovelIdAndChapterNum("novel1", 3)).thenReturn(null);
        when(novelBaseMapper.selectById("novel1")).thenReturn(null);

        service.saveToMySQL("novel1", 3, "第三章", "本章大纲", "本章正文内容",
                100, "", "", "", "");

        verify(novelChapterMapper).insert(any(NovelChapter.class));
        verify(arcSummaryService).refreshArcSummaryIfCompleted("novel1", 3);
    }

    @Test
    void saveToMySQL_arcSummaryFailure_doesNotBreakChapterSave() {
        when(novelChapterMapper.selectByNovelIdAndChapterNum("novel1", 3)).thenReturn(null);
        when(novelBaseMapper.selectById("novel1")).thenReturn(null);
        doThrow(new RuntimeException("弧摘要写入失败"))
                .when(arcSummaryService).refreshArcSummaryIfCompleted("novel1", 3);

        assertDoesNotThrow(() -> service.saveToMySQL("novel1", 3, "第三章", "本章大纲",
                "本章正文内容", 100, "", "", "", ""));

        verify(novelChapterMapper).insert(any(NovelChapter.class));
    }
}
