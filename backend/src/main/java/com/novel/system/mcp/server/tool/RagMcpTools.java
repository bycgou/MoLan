package com.novel.system.mcp.server.tool;

import com.novel.system.ai.rag.RagService;
import com.novel.system.entity.mongo.ChapterSummary;
import com.novel.system.mcp.server.McpServerConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class RagMcpTools extends BaseMcpTools {

    private final RagService ragService;

    @Autowired
    public RagMcpTools(McpServerConfig mcpConfig, RagService ragService) {
        super(mcpConfig);
        this.ragService = ragService;
    }

    @Override
    public void registerAll() {
        addTool("moyun_recall_history",
            "语义检索历史章节内容，用于回忆早期情节、回收伏笔、确保与前文一致",
            Map.of(
                "novelId", Map.of("type", "string", "description", "小说ID"),
                "queryText", Map.of("type", "string", "description", "检索关键词或描述")
            ),
            List.of("novelId", "queryText"),
            args -> recallHistory(
                (String) args.get("novelId"),
                (String) args.get("queryText")));

        addTool("moyun_get_global_memory",
            "获取小说的全局压缩记忆，包含已压缩的历史剧情摘要",
            Map.of("novelId", Map.of("type", "string", "description", "小说ID")),
            List.of("novelId"),
            args -> getGlobalMemory((String) args.get("novelId")));
    }

    private String recallHistory(String novelId, String queryText) {
        try {
            List<ChapterSummary> results = ragService.hybridSearch(novelId, queryText, 3);
            if (results.isEmpty()) return "未找到相关历史章节内容";
            StringBuilder sb = new StringBuilder("【相关历史章节】\n");
            for (ChapterSummary summary : results) {
                sb.append("第").append(summary.getChapterNum()).append("章: ");
                String content = summary.getContent();
                if (content != null && content.length() > 300) {
                    content = content.substring(0, 300) + "...";
                }
                sb.append(content != null ? content : "无摘要").append("\n\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "检索失败: " + e.getMessage();
        }
    }

    private String getGlobalMemory(String novelId) {
        String memory = ragService.getGlobalMemory(novelId);
        return memory != null && !memory.isEmpty() ? memory : "暂无全局记忆";
    }
}
