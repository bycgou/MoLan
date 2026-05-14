package com.novel.system.mcp.server.tool;

import com.novel.system.dto.response.ChapterResponse;
import com.novel.system.mcp.server.McpServerConfig;
import com.novel.system.service.chapter.ChapterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class ChapterMcpTools extends BaseMcpTools {

    private final ChapterService chapterService;

    @Autowired
    public ChapterMcpTools(McpServerConfig mcpConfig, ChapterService chapterService) {
        super(mcpConfig);
        this.chapterService = chapterService;
    }

    @Override
    public void registerAll() {
        addTool("moyun_list_chapters",
            "获取小说的章节列表，包含章节号、标题、字数",
            Map.of("novelId", Map.of("type", "string", "description", "小说ID")),
            List.of("novelId"),
            args -> listChapters((String) args.get("novelId")));

        addTool("moyun_get_chapter",
            "获取指定章节的完整内容，包括标题、正文、章纲、摘要",
            Map.of(
                "novelId", Map.of("type", "string", "description", "小说ID"),
                "chapterNum", Map.of("type", "integer", "description", "章节号")
            ),
            List.of("novelId", "chapterNum"),
            args -> getChapterContent(
                (String) args.get("novelId"),
                ((Number) args.get("chapterNum")).intValue()));

        addTool("moyun_get_recent_chapters",
            "获取最近N章的摘要信息",
            Map.of(
                "novelId", Map.of("type", "string", "description", "小说ID"),
                "count", Map.of("type", "integer", "description", "获取章节数量，默认10")
            ),
            List.of("novelId"),
            args -> getRecentChapters(
                (String) args.get("novelId"),
                args.containsKey("count") ? ((Number) args.get("count")).intValue() : 10));
    }

    private String listChapters(String novelId) {
        var chapters = chapterService.getChapters(novelId);
        if (chapters.isEmpty()) return "该小说暂无章节";
        StringBuilder sb = new StringBuilder();
        for (ChapterResponse ch : chapters) {
            sb.append("第").append(ch.getChapterNum()).append("章: ")
              .append(ch.getChapterTitle())
              .append(" (").append(ch.getWordCount()).append("字)\n");
        }
        return sb.toString();
    }

    private String getChapterContent(String novelId, int chapterNum) {
        ChapterResponse ch = chapterService.getChapter(novelId, chapterNum);
        if (ch == null) return "未找到该章节";
        StringBuilder sb = new StringBuilder();
        sb.append("第").append(ch.getChapterNum()).append("章: ")
          .append(ch.getChapterTitle()).append("\n\n");
        if (ch.getContent() != null) sb.append(ch.getContent());
        return sb.toString();
    }

    private String getRecentChapters(String novelId, int count) {
        var chapters = chapterService.getRecentChapters(novelId, count);
        StringBuilder sb = new StringBuilder();
        for (ChapterResponse ch : chapters) {
            sb.append("第").append(ch.getChapterNum()).append("章: ")
              .append(ch.getChapterTitle()).append("\n");
        }
        return sb.toString();
    }
}
