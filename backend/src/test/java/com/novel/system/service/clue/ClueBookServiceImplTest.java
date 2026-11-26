package com.novel.system.service.clue;

import com.novel.system.entity.mysql.ClueBook;
import com.novel.system.mapper.ClueBookMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 伏笔账本服务单元测试
 *
 * 覆盖「伏笔时限」机制的写入侧：
 * - 自动创建（自动抽取路径调用同一个 createClue）必须带上非空 deadline
 * - deadline = 埋设章节 + 默认期限章数（与读取侧判定窗口自洽）
 * - 显式指定的 deadline 不被覆盖
 */
@ExtendWith(MockitoExtension.class)
class ClueBookServiceImplTest {

    @Mock
    private ClueBookMapper clueBookMapper;

    private ClueBookServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ClueBookServiceImpl(clueBookMapper);
    }

    /**
     * 自动抽取的伏笔没有 deadline 时，应按默认策略自动补齐
     */
    @Test
    void createClue_assignsDefaultDeadline_whenDeadlineMissing() {
        ClueBook clue = new ClueBook();
        clue.setNovelId("novel1");
        clue.setClueContent("神秘玉佩里藏着的秘密");
        clue.setBuryChapter(12);
        clue.setStatus(0);

        ClueBook saved = service.createClue(clue);

        assertNotNull(saved.getDeadline(), "自动创建的伏笔必须带上非空 deadline");
        assertEquals(12 + ClueBookServiceImpl.DEFAULT_DEADLINE_CHAPTERS, saved.getDeadline());
        assertEquals(17, saved.getDeadline());

        // 落库实体必须携带 deadline（ClueBookMapper.insert 语句已包含 deadline 列）
        ArgumentCaptor<ClueBook> captor = ArgumentCaptor.forClass(ClueBook.class);
        verify(clueBookMapper).insert(captor.capture());
        assertEquals(17, captor.getValue().getDeadline());
    }

    /**
     * 手动指定 deadline 时不得被默认策略覆盖
     */
    @Test
    void createClue_keepsExplicitDeadline() {
        ClueBook clue = new ClueBook();
        clue.setNovelId("novel1");
        clue.setClueContent("手动设定的回收期限");
        clue.setBuryChapter(3);
        clue.setDeadline(20);

        ClueBook saved = service.createClue(clue);

        assertEquals(20, saved.getDeadline());
        verify(clueBookMapper).insert(any(ClueBook.class));
    }

    /**
     * 埋设章节未知时无法推导期限，deadline 保持为空且不抛异常
     */
    @Test
    void createClue_leavesDeadlineNull_whenBuryChapterMissing() {
        ClueBook clue = new ClueBook();
        clue.setNovelId("novel1");
        clue.setClueContent("没有埋设章节的伏笔");
        clue.setStatus(0);

        ClueBook saved = service.createClue(clue);

        assertNull(saved.getDeadline());
        verify(clueBookMapper).insert(any(ClueBook.class));
    }

    /**
     * 默认期限推导规则：埋设章节 + DEFAULT_DEADLINE_CHAPTERS
     */
    @Test
    void resolveDeadline_returnsBuryChapterPlusDefault() {
        assertEquals(8, service.resolveDeadline(3));
        assertEquals(5, service.resolveDeadline(0));
        assertNull(service.resolveDeadline(null));
    }

    /**
     * 默认期限取值必须与读取侧的判定窗口自洽：
     * - deadline-5：ContextBuilderService.buildOutlineContext / RuleDrivenRetrievalService 规则1
     * - deadline-3：ContextBuilderService.buildWritingContext
     */
    @Test
    void defaultDeadline_isSelfConsistentWithRetrievalWindows() {
        int buryChapter = 10;
        Integer deadline = service.resolveDeadline(buryChapter);

        assertNotNull(deadline);
        // 埋设当章即进入 deadline-5 窗口（规则1 可召回伏笔埋设事件）
        assertTrue(buryChapter >= deadline - 5);
        // 埋设后第3章进入 deadline-3 窗口（写作阶段提示「紧急」）
        assertFalse(buryChapter >= deadline - 3);
        assertTrue(buryChapter + 3 >= deadline - 3);
    }
}
