package com.novel.system.service.orchestration;

import com.novel.system.ai.agent.ArcOrchestrationAgent;
import com.novel.system.ai.dto.ArcOrchestrationOutput;
import com.novel.system.config.LangChainConfig;
import com.novel.system.entity.mongo.ArcOrchestration;
import com.novel.system.entity.mysql.ClueBook;
import com.novel.system.entity.mysql.LlmConfig;
import com.novel.system.entity.mysql.NovelArc;
import com.novel.system.entity.mysql.NovelBase;
import com.novel.system.mapper.NovelArcMapper;
import com.novel.system.mapper.NovelBaseMapper;
import com.novel.system.repository.mongo.ArcOrchestrationRepository;
import com.novel.system.service.clue.ClueBookService;
import com.novel.system.service.character.CharacterStateService;
import com.novel.system.service.llm.LlmConfigService;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArcOrchestrationServiceImpl implements ArcOrchestrationService {

    private final NovelBaseMapper novelBaseMapper;
    private final NovelArcMapper novelArcMapper;
    private final ClueBookService clueBookService;
    private final CharacterStateService characterStateService;
    private final ArcOrchestrationRepository orchestrationRepository;
    private final LangChainConfig langChainConfig;
    private final LlmConfigService llmConfigService;

    private static final int MAX_RETRIES = 3;

    @Override
    public ArcOrchestration orchestrate(String novelId, String arcId, String llmConfigId) {
        // 1. 幂等检查：已有编排则跳过
        ArcOrchestration existing = orchestrationRepository.findByArcId(arcId);
        if (existing != null) {
            log.info("卷编排已存在，跳过生成: arcId={}", arcId);
            return existing;
        }

        // 2. 收集上下文
        NovelBase novel = novelBaseMapper.selectById(novelId);
        NovelArc arc = novelArcMapper.selectById(arcId);
        if (novel == null || arc == null) {
            throw new RuntimeException("小说或卷信息不存在: novelId=" + novelId + ", arcId=" + arcId);
        }

        List<ClueBook> pendingClues = clueBookService.getUnrecycledClues(novelId);
        String charStates = characterStateService.formatForPrompt(novelId);

        // 3. 解析 LLM 配置
        LlmConfig config;
        if (llmConfigId != null && !llmConfigId.isEmpty()) {
            config = llmConfigService.getConfigById(llmConfigId);
        } else {
            config = llmConfigService.getAnyActiveConfig("all");
        }
        if (config == null) {
            throw new RuntimeException("未找到可用的模型配置");
        }

        // 4. 构建prompt
        String userPrompt = buildOrchestrationPrompt(novel, arc, pendingClues, charStates);

        // 5. 调用Agent（传 config 和 chapterCount，内部创建 config 副本）
        ArcOrchestrationOutput output = executeArcOrchestrationChain(
                userPrompt, config, arc.getChapterCount());

        // 6. DTO → 实体映射并存储
        ArcOrchestration orchestration = mapToEntity(output, novelId, arcId);
        orchestrationRepository.save(orchestration);

        log.info("卷编排生成完成: novelId={}, arcId={}, 章节数={}",
                novelId, arcId, orchestration.getChapters() != null ? orchestration.getChapters().size() : 0);

        return orchestration;
    }

    @Override
    public ArcOrchestration.ChapterPlan getChapterPlan(String novelId, int chapterNum) {
        NovelArc arc = novelArcMapper.findByChapterNum(novelId, chapterNum);
        if (arc == null) {
            return null;
        }

        ArcOrchestration orchestration = orchestrationRepository.findByArcId(arc.getArcId());
        if (orchestration == null || orchestration.getChapters() == null) {
            return null;
        }

        return orchestration.getChapters().stream()
                .filter(cp -> cp.getChapterNum() != null && cp.getChapterNum() == chapterNum)
                .findFirst()
                .orElse(null);
    }

    @Override
    public void invalidate(String arcId) {
        orchestrationRepository.deleteByArcId(arcId);
        log.info("卷编排已失效: arcId={}", arcId);
    }

    private ArcOrchestrationOutput executeArcOrchestrationChain(
            String userPrompt, LlmConfig config, int chapterCount) {
        log.info("执行卷级编排链, 章节数={}", chapterCount);

        // 动态 maxTokens：每章约 800 tokens（含中文 cluePlan bury/recycle 数组），乘以 1.5 安全系数，最低 8192
        int dynamicMaxTokens = Math.max(8192, (int) (chapterCount * 800 * 1.5));
        LlmConfig orchestrationConfig = copyConfigForOrchestration(config, dynamicMaxTokens);
        log.info("编排场景maxTokens={}, 章节数={}", dynamicMaxTokens, chapterCount);

        ChatModel model = langChainConfig.createChatModel(orchestrationConfig, LangChainConfig.TaskType.STRUCTURED);

        ArcOrchestrationAgent agent = AiServices.builder(ArcOrchestrationAgent.class)
                .chatModel(model)
                .build();

        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                ArcOrchestrationOutput result = agent.orchestrateArc(userPrompt);
                if (result == null) {
                    log.warn("LLM返回null, attempt={}/{}, model={}",
                            attempt, MAX_RETRIES, config.getModelName());
                    if (attempt < MAX_RETRIES) {
                        try { Thread.sleep(1000L * attempt); } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("编排重试被中断", ie);
                        }
                    }
                    continue;
                }
                return result;
            } catch (Exception e) {
                lastException = e;
                if (!isRetriableException(e)) {
                    log.error("编排调用不可重试异常, model={}, error={}",
                            config.getModelName(), e.getMessage());
                    throw new RuntimeException("卷编排失败（不可重试）: " + e.getMessage(), e);
                }
                log.warn("编排调用可重试异常, attempt={}/{}, model={}, error={}",
                        attempt, MAX_RETRIES, config.getModelName(), e.getMessage());
                if (attempt < MAX_RETRIES) {
                    try { Thread.sleep(1000L * attempt); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("编排重试被中断", ie);
                    }
                }
            }
        }
        throw new RuntimeException("卷编排失败，已重试" + MAX_RETRIES + "次", lastException);
    }

    private String buildOrchestrationPrompt(NovelBase novel, NovelArc arc,
                                            List<ClueBook> pendingClues, String charStates) {
        StringBuilder sb = new StringBuilder();

        sb.append(String.format("小说总纲：%s\n\n", novel.getTotalOutline()));
        sb.append(String.format("世界观设定：%s\n\n", novel.getWorldSetting()));
        sb.append(String.format("卷名：%s\n", arc.getArcName()));
        sb.append(String.format("卷纲：%s\n", arc.getArcOutline()));
        sb.append(String.format("章节范围：第%d章 ~ 第%d章（共%d章）\n\n",
                arc.getStartChapter(), arc.getEndChapter(), arc.getChapterCount()));

        if (!pendingClues.isEmpty()) {
            sb.append("【待回收伏笔】\n");
            for (ClueBook clue : pendingClues) {
                String deadline = clue.getDeadline() != null
                        ? String.format("（建议第%d章前回收）", clue.getDeadline()) : "";
                sb.append(String.format("- 第%d章埋：%s%s\n",
                        clue.getBuryChapter(), clue.getClueContent(), deadline));
            }
            sb.append("\n");
        }

        if (charStates != null && !charStates.isEmpty()) {
            sb.append("【人物当前状态】\n").append(charStates).append("\n\n");
        }

        if (novel.getNovelType() != null && !novel.getNovelType().isEmpty()) {
            sb.append(String.format("小说类型：%s\n", novel.getNovelType()));
        }
        if (novel.getTone() != null && !novel.getTone().isEmpty()) {
            sb.append(String.format("故事基调：%s\n", novel.getTone()));
        }
        if (novel.getTabooList() != null && !novel.getTabooList().isEmpty()) {
            sb.append(String.format("禁止雷点：%s\n", novel.getTabooList()));
        }

        sb.append("\n请为该卷所有章节做一次性编排。");

        return sb.toString();
    }

    private LlmConfig copyConfigForOrchestration(LlmConfig source, int maxTokens) {
        LlmConfig copy = new LlmConfig();
        copy.setConfigId(source.getConfigId());
        copy.setConfigName(source.getConfigName());
        copy.setApiKey(source.getApiKey());
        copy.setBaseUrl(source.getBaseUrl());
        copy.setModelName(source.getModelName());
        copy.setModelType(source.getModelType());
        copy.setTemperature(source.getTemperature());
        copy.setMaxTokens(maxTokens);
        return copy;
    }

    private boolean isRetriableException(Exception e) {
        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        if (msg.contains("connection") || msg.contains("timeout") || msg.contains("reset")) return true;
        if (msg.contains("500") || msg.contains("502") || msg.contains("503")) return true;
        if (msg.contains("json") || msg.contains("deserialize") || msg.contains("schema")) return false;
        return true;
    }

    private ArcOrchestration mapToEntity(ArcOrchestrationOutput output, String novelId, String arcId) {
        ArcOrchestration entity = new ArcOrchestration();
        entity.setId(novelId + "_" + arcId);
        entity.setNovelId(novelId);
        entity.setArcId(arcId);
        entity.setArcName(output.getArcName());
        entity.setOverallRhythm(output.getOverallRhythm());
        entity.setClimaxChapters(output.getClimaxChapters());
        entity.setCreatedAt(LocalDateTime.now().toString());

        if (output.getChapters() != null) {
            entity.setChapters(output.getChapters().stream().map(ch -> {
                ArcOrchestration.ChapterPlan plan = new ArcOrchestration.ChapterPlan();
                plan.setChapterNum(ch.getChapterNum());
                plan.setRhythmTag(ch.getRhythmTag());
                plan.setRhythmIntensity(ch.getRhythmIntensity());
                plan.setPerspective(ch.getPerspective());
                plan.setNarrativeMode(ch.getNarrativeMode());
                if (ch.getCluePlan() != null) {
                    ArcOrchestration.CluePlan cluePlan = new ArcOrchestration.CluePlan();
                    cluePlan.setBury(ch.getCluePlan().getBury());
                    cluePlan.setRecycle(ch.getCluePlan().getRecycle());
                    plan.setCluePlan(cluePlan);
                }
                plan.setConflictType(ch.getConflictType());
                plan.setOutcomeType(ch.getOutcomeType());
                plan.setKeyEvent(ch.getKeyEvent());
                plan.setHookDirection(ch.getHookDirection());
                return plan;
            }).collect(Collectors.toList()));
        }
        return entity;
    }
}
