package com.novel.system.mcp.server.tool;

import com.novel.system.ai.chain.ValidationRequest;
import com.novel.system.ai.chain.ValidationService;
import com.novel.system.mcp.server.McpServerConfig;
import com.novel.system.service.novel.RhythmService;
import com.novel.system.service.novel.StrandRhythmService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class AnalysisMcpTools extends BaseMcpTools {

    private final RhythmService rhythmService;
    private final StrandRhythmService strandRhythmService;
    private final ValidationService validationService;

    @Autowired
    public AnalysisMcpTools(McpServerConfig mcpConfig,
                            RhythmService rhythmService,
                            StrandRhythmService strandRhythmService,
                            ValidationService validationService) {
        super(mcpConfig);
        this.rhythmService = rhythmService;
        this.strandRhythmService = strandRhythmService;
        this.validationService = validationService;
    }

    @Override
    public void registerAll() {
        addTool("moyun_analyze_read_pull",
            "分析章节追读力，评估章节对读者的吸引力",
            Map.of(
                "novelId", Map.of("type", "string", "description", "小说ID"),
                "chapterNum", Map.of("type", "integer", "description", "章节号"),
                "chapterContent", Map.of("type", "string", "description", "章节内容")
            ),
            List.of("novelId", "chapterNum", "chapterContent"),
            args -> analyzeReadPull(
                (String) args.get("novelId"),
                ((Number) args.get("chapterNum")).intValue(),
                (String) args.get("chapterContent")));

        addTool("moyun_get_rhythm_status",
            "获取小说的节奏状态，包括各类型Strand的分布情况",
            Map.of("novelId", Map.of("type", "string", "description", "小说ID")),
            List.of("novelId"),
            args -> getRhythmStatus((String) args.get("novelId")));

        addTool("moyun_recommend_next_strand",
            "获取推荐的下一个Strand类型（quest/fire/constellation）",
            Map.of("novelId", Map.of("type", "string", "description", "小说ID")),
            List.of("novelId"),
            args -> recommendNextStrand((String) args.get("novelId")));

        addTool("moyun_validate_chapter",
            "校验章节内容，检测OOC、逻辑冲突、设定矛盾等问题",
            Map.of(
                "novelId", Map.of("type", "string", "description", "小说ID"),
                "chapterNum", Map.of("type", "integer", "description", "章节号"),
                "chapterContent", Map.of("type", "string", "description", "章节内容"),
                "chapterOutline", Map.of("type", "string", "description", "章纲"),
                "totalOutline", Map.of("type", "string", "description", "总纲"),
                "worldSetting", Map.of("type", "string", "description", "世界观设定")
            ),
            List.of("novelId", "chapterNum", "chapterContent"),
            args -> validateChapter(
                (String) args.get("novelId"),
                ((Number) args.get("chapterNum")).intValue(),
                (String) args.get("chapterContent"),
                (String) args.get("chapterOutline"),
                (String) args.get("totalOutline"),
                (String) args.get("worldSetting")));
    }

    private String analyzeReadPull(String novelId, int chapterNum, String chapterContent) {
        var analysis = rhythmService.analyzeReadPull(chapterContent, novelId, chapterNum);
        return "追读力评分: " + analysis.getTotalScore() + "/100\n" +
               "等级: " + analysis.getReadPullLevel() + "\n" +
               "建议: " + analysis.getSuggestion();
    }

    private String getRhythmStatus(String novelId) {
        var status = strandRhythmService.getRhythmStatus(novelId);
        return "当前节奏状态: " + status.toString();
    }

    private String recommendNextStrand(String novelId) {
        var recommended = strandRhythmService.recommendNextStrand(novelId);
        return "推荐Strand类型: " + recommended.name().toLowerCase();
    }

    private String validateChapter(String novelId, int chapterNum, String chapterContent,
                                    String chapterOutline, String totalOutline, String worldSetting) {
        ValidationRequest request = new ValidationRequest();
        request.setNovelId(novelId);
        request.setChapterNum(chapterNum);
        request.setChapterContent(chapterContent);
        request.setChapterOutline(chapterOutline);
        request.setTotalOutline(totalOutline);
        request.setWorldSetting(worldSetting);
        var result = validationService.validateAll(request);
        return "校验结果: " + result.getSummary();
    }
}
