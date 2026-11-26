package com.novel.system.service.memory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.novel.system.entity.mysql.ArcSummary;
import com.novel.system.entity.mysql.NovelArc;
import com.novel.system.entity.mysql.NovelChapter;
import com.novel.system.mapper.ArcSummaryMapper;
import com.novel.system.mapper.NovelArcMapper;
import com.novel.system.mapper.NovelChapterMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 弧段摘要服务实现 — 第四层滑动窗口（子层4a：弧段摘要）的写入路径
 *
 * 写入时机：某个弧段（卷）的章节写完并落库之后（ChapterSaveService 调用）。
 * 数据来源：MySQL 的 novel_chapter（章节标题/章纲/正文一定在库里，最稳），
 *           不依赖 Mongo 的 chapter_summary。
 * 幂等保证：以 (novel_id, chapter_from) 作为弧段的唯一标识，
 *           已存在则更新（chapter_to + summary），不存在才插入，不会产生重复行。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArcSummaryServiceImpl implements ArcSummaryService {

    private final ArcSummaryMapper arcSummaryMapper;
    private final NovelChapterMapper chapterMapper;
    private final NovelArcMapper novelArcMapper;

    /** 弧摘要最大长度（与 ContextBuilderService 的注入习惯一致，上限 1000 字） */
    static final int MAX_SUMMARY_LENGTH = 1000;

    /** 单章要点最大长度 */
    static final int MAX_CHAPTER_DIGEST_LENGTH = 80;

    @Override
    public boolean refreshArcSummaryIfCompleted(String novelId, int chapterNum) {
        if (novelId == null || novelId.isBlank()) {
            return false;
        }

        // 1. 找到本章所属的弧段（novel_arc：start_chapter ~ end_chapter）
        NovelArc arc = novelArcMapper.findByChapterNum(novelId, chapterNum);
        if (arc == null) {
            log.debug("章节不属于任何弧段，跳过弧摘要生成: novelId={}, chapter={}", novelId, chapterNum);
            return false;
        }
        Integer startChapter = arc.getStartChapter();
        if (startChapter == null) {
            log.debug("弧段缺少起始章节号，跳过弧摘要生成: novelId={}, chapter={}", novelId, chapterNum);
            return false;
        }

        // 2. 确定弧段结束章节：
        //    - 已配置 end_chapter：写完该章即视为弧段完成
        //    - 未配置（开放弧段）：以当前最大章节号为准，每写一章都刷新一次摘要
        int chapterTo;
        if (arc.getEndChapter() != null) {
            chapterTo = arc.getEndChapter();
            if (chapterNum < chapterTo) {
                log.debug("弧段尚未写完，暂不生成弧摘要: novelId={}, chapter={}, arcEnd={}",
                        novelId, chapterNum, chapterTo);
                return false;
            }
        } else {
            chapterTo = resolveMaxChapterNum(novelId, chapterNum);
        }

        // 3. 聚合该弧段内的章节要点（来源：MySQL novel_chapter）
        List<NovelChapter> chapters = chapterMapper.selectChaptersInRange(novelId, startChapter, chapterTo);
        if (chapters == null || chapters.isEmpty()) {
            log.warn("弧段内没有已保存的章节，跳过弧摘要生成: novelId={}, range=[{},{}]",
                    novelId, startChapter, chapterTo);
            return false;
        }

        String summary = buildSummary(arc, chapters);
        if (summary.isEmpty()) {
            return false;
        }

        // 4. Upsert（幂等）：同一弧段只保留一行
        upsert(novelId, startChapter, chapterTo, summary);
        return true;
    }

    /**
     * 解析弧段结束章节（开放弧段用当前最大章节号兜底）
     */
    private int resolveMaxChapterNum(String novelId, int fallback) {
        Integer maxChapterNum = chapterMapper.selectMaxChapterNum(novelId);
        return maxChapterNum != null ? maxChapterNum : fallback;
    }

    /**
     * 聚合弧段摘要文本：每章一行「第N章《标题》：要点」
     */
    String buildSummary(NovelArc arc, List<NovelChapter> chapters) {
        StringBuilder sb = new StringBuilder();
        if (arc.getArcName() != null && !arc.getArcName().isBlank()) {
            sb.append("【弧段：").append(arc.getArcName()).append("】\n");
        }
        for (NovelChapter ch : chapters) {
            String digest = extractDigest(ch);
            if (digest.isEmpty()) {
                continue;
            }
            sb.append("第").append(ch.getChapterNum()).append("章");
            if (ch.getChapterTitle() != null && !ch.getChapterTitle().isBlank()) {
                sb.append("《").append(ch.getChapterTitle()).append("》");
            }
            sb.append("：").append(digest).append("\n");
        }
        return truncate(sb.toString(), MAX_SUMMARY_LENGTH);
    }

    /**
     * 提取单章要点：优先章摘要 → 剧情指纹 → 章纲 → 正文开头
     */
    private String extractDigest(NovelChapter ch) {
        String source = firstNonBlank(ch.getChapterSummary(), ch.getPlotFingerprint(), ch.getChapterOutline());
        if (source == null) {
            source = ch.getContent();
        }
        return truncate(source, MAX_CHAPTER_DIGEST_LENGTH);
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    /**
     * 截断文本到指定长度（尽量在行边界处截断）
     */
    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        String cut = trimmed.substring(0, maxLength);
        int lastNewline = cut.lastIndexOf('\n');
        if (lastNewline > maxLength / 2) {
            cut = cut.substring(0, lastNewline);
        }
        return cut.trim();
    }

    /**
     * Upsert 弧摘要：以 (novel_id, chapter_from) 标识一个弧段
     * 表结构没有唯一键，这里用「先查后写」实现幂等，避免重复插入多行
     */
    private void upsert(String novelId, int chapterFrom, int chapterTo, String summary) {
        List<ArcSummary> existing = arcSummaryMapper.selectList(
                new LambdaQueryWrapper<ArcSummary>()
                        .eq(ArcSummary::getNovelId, novelId)
                        .eq(ArcSummary::getChapterFrom, chapterFrom)
                        .orderByAsc(ArcSummary::getId)
                        .last("LIMIT 1"));

        if (existing != null && !existing.isEmpty()) {
            ArcSummary row = existing.get(0);
            row.setChapterTo(chapterTo);
            row.setSummary(summary);
            // 表中没有 update_time 字段，刷新时同步更新 create_time 以反映最近一次生成时间
            row.setCreateTime(LocalDateTime.now());
            arcSummaryMapper.updateById(row);
            log.info("弧摘要已更新: novelId={}, range=[{},{}], 长度={}", novelId, chapterFrom, chapterTo, summary.length());
        } else {
            ArcSummary row = new ArcSummary();
            row.setNovelId(novelId);
            row.setChapterFrom(chapterFrom);
            row.setChapterTo(chapterTo);
            row.setSummary(summary);
            row.setCreateTime(LocalDateTime.now());
            arcSummaryMapper.insert(row);
            log.info("弧摘要已生成: novelId={}, range=[{},{}], 长度={}", novelId, chapterFrom, chapterTo, summary.length());
        }
    }
}
