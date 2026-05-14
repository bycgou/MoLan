package com.novel.system.mcp.server.tool;

import com.novel.system.dto.request.CreateNovelRequest;
import com.novel.system.dto.request.UpdateNovelRequest;
import com.novel.system.dto.response.NovelResponse;
import com.novel.system.mcp.server.McpServerConfig;
import com.novel.system.service.novel.NovelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class NovelMcpTools extends BaseMcpTools {

    private final NovelService novelService;

    @Autowired
    public NovelMcpTools(McpServerConfig mcpConfig, NovelService novelService) {
        super(mcpConfig);
        this.novelService = novelService;
    }

    @Override
    public void registerAll() {
        addTool("moyun_list_novels",
            "获取用户的所有小说列表，返回小说ID、名称、题材",
            Map.of(), List.of(),
            args -> listNovels());

        addTool("moyun_get_novel_info",
            "查询小说基本信息，包括名称、题材、总纲、世界观设定、字数、章节数",
            Map.of("novelId", Map.of("type", "string", "description", "小说ID")),
            List.of("novelId"),
            args -> getNovelInfo((String) args.get("novelId")));

        addTool("moyun_create_novel",
            "创建一部新小说，需提供小说名称、题材、核心创意等基本信息",
            Map.of(
                "novelName", Map.of("type", "string", "description", "小说名称"),
                "genre", Map.of("type", "string", "description", "题材"),
                "totalOutline", Map.of("type", "string", "description", "总纲/核心创意"),
                "worldSetting", Map.of("type", "string", "description", "世界观设定")
            ),
            List.of("novelName", "genre"),
            args -> createNovel(
                (String) args.get("novelName"),
                (String) args.get("genre"),
                (String) args.get("totalOutline"),
                (String) args.get("worldSetting")));

        addTool("moyun_update_novel",
            "更新小说信息，如总纲、世界观设定等",
            Map.of(
                "novelId", Map.of("type", "string", "description", "小说ID"),
                "totalOutline", Map.of("type", "string", "description", "新总纲"),
                "worldSetting", Map.of("type", "string", "description", "新世界观设定"),
                "novelName", Map.of("type", "string", "description", "新名称")
            ),
            List.of("novelId"),
            args -> updateNovel(
                (String) args.get("novelId"),
                (String) args.get("totalOutline"),
                (String) args.get("worldSetting"),
                (String) args.get("novelName")));

        addTool("moyun_export_novel",
            "导出小说为纯文本格式",
            Map.of("novelId", Map.of("type", "string", "description", "小说ID")),
            List.of("novelId"),
            args -> exportNovel((String) args.get("novelId")));
    }

    private String listNovels() {
        var novels = novelService.getNovelList(resolveUserId());
        if (novels.isEmpty()) return "暂无小说";
        StringBuilder sb = new StringBuilder();
        for (NovelResponse n : novels) {
            sb.append("- [").append(n.getNovelId()).append("] ")
              .append(n.getNovelName()).append(" (").append(n.getGenre()).append(")\n");
        }
        return sb.toString();
    }

    private String getNovelInfo(String novelId) {
        NovelResponse novel = novelService.getNovel(novelId);
        if (novel == null) return "未找到该小说";
        StringBuilder sb = new StringBuilder();
        sb.append("小说名: ").append(novel.getNovelName()).append("\n");
        sb.append("题材: ").append(novel.getGenre()).append("\n");
        sb.append("字数: ").append(novel.getWordCount()).append("\n");
        sb.append("章节数: ").append(novel.getChapterCount()).append("\n");
        if (novel.getTotalOutline() != null) {
            sb.append("总纲: ").append(novel.getTotalOutline()).append("\n");
        }
        return sb.toString();
    }

    private String createNovel(String novelName, String genre,
                                String totalOutline, String worldSetting) {
        CreateNovelRequest request = new CreateNovelRequest();
        request.setNovelName(novelName);
        request.setGenre(genre);
        request.setTotalOutline(totalOutline);
        request.setWorldSetting(worldSetting);
        NovelResponse response = novelService.createNovel(request, resolveUserId());
        return "小说创建成功，ID: " + response.getNovelId() + "，名称: " + response.getNovelName();
    }

    private String updateNovel(String novelId, String totalOutline,
                                String worldSetting, String novelName) {
        UpdateNovelRequest request = new UpdateNovelRequest();
        request.setTotalOutline(totalOutline);
        request.setWorldSetting(worldSetting);
        request.setNovelName(novelName);
        novelService.updateNovel(novelId, request);
        return "小说更新成功";
    }

    private String exportNovel(String novelId) {
        var response = novelService.exportNovelTxt(novelId);
        return "导出成功，文件大小: " + response.getBody().length + " 字节";
    }
}
