package com.novel.system.service.memory;

/**
 * 弧段摘要服务 — 五层记忆架构第四层：滑动窗口（子层4a：弧段摘要）
 *
 * 弧段摘要用于防止 AI 对早期情节失忆：某个弧段（卷）的章节写完后，
 * 把该弧段内各章的要点压缩成一条摘要，写入 arc_summaries 表，
 * 供 ContextBuilderService 按「章节区间」注入到章纲/正文上下文。
 *
 * 读写粒度对齐（重要）：
 * - 写入：一条记录 = 一个弧段，chapter_from = 弧段起始章节，chapter_to = 弧段结束章节
 * - 读取：ContextBuilderService 调用 selectByNovelIdAndRange(novelId, chapterNum)，
 *         取 chapter_to <= 当前章节号 的全部弧摘要，按 chapter_from 升序注入
 */
public interface ArcSummaryService {

    /**
     * 弧段章节写完后，生成/更新该弧段的摘要（幂等，不会产生重复行）
     *
     * @param novelId 小说ID
     * @param chapterNum 刚写入的章节号
     * @return true 表示本次确实写入（新增或更新）了弧摘要
     */
    boolean refreshArcSummaryIfCompleted(String novelId, int chapterNum);
}
