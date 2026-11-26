package com.novel.system.service.clue;

import com.novel.system.entity.mysql.ClueBook;
import java.util.List;

/**
 * 伏笔账本服务接口
 */
public interface ClueBookService {
    
    /**
     * 创建伏笔
     */
    ClueBook createClue(ClueBook clue);

    /**
     * 根据埋设章节推导伏笔的默认回收截止章节（deadline）
     *
     * 默认策略：deadline = 埋设章节 + DEFAULT_DEADLINE_CHAPTERS（5章），
     * 与读取侧的三个判定窗口自洽：
     * - ContextBuilderService.buildOutlineContext：chapterNum >= deadline - 5 标记「紧急」
     * - ContextBuilderService.buildWritingContext：chapterNum >= deadline - 3 标记「紧急」
     * - RuleDrivenRetrievalService 规则1：chapterNum >= deadline - 5 触发事件图谱召回
     *
     * @param buryChapter 埋设章节号
     * @return 回收截止章节；埋设章节为空时返回 null
     */
    Integer resolveDeadline(Integer buryChapter);

    /**
     * 获取小说所有伏笔
     */
    List<ClueBook> getCluesByNovelId(String novelId);
    
    /**
     * 获取未回收的伏笔
     */
    List<ClueBook> getUnrecycledClues(String novelId);
    
    /**
     * 获取高优先级未回收伏笔
     */
    List<ClueBook> getHighPriorityUnrecycledClues(String novelId);
    
    /**
     * 回收伏笔
     */
    void recycleClue(String clueId, Integer recycleChapter);
    
    /**
     * 批量回收伏笔
     */
    void batchRecycleClues(List<String> clueIds, Integer recycleChapter);
    
    /**
     * 获取在某章埋下的伏笔
     */
    List<ClueBook> getCluesByburyChapter(String novelId, Integer chapterNum);
    
    /**
     * 获取在某章回收的伏笔
     */
    List<ClueBook> getRecycledCluesByChapter(String novelId, Integer chapterNum);
}
