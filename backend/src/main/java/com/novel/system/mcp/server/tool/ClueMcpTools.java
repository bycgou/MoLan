package com.novel.system.mcp.server.tool;

import com.novel.system.entity.mysql.ClueBook;
import com.novel.system.mcp.server.McpServerConfig;
import com.novel.system.service.clue.ClueBookService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class ClueMcpTools extends BaseMcpTools {

    private final ClueBookService clueBookService;

    @Autowired
    public ClueMcpTools(McpServerConfig mcpConfig, ClueBookService clueBookService) {
        super(mcpConfig);
        this.clueBookService = clueBookService;
    }

    @Override
    public void registerAll() {
        addTool("moyun_get_unrecycled_clues",
            "查询小说中所有未回收的伏笔（线索），返回伏笔内容、优先级和埋设章节",
            Map.of("novelId", Map.of("type", "string", "description", "小说ID")),
            List.of("novelId"),
            args -> getUnrecycledClues((String) args.get("novelId")));

        addTool("moyun_get_high_priority_clues",
            "查询高优先级未回收伏笔，这些伏笔必须尽快回收",
            Map.of("novelId", Map.of("type", "string", "description", "小说ID")),
            List.of("novelId"),
            args -> getHighPriorityClues((String) args.get("novelId")));

        addTool("moyun_recycle_clue",
            "回收伏笔，将指定伏笔标记为已回收",
            Map.of(
                "novelId", Map.of("type", "string", "description", "小说ID"),
                "clueId", Map.of("type", "string", "description", "伏笔ID"),
                "recycleChapter", Map.of("type", "integer", "description", "回收章节号")
            ),
            List.of("novelId", "clueId", "recycleChapter"),
            args -> recycleClue(
                (String) args.get("novelId"),
                (String) args.get("clueId"),
                ((Number) args.get("recycleChapter")).intValue()));
    }

    private String getUnrecycledClues(String novelId) {
        List<ClueBook> clues = clueBookService.getUnrecycledClues(novelId);
        if (clues.isEmpty()) return "暂无未回收的伏笔";
        StringBuilder sb = new StringBuilder();
        for (ClueBook clue : clues) {
            sb.append("• ");
            if (clue.getPriority() != null && clue.getPriority() == 1) sb.append("[高优先级] ");
            sb.append(clue.getClueContent());
            sb.append(" (埋于第").append(clue.getBuryChapter()).append("章)\n");
        }
        return sb.toString();
    }

    private String getHighPriorityClues(String novelId) {
        List<ClueBook> clues = clueBookService.getHighPriorityUnrecycledClues(novelId);
        if (clues.isEmpty()) return "暂无高优先级未回收伏笔";
        StringBuilder sb = new StringBuilder();
        for (ClueBook clue : clues) {
            sb.append("• ").append(clue.getClueContent())
              .append(" (埋于第").append(clue.getBuryChapter()).append("章)\n");
        }
        return sb.toString();
    }

    private String recycleClue(String novelId, String clueId, int recycleChapter) {
        clueBookService.recycleClue(clueId, recycleChapter);
        return "伏笔回收成功";
    }
}
