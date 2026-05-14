package com.novel.system.mcp.server.tool;

import com.novel.system.ai.chain.ChapterGenerationChain;
import com.novel.system.dto.request.GenerateChapterRequest;
import com.novel.system.dto.response.NovelResponse;
import com.novel.system.mcp.server.McpServerConfig;
import com.novel.system.service.novel.NovelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class CreationMcpTools extends BaseMcpTools {

    private final NovelService novelService;
    private final ChapterGenerationChain chapterGenerationChain;

    @Autowired
    public CreationMcpTools(McpServerConfig mcpConfig,
                            NovelService novelService,
                            ChapterGenerationChain chapterGenerationChain) {
        super(mcpConfig);
        this.novelService = novelService;
        this.chapterGenerationChain = chapterGenerationChain;
    }

    @Override
    public void registerAll() {
        addTool("moyun_generate_outline",
            "为指定小说生成章纲（大纲），需提供小说ID和章节号，可选提供用户意图",
            Map.of(
                "novelId", Map.of("type", "string", "description", "小说ID"),
                "chapterNum", Map.of("type", "integer", "description", "章节号"),
                "userIntent", Map.of("type", "string", "description", "用户意图（可选）"),
                "llmConfigId", Map.of("type", "string", "description", "模型配置ID（可选）")
            ),
            List.of("novelId", "chapterNum"),
            args -> generateOutline(
                (String) args.get("novelId"),
                ((Number) args.get("chapterNum")).intValue(),
                (String) args.get("userIntent"),
                (String) args.get("llmConfigId")));

        addTool("moyun_generate_chapter",
            "为指定小说生成章节正文，需提供小说ID和章节号，可选用户意图",
            Map.of(
                "novelId", Map.of("type", "string", "description", "小说ID"),
                "chapterNum", Map.of("type", "integer", "description", "章节号"),
                "userIntent", Map.of("type", "string", "description", "用户意图（可选）"),
                "llmConfigId", Map.of("type", "string", "description", "模型配置ID（可选）")
            ),
            List.of("novelId", "chapterNum"),
            args -> generateChapter(
                (String) args.get("novelId"),
                ((Number) args.get("chapterNum")).intValue(),
                (String) args.get("userIntent"),
                (String) args.get("llmConfigId")));

        addTool("moyun_generate_volume_outline",
            "生成卷纲（弧线大纲），为整卷小说规划章节结构",
            Map.of(
                "novelId", Map.of("type", "string", "description", "小说ID"),
                "llmConfigId", Map.of("type", "string", "description", "模型配置ID（可选）")
            ),
            List.of("novelId"),
            args -> generateVolumeOutline(
                (String) args.get("novelId"),
                (String) args.get("llmConfigId")));
    }

    private String generateOutline(String novelId, int chapterNum, String userIntent, String llmConfigId) {
        try {
            var result = novelService.generateOutline(novelId, chapterNum, userIntent, llmConfigId, "all");
            return "章纲生成成功:\n" + result.getOutline();
        } catch (Exception e) {
            return "章纲生成失败: " + e.getMessage();
        }
    }

    private String generateChapter(String novelId, int chapterNum,
                                    String userIntent, String llmConfigId) {
        try {
            GenerateChapterRequest request = new GenerateChapterRequest();
            request.setChapterNum(chapterNum);
            request.setUserIntent(userIntent);
            request.setLlmConfigId(llmConfigId);
            var result = novelService.generateChapter(novelId, request);
            return "章节生成成功，字数: " + result.getWordCount();
        } catch (Exception e) {
            return "章节生成失败: " + e.getMessage();
        }
    }

    private String generateVolumeOutline(String novelId, String llmConfigId) {
        try {
            NovelResponse novel = novelService.getNovel(novelId);
            String volumeOutlineJson = chapterGenerationChain.executeVolumeOutlineChain(
                novel.getTotalOutline(), novel.getGenre(),
                novel.getWorldSetting(), novel.getTotalOutline(),
                novel.getNovelType(), novel.getTone(), novel.getTabooList(),
                llmConfigId);
            return "卷纲生成成功:\n" + volumeOutlineJson;
        } catch (Exception e) {
            return "卷纲生成失败: " + e.getMessage();
        }
    }
}
