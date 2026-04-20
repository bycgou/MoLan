package com.novel.system.service.clue;

import com.novel.system.entity.mysql.ClueBook;
import com.novel.system.mapper.ClueBookMapper;
import com.novel.system.service.clue.ClueBookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 伏笔账本服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClueBookServiceImpl implements ClueBookService {
    
    private final ClueBookMapper clueBookMapper;
    
    @Override
    public ClueBook createClue(ClueBook clue) {
        clue.setClueId(UUID.randomUUID().toString().replace("-", ""));
        clue.setCreateTime(LocalDateTime.now());
        clue.setUpdateTime(LocalDateTime.now());
        clueBookMapper.insert(clue);
        return clue;
    }
    
    @Override
    public List<ClueBook> getCluesByNovelId(String novelId) {
        return clueBookMapper.selectByNovelId(novelId);
    }
    
    @Override
    public List<ClueBook> getUnrecycledClues(String novelId) {
        return clueBookMapper.selectUnrecycledByNovelId(novelId);
    }
    
    @Override
    public List<ClueBook> getHighPriorityUnrecycledClues(String novelId) {
        return clueBookMapper.selectHighPriorityUnrecycled(novelId);
    }
    
    @Override
    public void recycleClue(String clueId, Integer recycleChapter) {
        ClueBook clue = clueBookMapper.selectById(clueId);
        if (clue != null) {
            clue.setStatus(1);
            clue.setRecycleChapter(recycleChapter);
            clue.setUpdateTime(LocalDateTime.now());
            clueBookMapper.updateById(clue);
            log.info("伏笔已回收: {}，回收章节: {}", clueId, recycleChapter);
        }
    }
    
    @Override
    public void batchRecycleClues(List<String> clueIds, Integer recycleChapter) {
        for (String clueId : clueIds) {
            recycleClue(clueId, recycleChapter);
        }
    }
    
    @Override
    public List<ClueBook> getCluesByburyChapter(String novelId, Integer chapterNum) {
        return clueBookMapper.selectByBuryChapter(novelId, chapterNum);
    }
    
    @Override
    public List<ClueBook> getRecycledCluesByChapter(String novelId, Integer chapterNum) {
        return clueBookMapper.selectRecycledByChapter(novelId, chapterNum);
    }
}
