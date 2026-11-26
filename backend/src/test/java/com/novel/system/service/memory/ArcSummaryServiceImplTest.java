package com.novel.system.service.memory;

import com.novel.system.entity.mysql.ArcSummary;
import com.novel.system.entity.mysql.NovelArc;
import com.novel.system.entity.mysql.NovelChapter;
import com.novel.system.mapper.ArcSummaryMapper;
import com.novel.system.mapper.NovelArcMapper;
import com.novel.system.mapper.NovelChapterMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 弧段摘要服务单元测试
 *
 * 覆盖 arc_summaries 表此前完全没有写入路径的问题：
 * - 弧段章节写完后写入一条弧摘要（chapter_from/chapter_to 与读取方区间查询一致）
 * - 重复触发只更新不重复插入（幂等）
 * - 弧段未写完 / 章节不属于任何弧段时不写入
 */
@ExtendWith(MockitoExtension.class)
class ArcSummaryServiceImplTest {

    @Mock
    private ArcSummaryMapper arcSummaryMapper;
    @Mock
    private NovelChapterMapper chapterMapper;
    @Mock
    private NovelArcMapper novelArcMapper;

    private ArcSummaryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ArcSummaryServiceImpl(arcSummaryMapper, chapterMapper, novelArcMapper);
    }

    /**
     * 弧段最后一章写完后，应写入一条弧摘要
     */
    @Test
    void refreshArcSummaryIfCompleted_insertsSummary_whenArcFinished() {
        NovelArc arc = arc("arc1", "初入江湖", 1, 3);
        when(novelArcMapper.findByChapterNum("novel1", 3)).thenReturn(arc);
        when(chapterMapper.selectChaptersInRange("novel1", 1, 3)).thenReturn(List.of(
                chapter(1, "第一章 落脚", "少年辞别故乡，踏入江湖"),
                chapter(2, "第二章 拜师", "少年拜入青云门，结识师姐"),
                chapter(3, "第三章 遇袭", "少年遭神秘黑衣人伏击")));
        when(arcSummaryMapper.selectList(any())).thenReturn(Collections.emptyList());

        boolean written = service.refreshArcSummaryIfCompleted("novel1", 3);

        assertTrue(written, "弧段写完应写入弧摘要");

        ArgumentCaptor<ArcSummary> captor = ArgumentCaptor.forClass(ArcSummary.class);
        verify(arcSummaryMapper).insert(captor.capture());
        ArcSummary row = captor.getValue();
        assertEquals("novel1", row.getNovelId());
        assertEquals(1, row.getChapterFrom());
        assertEquals(3, row.getChapterTo());
        assertNotNull(row.getSummary());
        assertTrue(row.getSummary().contains("少年辞别故乡"), "摘要应聚合弧段内各章要点");
        assertTrue(row.getSummary().contains("青云门"));
        assertTrue(row.getSummary().length() <= 1000, "摘要长度应受限（<=1000字）");
        verify(arcSummaryMapper, never()).updateById(any());
    }

    /**
     * 幂等：同一弧段重复触发只更新既有行，不产生重复行
     */
    @Test
    void refreshArcSummaryIfCompleted_updatesExistingRow_whenTriggeredTwice() {
        NovelArc arc = arc("arc1", "初入江湖", 1, 3);
        when(novelArcMapper.findByChapterNum("novel1", 3)).thenReturn(arc);
        when(chapterMapper.selectChaptersInRange("novel1", 1, 3)).thenReturn(List.of(
                chapter(1, "第一章 落脚", "少年辞别故乡"),
                chapter(2, "第二章 拜师", "少年拜入青云门"),
                chapter(3, "第三章 遇袭", "少年遭伏击")));

        // 已有同一弧段（novel_id + chapter_from）的摘要行
        ArcSummary existing = new ArcSummary();
        existing.setId(7L);
        existing.setNovelId("novel1");
        existing.setChapterFrom(1);
        existing.setChapterTo(3);
        existing.setSummary("旧摘要");
        when(arcSummaryMapper.selectList(any())).thenReturn(List.of(existing));

        boolean written = service.refreshArcSummaryIfCompleted("novel1", 3);

        assertTrue(written);
        verify(arcSummaryMapper, never()).insert(any(ArcSummary.class));
        verify(arcSummaryMapper).updateById(existing);
        assertEquals(3, existing.getChapterTo());
        assertTrue(existing.getSummary().contains("青云门"));
        assertNotEquals("旧摘要", existing.getSummary());
    }

    /**
     * 弧段尚未写完时不写入弧摘要
     */
    @Test
    void refreshArcSummaryIfCompleted_skips_whenArcNotFinished() {
        when(novelArcMapper.findByChapterNum("novel1", 2)).thenReturn(arc("arc1", "初入江湖", 1, 3));

        boolean written = service.refreshArcSummaryIfCompleted("novel1", 2);

        assertFalse(written);
        verify(arcSummaryMapper, never()).insert(any(ArcSummary.class));
        verify(arcSummaryMapper, never()).updateById(any(ArcSummary.class));
        verify(chapterMapper, never()).selectChaptersInRange(anyString(), any(), any());
    }

    /**
     * 章节不属于任何弧段时不写入
     */
    @Test
    void refreshArcSummaryIfCompleted_skips_whenChapterNotInAnyArc() {
        when(novelArcMapper.findByChapterNum("novel1", 5)).thenReturn(null);

        boolean written = service.refreshArcSummaryIfCompleted("novel1", 5);

        assertFalse(written);
        verify(arcSummaryMapper, never()).insert(any(ArcSummary.class));
    }

    /**
     * 弧段内没有已保存章节时不写入空摘要
     */
    @Test
    void refreshArcSummaryIfCompleted_skips_whenNoChaptersInRange() {
        when(novelArcMapper.findByChapterNum("novel1", 3)).thenReturn(arc("arc1", "初入江湖", 1, 3));
        when(chapterMapper.selectChaptersInRange("novel1", 1, 3)).thenReturn(Collections.emptyList());

        boolean written = service.refreshArcSummaryIfCompleted("novel1", 3);

        assertFalse(written);
        verify(arcSummaryMapper, never()).insert(any(ArcSummary.class));
    }

    /**
     * 开放弧段（未配置 end_chapter）：以当前最大章节号为准刷新摘要区间
     */
    @Test
    void refreshArcSummaryIfCompleted_usesMaxChapterForOpenEndedArc() {
        NovelArc arc = arc("arc1", "长线卷", 1, null);
        when(novelArcMapper.findByChapterNum("novel1", 4)).thenReturn(arc);
        when(chapterMapper.selectMaxChapterNum("novel1")).thenReturn(4);
        when(chapterMapper.selectChaptersInRange("novel1", 1, 4)).thenReturn(List.of(
                chapter(1, "第一章", "开篇"),
                chapter(4, "第四章", "当前进度")));
        when(arcSummaryMapper.selectList(any())).thenReturn(Collections.emptyList());

        boolean written = service.refreshArcSummaryIfCompleted("novel1", 4);

        assertTrue(written);
        ArgumentCaptor<ArcSummary> captor = ArgumentCaptor.forClass(ArcSummary.class);
        verify(arcSummaryMapper).insert(captor.capture());
        assertEquals(1, captor.getValue().getChapterFrom());
        assertEquals(4, captor.getValue().getChapterTo());
    }

    private NovelArc arc(String arcId, String arcName, Integer startChapter, Integer endChapter) {
        NovelArc arc = new NovelArc();
        arc.setArcId(arcId);
        arc.setNovelId("novel1");
        arc.setArcName(arcName);
        arc.setStartChapter(startChapter);
        arc.setEndChapter(endChapter);
        return arc;
    }

    private NovelChapter chapter(int num, String title, String outline) {
        NovelChapter ch = new NovelChapter();
        ch.setChapterId("ch" + num);
        ch.setNovelId("novel1");
        ch.setChapterNum(num);
        ch.setChapterTitle(title);
        ch.setChapterOutline(outline);
        ch.setContent(outline + "（正文）");
        return ch;
    }
}
