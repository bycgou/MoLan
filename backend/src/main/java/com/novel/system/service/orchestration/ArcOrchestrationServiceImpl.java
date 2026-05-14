package com.novel.system.service.orchestration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.system.ai.agent.ArcOrchestrationAgent;
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
    private final ObjectMapper objectMapper;

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

        // 3. 构建prompt
        String userPrompt = buildOrchestrationPrompt(novel, arc, pendingClues, charStates);

        // 4. 调用Agent
        String resultJson = executeArcOrchestrationChain(userPrompt, llmConfigId);

        // 5. 解析并存储
        ArcOrchestration orchestration = parseOrchestrationResult(resultJson, novelId, arcId);
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

    private String executeArcOrchestrationChain(String userPrompt, String llmConfigId) {
        log.info("执行卷级编排链");

        LlmConfig config;
        if (llmConfigId != null && !llmConfigId.isEmpty()) {
            config = llmConfigService.getConfigById(llmConfigId);
        } else {
            config = llmConfigService.getAnyActiveConfig("all");
        }

        if (config == null) {
            throw new RuntimeException("未找到可用的模型配置");
        }

        ChatModel model = langChainConfig.createChatModel(config, LangChainConfig.TaskType.STRUCTURED);

        ArcOrchestrationAgent agent = AiServices.builder(ArcOrchestrationAgent.class)
                .chatModel(model)
                .build();

        return agent.orchestrateArc(userPrompt);
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

    private ArcOrchestration parseOrchestrationResult(String json, String novelId, String arcId) {
        try {
            String cleaned = json.trim();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceAll("^```(?:json)?\\s*", "")
                        .replaceAll("\\s*```$", "");
            }

            ArcOrchestration result = objectMapper.readValue(cleaned, ArcOrchestration.class);
            result.setNovelId(novelId);
            result.setArcId(arcId);
            result.setCreatedAt(LocalDateTime.now().toString());

            if (result.getId() == null) {
                result.setId(novelId + "_" + arcId);
            }

            return result;
        } catch (Exception e) {
            log.error("解析编排结果失败: {}", e.getMessage());
            throw new RuntimeException("解析编排结果失败: " + e.getMessage());
        }
    }
}
