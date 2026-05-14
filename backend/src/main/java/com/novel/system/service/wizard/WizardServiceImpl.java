package com.novel.system.service.wizard;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.system.ai.chain.ChapterGenerationChain;
import com.novel.system.dto.request.WizardSaveRequest;
import com.novel.system.dto.request.WizardSettingsRequest;
import com.novel.system.dto.response.*;
import com.novel.system.entity.mysql.NovelArc;
import com.novel.system.entity.mysql.NovelBase;
import com.novel.system.entity.mysql.NovelChapter;
import com.novel.system.mapper.NovelArcMapper;
import com.novel.system.mapper.NovelBaseMapper;
import com.novel.system.mapper.NovelChapterMapper;
import com.novel.system.service.chapter.ChapterGenerationRequest;
import com.novel.system.service.chapter.ChapterGenerationResponse;
import com.novel.system.service.chapter.ChapterGenerationService;
import com.novel.system.service.orchestration.ArcOrchestrationService;
import com.novel.system.service.style.StyleCardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * 向导服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WizardServiceImpl implements WizardService {

    private final ChapterGenerationChain chapterGenerationChain;
    private final NovelBaseMapper novelBaseMapper;
    private final NovelArcMapper novelArcMapper;
    private final NovelChapterMapper novelChapterMapper;
    private final ChapterGenerationService chapterGenerationService;
    private final StyleCardService styleCardService;
    private final ArcOrchestrationService arcOrchestrationService;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    @Qualifier("ioTaskExecutor")
    private final Executor ioTaskExecutor;

    private static final String BATCH_PROGRESS_PREFIX = "novel:wizard:batch:";

    @Override
    public List<ArcOutlineDTO> generateVolumeOutlines(WizardSettingsRequest request) {
        log.info("生成卷纲，小说: {}", request.getNovelName());

        String json = chapterGenerationChain.executeVolumeOutlineChain(
                request.getTotalOutline(),
                request.getGenre(),
                request.getWorldSetting(),
                request.getPrompt(),
                request.getNovelType(),
                request.getTone(),
                request.getTabooList(),
                request.getLlmConfigId()
        );

        return parseVolumeOutlineJson(json);
    }

    @Override
    public NovelResponse saveNovelWithArcs(WizardSaveRequest request, String userId) {
        log.info("保存小说及卷纲，小说: {}", request.getNovelName());

        // 创建 NovelBase
        NovelBase novel = new NovelBase();
        novel.setNovelId(UUID.randomUUID().toString().replace("-", ""));
        novel.setNovelName(request.getNovelName());
        novel.setUserId(userId);
        novel.setGenre(request.getGenre() != null ? request.getGenre() : "玄幻");
        novel.setNovelType(request.getNovelType());
        novel.setTone(request.getTone());
        novel.setTabooList(request.getTabooList());
        novel.setTotalOutline(request.getTotalOutline());
        novel.setWorldSetting(request.getWorldSetting());
        novel.setLlmConfigId(request.getLlmConfigId());
        novel.setStatus(0);
        novel.setCreateTime(LocalDateTime.now());
        novel.setUpdateTime(LocalDateTime.now());
        novelBaseMapper.insert(novel);

        // 删除已有卷纲（如果有）
        novelArcMapper.deleteByNovelId(novel.getNovelId());

        // 批量插入卷纲
        if (request.getArcs() != null && !request.getArcs().isEmpty()) {
            List<NovelArc> arcs = new ArrayList<>();
            for (ArcOutlineDTO dto : request.getArcs()) {
                NovelArc arc = new NovelArc();
                arc.setArcId(UUID.randomUUID().toString().replace("-", ""));
                arc.setNovelId(novel.getNovelId());
                arc.setArcName(dto.getArcName());
                arc.setArcOutline(dto.getArcOutline());
                arc.setStartChapter(dto.getStartChapter());
                arc.setEndChapter(dto.getEndChapter());
                arc.setChapterCount(dto.getChapterCount());
                arc.setRhythmTag(dto.getRhythmTag() != null ? dto.getRhythmTag() : "quest");
                arc.setRhythmIntensity(dto.getRhythmIntensity() != null ? dto.getRhythmIntensity() : 5);
                arcs.add(arc);
            }
            novelArcMapper.insertBatch(arcs);

            // 为每卷生成编排计划（异步，失败不影响小说创建）
            for (NovelArc arc : arcs) {
                try {
                    arcOrchestrationService.orchestrate(novel.getNovelId(), arc.getArcId(), request.getLlmConfigId());
                    log.info("卷编排生成成功: arcId={}", arc.getArcId());
                } catch (Exception e) {
                    log.warn("卷编排生成失败（不影响小说创建）: arcId={}, error={}", arc.getArcId(), e.getMessage());
                }
            }
        }

        // 绑定文风卡片
        if (request.getStyleCardId() != null && !request.getStyleCardId().isEmpty()) {
            try {
                styleCardService.bindNovelToCard(novel.getNovelId(), request.getStyleCardId());
                log.info("文风卡片绑定成功: novelId={}, cardId={}", novel.getNovelId(), request.getStyleCardId());
            } catch (Exception e) {
                log.warn("文风卡片绑定失败（不影响小说创建）: {}", e.getMessage());
            }
        }

        NovelResponse response = new NovelResponse();
        response.setNovelId(novel.getNovelId());
        response.setNovelName(novel.getNovelName());
        response.setGenre(novel.getGenre());
        response.setTotalOutline(novel.getTotalOutline());
        response.setWorldSetting(novel.getWorldSetting());
        response.setStatus(novel.getStatus());
        response.setCreateTime(novel.getCreateTime());
        response.setUpdateTime(novel.getUpdateTime());
        return response;
    }

    @Override
    public List<ArcChapterOutlineDTO> generateArcChapterOutlines(String novelId, int arcIndex) {
        log.info("生成卷章纲，小说ID: {}, 卷索引: {}", novelId, arcIndex);

        List<NovelArc> arcs = novelArcMapper.selectByNovelId(novelId);
        NovelBase novel = novelBaseMapper.selectById(novelId);
        String llmConfigId = novel != null ? novel.getLlmConfigId() : null;
        if (arcIndex < 0 || arcIndex >= arcs.size()) {
            throw new RuntimeException("卷索引无效: " + arcIndex);
        }

        NovelArc arc = arcs.get(arcIndex);
        int startChapter = arc.getStartChapter() != null ? arc.getStartChapter() : 1;
        int endChapter = arc.getEndChapter() != null ? arc.getEndChapter() : startChapter + 9;

        List<ArcChapterOutlineDTO> results = new ArrayList<>();
        for (int ch = startChapter; ch <= endChapter; ch++) {
            log.info("生成第{}卷第{}章章纲", arc.getArcName(), ch);

            ChapterGenerationRequest genRequest = new ChapterGenerationRequest();
            genRequest.setNovelId(novelId);
            genRequest.setChapterNum(ch);
            genRequest.setLlmConfigId(llmConfigId);

            try {
                ChapterGenerationResponse response = chapterGenerationService.generateOutline(genRequest);

                if (response.getSuccess()) {
                    ArcChapterOutlineDTO dto = new ArcChapterOutlineDTO();
                    dto.setChapterNum(ch);
                    dto.setChapterTitle("第" + ch + "章");
                    dto.setOutline(response.getChapterOutline());
                    dto.setImplementationIdeas(response.getImplementationIdeas());
                    results.add(dto);
                } else {
                    log.warn("第{}章章纲生成失败: {}", ch, response.getErrorMessage());
                    ArcChapterOutlineDTO dto = new ArcChapterOutlineDTO();
                    dto.setChapterNum(ch);
                    dto.setChapterTitle("第" + ch + "章");
                    dto.setOutline("章纲生成失败: " + response.getErrorMessage());
                    results.add(dto);
                }
            } catch (Exception e) {
                log.error("第{}章章纲生成异常", ch, e);
                ArcChapterOutlineDTO dto = new ArcChapterOutlineDTO();
                dto.setChapterNum(ch);
                dto.setChapterTitle("第" + ch + "章");
                dto.setOutline("章纲生成异常: " + e.getMessage());
                results.add(dto);
            }
        }

        return results;
    }

    @Override
    public void confirmChapterOutlines(String novelId, int arcIndex, List<ArcChapterOutlineDTO> outlines) {
        log.info("确认章纲，小说ID: {}, 卷索引: {}, 章节数: {}", novelId, arcIndex, outlines.size());

        // Query arcs once before the loop
        List<NovelArc> arcs = novelArcMapper.selectByNovelId(novelId);
        String arcId = (arcIndex >= 0 && arcIndex < arcs.size())
                ? arcs.get(arcIndex).getArcId() : null;

        for (ArcChapterOutlineDTO dto : outlines) {
            NovelChapter existing = novelChapterMapper.selectByNovelIdAndChapterNum(novelId, dto.getChapterNum());

            if (existing != null) {
                existing.setChapterOutline(dto.getOutline());
                existing.setChapterTitle(dto.getChapterTitle());
                if (existing.getArcId() == null && arcId != null) {
                    existing.setArcId(arcId);
                }
                existing.setUpdateTime(LocalDateTime.now());
                novelChapterMapper.updateById(existing);
            } else {
                NovelChapter chapter = new NovelChapter();
                chapter.setChapterId(UUID.randomUUID().toString().replace("-", ""));
                chapter.setNovelId(novelId);
                chapter.setChapterNum(dto.getChapterNum());
                chapter.setChapterTitle(dto.getChapterTitle());
                chapter.setChapterOutline(dto.getOutline());
                chapter.setArcId(arcId);
                chapter.setGenerationStatus(0);
                chapter.setCreateTime(LocalDateTime.now());
                chapter.setUpdateTime(LocalDateTime.now());
                novelChapterMapper.insert(chapter);
            }
        }

        // Update novel chapter count
        NovelBase novel = novelBaseMapper.selectById(novelId);
        if (novel != null) {
            int maxChapter = outlines.stream()
                    .mapToInt(ArcChapterOutlineDTO::getChapterNum)
                    .max().orElse(0);
            novel.setChapterCount(Math.max(novel.getChapterCount() != null ? novel.getChapterCount() : 0, maxChapter));
            novel.setUpdateTime(LocalDateTime.now());
            novelBaseMapper.updateById(novel);
        }
    }

    @Override
    public String batchGenerateChapters(String novelId) {
        NovelBase novel = novelBaseMapper.selectById(novelId);
        String llmConfigId = novel != null ? novel.getLlmConfigId() : null;
        String styleCardId = styleCardService.getBoundCardId(novelId);
        String taskId = UUID.randomUUID().toString();
        log.info("启动批量章节生成任务，小说ID: {}, 任务ID: {}", novelId, taskId);

        BatchGenerationProgress progress = new BatchGenerationProgress();
        progress.setTaskId(taskId);
        progress.setStatus("GENERATING");
        progress.setCompletedChapters(0);
        progress.setCurrentPhase("writing");
        saveBatchProgress(taskId, progress);

        SecurityContext securityContext = SecurityContextHolder.getContext();
        CompletableFuture.runAsync(() -> {
            SecurityContextHolder.setContext(securityContext);
            try {
                // 获取所有有待生成章纲的章节
                List<NovelChapter> chapters = novelChapterMapper.selectByNovelId(novelId);
                // 过滤出有章纲但无正文的章节
                List<NovelChapter> pendingChapters = chapters.stream()
                        .filter(ch -> ch.getChapterOutline() != null && !ch.getChapterOutline().isEmpty()
                                && (ch.getContent() == null || ch.getContent().isEmpty()))
                        .sorted(Comparator.comparingInt(NovelChapter::getChapterNum))
                        .toList();

                progress.setTotalChapters(pendingChapters.size());
                saveBatchProgress(taskId, progress);

                for (NovelChapter chapter : pendingChapters) {
                    progress.setCurrentChapterNum(chapter.getChapterNum());
                    saveBatchProgress(taskId, progress);

                    log.info("批量生成: 第{}章 ({}/{})",
                            chapter.getChapterNum(), progress.getCompletedChapters() + 1, progress.getTotalChapters());

                    try {
                        ChapterGenerationRequest genRequest = new ChapterGenerationRequest();
                        genRequest.setNovelId(novelId);
                        genRequest.setChapterNum(chapter.getChapterNum());
                        genRequest.setLlmConfigId(llmConfigId);
                        genRequest.setStyleCardId(styleCardId);

                        ChapterGenerationResponse response = chapterGenerationService.generateChapter(genRequest);

                        if (response.getSuccess()) {
                            progress.setCompletedChapters(progress.getCompletedChapters() + 1);
                        } else {
                            progress.setFailedChapters(progress.getFailedChapters() + 1);
                            progress.setErrorMessage("第" + chapter.getChapterNum() + "章生成失败: " + response.getErrorMessage());
                            log.warn("第{}章生成失败: {}", chapter.getChapterNum(), response.getErrorMessage());
                        }
                    } catch (Exception e) {
                        progress.setFailedChapters(progress.getFailedChapters() + 1);
                        progress.setErrorMessage("第" + chapter.getChapterNum() + "章生成异常: " + e.getMessage());
                        log.error("批量生成章节 {} 异常", chapter.getChapterNum(), e);
                    }

                    saveBatchProgress(taskId, progress);
                }

                progress.setStatus("COMPLETED");
                saveBatchProgress(taskId, progress);
                log.info("批量章节生成完成，小说ID: {}, 任务ID: {}", novelId, taskId);

            } catch (Exception e) {
                log.error("批量章节生成失败，小说ID: {}, 任务ID: {}", novelId, taskId, e);
                progress.setStatus("FAILED");
                progress.setErrorMessage("批量生成失败: " + e.getMessage());
                saveBatchProgress(taskId, progress);
            } finally {
                SecurityContextHolder.clearContext();
            }
        }, ioTaskExecutor);

        return taskId;
    }

    @Override
    public BatchGenerationProgress getBatchProgress(String novelId, String taskId) {
        String key = BATCH_PROGRESS_PREFIX + taskId;
        String json = redisTemplate.opsForValue().get(key);
        if (json != null) {
            try {
                return objectMapper.readValue(json, BatchGenerationProgress.class);
            } catch (JsonProcessingException e) {
                log.error("解析批量进度失败", e);
            }
        }
        BatchGenerationProgress empty = new BatchGenerationProgress();
        empty.setTaskId(taskId);
        empty.setStatus("UNKNOWN");
        empty.setErrorMessage("任务不存在或已过期");
        return empty;
    }

    // ========== 辅助方法 ==========

    private void saveBatchProgress(String taskId, BatchGenerationProgress progress) {
        try {
            String key = BATCH_PROGRESS_PREFIX + taskId;
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(progress), 24, TimeUnit.HOURS);
        } catch (Exception e) {
            log.error("保存批量进度失败", e);
        }
    }

    private List<ArcOutlineDTO> parseVolumeOutlineJson(String json) {
        List<ArcOutlineDTO> results = new ArrayList<>();
        try {
            String cleaned = cleanMarkdownJson(json);
            JsonNode arrayNode = objectMapper.readTree(cleaned);

            if (arrayNode.isArray()) {
                for (JsonNode node : arrayNode) {
                    ArcOutlineDTO dto = new ArcOutlineDTO();
                    dto.setArcName(node.has("arcName") ? node.get("arcName").asText() : "未命名卷");
                    dto.setArcOutline(node.has("arcOutline") ? node.get("arcOutline").asText() : "");
                    dto.setStartChapter(node.has("startChapter") ? node.get("startChapter").asInt(1) : 1);
                    dto.setEndChapter(node.has("endChapter") ? node.get("endChapter").asInt(10) : 10);
                    dto.setChapterCount(node.has("chapterCount") ? node.get("chapterCount").asInt(10) : 10);
                    dto.setRhythmTag(node.has("rhythmTag") ? node.get("rhythmTag").asText("quest") : "quest");
                    dto.setRhythmIntensity(node.has("rhythmIntensity") ? node.get("rhythmIntensity").asInt(5) : 5);
                    results.add(dto);
                }
            }
        } catch (Exception e) {
            log.warn("解析卷纲JSON失败: {}", e.getMessage());
            // fallback: 如果解析失败，返回一个默认卷
            ArcOutlineDTO dto = new ArcOutlineDTO();
            dto.setArcName("第一卷");
            dto.setArcOutline(json.substring(0, Math.min(json.length(), 500)));
            dto.setStartChapter(1);
            dto.setEndChapter(20);
            dto.setChapterCount(20);
            dto.setRhythmTag("quest");
            dto.setRhythmIntensity(5);
            results.add(dto);
        }
        return results;
    }

    private String cleanMarkdownJson(String text) {
        String s = text.trim();
        if (s.startsWith("```json")) s = s.substring(7);
        else if (s.startsWith("```")) s = s.substring(3);
        if (s.endsWith("```")) s = s.substring(0, s.length() - 3);
        return s.trim();
    }
}
