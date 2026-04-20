package com.novel.system.ai.tool;

import dev.langchain4j.agent.tool.Tool;
import com.novel.system.entity.mysql.ClueBook;
import com.novel.system.service.clue.ClueBookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 伏笔工具 - Agent可调用的伏笔查询工具
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClueTool {
    
    private final ClueBookService clueBookService;
    
    @Tool("查询小说中所有未回收的伏笔（线索），返回伏笔内容和优先级。用于确保新章节不遗漏需要回收的伏笔。")
    public String getUnrecycledClues(String novelId) {
        log.info("[Tool] 查询未回收伏笔: novelId={}", novelId);
        List<ClueBook> clues = clueBookService.getUnrecycledClues(novelId);
        if (clues.isEmpty()) {
            return "暂无未回收的伏笔";
        }
        
        StringBuilder sb = new StringBuilder();
        for (ClueBook clue : clues) {
            sb.append("• ");
            if (clue.getPriority() != null && clue.getPriority() == 1) sb.append("[高优先级] ");
            sb.append(clue.getClueContent());
            sb.append(" (埋于第").append(clue.getBuryChapter()).append("章)");
            sb.append("\n");
        }
        return sb.toString();
    }
    
    @Tool("查询小说中高优先级未回收的伏笔，这些伏笔必须尽快回收。")
    public String getHighPriorityClues(String novelId) {
        log.info("[Tool] 查询高优先级伏笔: novelId={}", novelId);
        List<ClueBook> clues = clueBookService.getHighPriorityUnrecycledClues(novelId);
        if (clues.isEmpty()) {
            return "暂无高优先级未回收伏笔";
        }
        
        StringBuilder sb = new StringBuilder();
        for (ClueBook clue : clues) {
            sb.append("• ").append(clue.getClueContent());
            sb.append(" (埋于第").append(clue.getBuryChapter()).append("章)");
            sb.append("\n");
        }
        return sb.toString();
    }
    
    @Tool("查询在指定章节埋下的伏笔")
    public String getCluesByBuryChapter(String novelId, int chapterNum) {
        log.info("[Tool] 查询章节埋下的伏笔: novelId={}, chapter={}", novelId, chapterNum);
        List<ClueBook> clues = clueBookService.getCluesByburyChapter(novelId, chapterNum);
        if (clues.isEmpty()) {
            return "该章节未埋下伏笔";
        }
        
        StringBuilder sb = new StringBuilder();
        for (ClueBook clue : clues) {
            sb.append("• ").append(clue.getClueContent()).append("\n");
        }
        return sb.toString();
    }
    
    @Tool("查询在指定章节回收的伏笔")
    public String getRecycledCluesByChapter(String novelId, int chapterNum) {
        log.info("[Tool] 查询章节回收的伏笔: novelId={}, chapter={}", novelId, chapterNum);
        List<ClueBook> clues = clueBookService.getRecycledCluesByChapter(novelId, chapterNum);
        if (clues.isEmpty()) {
            return "该章节未回收伏笔";
        }
        
        StringBuilder sb = new StringBuilder();
        for (ClueBook clue : clues) {
            sb.append("• ").append(clue.getClueContent()).append("\n");
        }
        return sb.toString();
    }
}
