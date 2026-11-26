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

    /**
     * 默认伏笔期限章数：伏笔埋设后必须在该章数内回收。
     *
     * 取值 5 的依据（必须与读取侧的判定窗口自洽，否则「伏笔时限」机制形同虚设）：
     * - ContextBuilderService.buildOutlineContext：chapterNum >= deadline - 5 时标注「紧急」
     * - ContextBuilderService.buildWritingContext：chapterNum >= deadline - 3 时标注「紧急」
     * - RuleDrivenRetrievalService 规则1：chapterNum >= deadline - 5 时触发事件图谱召回
     *
     * 取 5 意味着「埋设后 5 章内必须处理」：
     * - 埋设当章即进入 deadline-5 窗口（规则1 从埋设章起即可召回伏笔埋设事件）
     * - 埋设后第 3 章起进入 deadline-3 窗口（写作阶段开始提示「紧急」）
     */
    public static final int DEFAULT_DEADLINE_CHAPTERS = 5;

    private final ClueBookMapper clueBookMapper;

    @Override
    public ClueBook createClue(ClueBook clue) {
        clue.setClueId(UUID.randomUUID().toString().replace("-", ""));
        clue.setCreateTime(LocalDateTime.now());
        clue.setUpdateTime(LocalDateTime.now());
        // 默认期限策略：未显式指定 deadline 时，按「埋设章节 + 5章」自动推导，
        // 保证伏笔账本的 deadline 恒不为空，紧急提示与规则1召回才能真正生效
        if (clue.getDeadline() == null) {
            Integer defaultDeadline = resolveDeadline(clue.getBuryChapter());
            if (defaultDeadline != null) {
                clue.setDeadline(defaultDeadline);
                log.info("伏笔未指定回收期限，按默认策略自动设置: clueId={}, 埋设章节={}, deadline={}",
                        clue.getClueId(), clue.getBuryChapter(), defaultDeadline);
            }
        }
        clueBookMapper.insert(clue);
        return clue;
    }

    @Override
    public Integer resolveDeadline(Integer buryChapter) {
        if (buryChapter == null) {
            return null;
        }
        return buryChapter + DEFAULT_DEADLINE_CHAPTERS;
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
