package com.novel.system.ai.graph;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.system.ai.chain.ChapterGenerationChain;
import com.novel.system.entity.mysql.NovelArc;
import com.novel.system.entity.mysql.NovelBase;
import com.novel.system.entity.mysql.NovelChapter;
import com.novel.system.mapper.NovelArcMapper;
import com.novel.system.mapper.NovelBaseMapper;
import com.novel.system.mapper.NovelChapterMapper;
import com.novel.system.service.chapter.ContextBuilderService;
import com.novel.system.util.JsonExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.springframework.util.StringUtils.hasText;

/**
 * 章纲生成节点 — 生成章纲 + 构建六层记忆上下文（存入 state）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutlineNode implements NovelGraphNode {

    private final ChapterGenerationChain chain;
    private final ContextBuilderService contextBuilderService;
    private final NovelBaseMapper novelBaseMapper;
    private final NovelArcMapper novelArcMapper;
    private final NovelChapterMapper chapterMapper;
    private final ObjectMapper objectMapper;

    @Override
    public String nodeName() { return "outline"; }

    @Override
    public NovelAgentState resetState(NovelAgentState state, String source) {
        return state.toBuilder()
                .chapterPlan(null)
                .preWriteValidation(null)
                .selectedIdea(null)
                .implementationIdeas(null)
                .build();
    }

    @Override
    public ErrorStrategy errorStrategy() { return ErrorStrategy.RETRY; }

    @Override
    public int maxRetries() { return 2; }

    @Override
    public boolean shouldSkip(NovelAgentState state) {
        return state.getChapterPlan() != null;
    }

    @Override
    public NovelAgentState apply(NovelAgentState state) throws Exception {
        log.info("[{}] 生成章纲, novelId={}, chapter={}",
                nodeName(), state.getNovelId(), state.getChapterNum());

        NovelBase novel = novelBaseMapper.selectById(state.getNovelId());
        NovelArc arc = novelArcMapper.findByChapterNum(state.getNovelId(), state.getChapterNum());

        // 优先使用已确认的章纲
        NovelChapter existing = chapterMapper.selectByNovelIdAndChapterNum(
                state.getNovelId(), state.getChapterNum());
        if (existing != null && hasText(existing.getChapterOutline())) {
            log.info("使用已确认章纲: novelId={}, chapter={}", state.getNovelId(), state.getChapterNum());
            NovelAgentState.ChapterPlan plan = parseConfirmedOutline(existing.getChapterOutline());
            return state.toBuilder()
                    .chapterPlan(plan)
                    .currentPhase(NovelAgentState.GenerationPhase.OUTLINE.name())
                    .build();
        }

        String arcName = arc != null ? arc.getArcName() : null;
        String arcOutline = arc != null ? arc.getArcOutline() : null;

        // 构建上下文（只构建一次，存入 state）
        String outlineContext = contextBuilderService.buildOutlineContext(
                state.getNovelId(), state.getChapterNum(), novel, arcName, arcOutline);

        // 调用 OutlineAgent
        String outlineJson = chain.executeOutlineChain(
                state.getNovelId(), state.getChapterNum(),
                outlineContext, state.getLlmConfigId());

        NovelAgentState.ChapterPlan plan = parseOutline(outlineJson);

        NovelAgentState updated = state.toBuilder()
                .outlineContext(outlineContext)
                .chapterPlan(plan)
                .currentPhase(NovelAgentState.GenerationPhase.OUTLINE.name())
                .build();

        return updated;
    }

    private NovelAgentState.ChapterPlan parseConfirmedOutline(String outlineText) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            return mapper.readValue(outlineText, NovelAgentState.ChapterPlan.class);
        } catch (Exception e) {
            log.debug("章纲非 JSON 格式，按纯文本处理: {}", e.getMessage());
        }
        return NovelAgentState.ChapterPlan.builder()
                .title("")
                .summary(outlineText)
                .hook("")
                .keyPoints(Collections.emptyList())
                .implementationIdeas(Collections.emptyList())
                .build();
    }

    private NovelAgentState.ChapterPlan parseOutline(String json) {
        try {
            String cleaned = JsonExtractor.extractJson(json);
            JsonNode node = objectMapper.readTree(cleaned);

            NovelAgentState.ChapterPlan.ChapterPlanBuilder builder = NovelAgentState.ChapterPlan.builder();
            if (node.has("title")) builder.title(node.get("title").asText());
            if (node.has("summary")) builder.summary(node.get("summary").asText());
            if (node.has("hook")) builder.hook(node.get("hook").asText());
            if (node.has("key_points") && node.get("key_points").isArray()) {
                List<String> points = new ArrayList<>();
                node.get("key_points").forEach(p -> points.add(p.asText()));
                builder.keyPoints(points);
            }
            if (node.has("ideas") && node.get("ideas").isArray()) {
                List<String> ideas = new ArrayList<>();
                node.get("ideas").forEach(i -> ideas.add(i.asText()));
                builder.implementationIdeas(ideas);
            }
            return builder.build();
        } catch (Exception e) {
            log.error("解析章纲失败: {}", e.getMessage());
            String fallback = json != null ? json.substring(0, Math.min(200, json.length())) : "章纲生成异常";
            return NovelAgentState.ChapterPlan.builder()
                    .summary(fallback)
                    .build();
        }
    }
}
