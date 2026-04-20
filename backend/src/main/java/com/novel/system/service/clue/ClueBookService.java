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
