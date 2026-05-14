package com.novel.system.service.chapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.system.entity.mysql.*;
import com.novel.system.mapper.ArcSummaryMapper;
import com.novel.system.mapper.NovelChapterMapper;
import com.novel.system.ai.memory.CharacterGraphService;
import com.novel.system.service.character.CharacterStateService;
import com.novel.system.service.clue.ClueBookService;
import com.novel.system.service.story.StoryStateService;
import com.novel.system.entity.mongo.ArcOrchestration;
import com.novel.system.entity.mongo.StoryState;
import com.novel.system.service.orchestration.ArcOrchestrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 上下文构建服务
 * 实现三层记忆体系，构建章纲和正文生成的上下文
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContextBuilderService {

    private final ArcSummaryMapper arcSummaryMapper;
    private final NovelChapterMapper chapterMapper;
    private final CharacterStateService characterStateService;
    private final ClueBookService clueBookService;
    private final StoryStateService storyStateService;
    private final TokenBudgetService tokenBudgetService;
    private final CharacterGraphService characterGraphService;
    private final ArcOrchestrationService arcOrchestrationService;
    private final com.novel.system.service.event.RuleDrivenRetrievalService ruleDrivenRetrievalService;
    private final com.novel.system.service.event.EventGraphService eventGraphService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 构建章纲生成的上下文 — 三层记忆体系（默认8192 token预算）
     */
    public String buildOutlineContext(String novelId, int chapterNum,
                                       NovelBase novel, String arcName, String arcOutline) {
        return buildOutlineContext(novelId, chapterNum, novel, arcName, arcOutline, 8192);
    }

    /**
     * 构建章纲生成的上下文 — 三层记忆体系（支持Token预算控制）
     *
     * 第一层：弧段摘要 (全局历史记忆，防止 AI 对早期情节失忆)
     * 第二层：近8章大纲 (微观衔接)
     * 第三层：上批钩子 (本章开头必须承接)
     */
    public String buildOutlineContext(String novelId, int chapterNum,
                                       NovelBase novel, String arcName, String arcOutline,
                                       int modelMaxToken) {
        TokenBudgetService.TokenBudget budget = tokenBudgetService.calculateBudget(modelMaxToken);
        StringBuilder sb = new StringBuilder();

        // === 第一层：弧段摘要 ===
        List<ArcSummary> arcSummaries = arcSummaryMapper
            .selectByNovelIdAndRange(novelId, chapterNum);
        if (!arcSummaries.isEmpty()) {
            StringBuilder arcSb = new StringBuilder();
            arcSb.append("【全书故事线回顾（按时序，必须与此保持一致）】\n");
            for (ArcSummary arc : arcSummaries) {
                arcSb.append(String.format("【第%d-%d章故事线】%s\n\n",
                    arc.getChapterFrom(), arc.getChapterTo(), arc.getSummary()));
            }
            sb.append(tokenBudgetService.truncateToBudget(arcSb.toString(), budget.getAllocated("arcSummary")));
        }

        // === 第二层：近8章局部大纲 ===
        List<NovelChapter> recentChapters = chapterMapper
            .selectRecentOutlines(novelId, chapterNum, 8);
        if (!recentChapters.isEmpty()) {
            StringBuilder outlineSb = new StringBuilder();
            outlineSb.append("【前情参考（保持剧情连贯，注意每章钩子的承接）】\n");
            for (NovelChapter ch : recentChapters) {
                String outline = truncate(ch.getChapterOutline(), 80);
                String hook = ch.getHook() != null ? "  →钩子：" + ch.getHook() : "";
                outlineSb.append(String.format("第%d章《%s》：%s%s\n",
                    ch.getChapterNum(), ch.getChapterTitle(), outline, hook));
            }
            sb.append(tokenBudgetService.truncateToBudget(outlineSb.toString(), budget.getAllocated("recentOutline")));
        }

        // === 第三层：上一章结尾钩子 ===
        NovelChapter prevChapter = chapterMapper
            .selectByNovelIdAndChapterNum(novelId, chapterNum - 1);
        if (prevChapter != null && prevChapter.getHook() != null) {
            sb.append(String.format(
                "【上一章结尾钩子（本章开头必须直接承接这个悬念）】\n%s\n\n",
                prevChapter.getHook()));
        }

        // === 事件图谱：规则驱动自动检索 ===
        try {
            List<com.novel.system.entity.mysql.ClueBook> pendingClues =
                clueBookService.getUnrecycledClues(novelId);
            List<com.novel.system.entity.mongo.EventNode> retrievedEvents =
                ruleDrivenRetrievalService.autoRetrieve(novelId, chapterNum,
                    arcOutline, pendingClues);
            if (!retrievedEvents.isEmpty()) {
                String eventsText = eventGraphService.formatEventsForPrompt(
                    retrievedEvents, "历史关键事件回忆（大纲中必须保持一致）");
                sb.append(tokenBudgetService.truncateToBudget(eventsText, 1000));
            }
        } catch (Exception e) {
            log.warn("事件图谱检索失败（不影响生成）: {}", e.getMessage());
        }

        // === 故事状态（核心枢纽，第一优先级数据源）===
        try {
            StoryState storyState = storyStateService.getOrCreate(novelId);
            String stateText = storyStateService.formatForPrompt(storyState);
            if (stateText != null && !stateText.isEmpty()) {
                sb.append(tokenBudgetService.truncateToBudget(stateText, budget.getAllocated("storyState")));
            }
        } catch (Exception e) {
            log.warn("获取故事状态失败: {}", e.getMessage());
        }

        // === 人物当前状态 ===
        String charStates = characterStateService.formatForPrompt(novelId);
        if (charStates != null && !charStates.isEmpty()) {
            sb.append("【人物当前状态（大纲中必须与此一致）】\n");
            sb.append(tokenBudgetService.truncateToBudget(charStates, budget.getAllocated("characterState")));
            sb.append("\n\n");
        }

        // === 人物关系图谱 ===
        try {
            String graphStr = characterGraphService.formatGraphAsString(novelId);
            if (graphStr != null && !graphStr.isEmpty() && !"暂无人物关系数据".equals(graphStr)) {
                sb.append(tokenBudgetService.truncateToBudget(
                    "【人物关系图谱】\n" + graphStr + "\n\n",
                    budget.getAllocated("characterState") / 2));
            }
        } catch (Exception e) {
            log.warn("获取人物关系图谱失败: {}", e.getMessage());
        }

        // === 伏笔 deadline 紧急提示 ===
        List<ClueBook> pendingClues = clueBookService.getUnrecycledClues(novelId);
        if (!pendingClues.isEmpty()) {
            StringBuilder clueSb = new StringBuilder();
            clueSb.append("【待回收伏笔（大纲中应适时安排回收，切勿遗忘）】\n");
            for (ClueBook clue : pendingClues) {
                String deadline = "";
                if (clue.getDeadline() != null) {
                    deadline = chapterNum >= clue.getDeadline() - 5
                        ? " ⚠️【紧急】建议" + clue.getDeadline() + "章前回收（已临近）"
                        : "（建议" + clue.getDeadline() + "章前回收）";
                }
                clueSb.append(String.format("第%d章埋：%s%s\n",
                    clue.getBuryChapter(), clue.getClueContent(), deadline));
            }
            sb.append(tokenBudgetService.truncateToBudget(clueSb.toString(), budget.getAllocated("clueBook")));
        }

        // === 卷级编排约束（如果存在） ===
        try {
            ArcOrchestration.ChapterPlan chapterPlan = arcOrchestrationService.getChapterPlan(novelId, chapterNum);
            if (chapterPlan != null) {
                StringBuilder planSb = new StringBuilder();
                planSb.append("【卷级编排约束——章纲必须遵守以下规划】\n");
                planSb.append(String.format("节奏标签：%s（quest=主线推进/fire=高潮冲突/constellation=支线展开）\n",
                        chapterPlan.getRhythmTag()));
                planSb.append(String.format("节奏强度：%d/10\n", chapterPlan.getRhythmIntensity()));
                planSb.append(String.format("叙事视角：%s\n", chapterPlan.getPerspective()));
                planSb.append(String.format("叙事模式：%s\n", chapterPlan.getNarrativeMode()));
                planSb.append(String.format("冲突类型：%s\n", chapterPlan.getConflictType()));
                planSb.append(String.format("结局类型：%s\n", chapterPlan.getOutcomeType()));
                planSb.append(String.format("核心事件：%s\n", chapterPlan.getKeyEvent()));
                planSb.append(String.format("章尾钩子方向：%s\n", chapterPlan.getHookDirection()));

                if (chapterPlan.getCluePlan() != null) {
                    ArcOrchestration.CluePlan cp = chapterPlan.getCluePlan();
                    if (cp.getBury() != null && !cp.getBury().isEmpty()) {
                        planSb.append("本章需埋设伏笔：").append(String.join("；", cp.getBury())).append("\n");
                    }
                    if (cp.getRecycle() != null && !cp.getRecycle().isEmpty()) {
                        planSb.append("本章需回收伏笔：").append(String.join("；", cp.getRecycle())).append("\n");
                    }
                }

                sb.append(tokenBudgetService.truncateToBudget(planSb.toString(), 500));
                sb.append("\n");
            }
        } catch (Exception e) {
            log.warn("获取卷级编排约束失败（不影响生成）: {}", e.getMessage());
        }

        // === 故事势能 ===
        if (novel.getStoryMomentum() != null && !novel.getStoryMomentum().isEmpty()) {
            sb.append("【当前故事势能（续写时保持张力）】\n")
              .append(novel.getStoryMomentum()).append("\n\n");
        }

        // === 基础上下文 ===
        appendNovelPreferences(sb, novel);
        sb.append("===小说总纲===\n")
          .append(tokenBudgetService.truncateToBudget(novel.getTotalOutline(), budget.getAllocated("novelOutline"))).append("\n\n");
        sb.append("===卷信息===\n")
          .append("卷名: ").append(arcName != null ? arcName : "待配置").append("\n")
          .append("卷纲: ").append(tokenBudgetService.truncateToBudget(arcOutline, budget.getAllocated("recentOutline") / 2)).append("\n\n");
        sb.append("===世界观===\n")
          .append(tokenBudgetService.truncateToBudget(novel.getWorldSetting(), budget.getAllocated("worldSetting"))).append("\n\n");

        return sb.toString();
    }
    
    /**
     * 构建章纲生成的上下文（兼容旧接口，默认8192 token预算）
     */
    public String buildOutlineContext(String novelId, int chapterNum,
                                       NovelBase novel, Object arcInfo) {
        String arcName = null;
        String arcOutline = null;

        try {
            java.lang.reflect.Field arcNameField = arcInfo.getClass().getDeclaredField("arcName");
            arcNameField.setAccessible(true);
            arcName = (String) arcNameField.get(arcInfo);

            java.lang.reflect.Field arcOutlineField = arcInfo.getClass().getDeclaredField("arcOutline");
            arcOutlineField.setAccessible(true);
            arcOutline = (String) arcOutlineField.get(arcInfo);
        } catch (Exception e) {
            log.warn("无法从 arcInfo 提取字段: {}", e.getMessage());
        }

        return buildOutlineContext(novelId, chapterNum, novel, arcName, arcOutline);
    }
    
    /**
     * 构建正文写作的上下文 — 增强版（默认8192 token预算）
     */
    public String buildWritingContext(String novelId, int chapterNum,
                                       NovelChapter chapter, NovelBase novel,
                                       String selectedIdea) {
        return buildWritingContext(novelId, chapterNum, chapter, novel, selectedIdea, 8192);
    }

    /**
     * 构建正文写作的上下文 — 增强版（支持Token预算控制）
     */
    public String buildWritingContext(String novelId, int chapterNum,
                                       NovelChapter chapter, NovelBase novel,
                                       String selectedIdea, int modelMaxToken) {
        TokenBudgetService.TokenBudget budget = tokenBudgetService.calculateBudget(modelMaxToken);
        StringBuilder sb = new StringBuilder();

        // === 三层记忆 ===
        // 弧段摘要
        List<ArcSummary> arcSummaries = arcSummaryMapper
            .selectByNovelIdAndRange(novelId, chapterNum);
        if (!arcSummaries.isEmpty()) {
            StringBuilder arcSb = new StringBuilder();
            arcSb.append("【全书故事线回顾（按时序，必须与此保持一致）】\n");
            for (ArcSummary arc : arcSummaries) {
                arcSb.append(String.format("【第%d-%d章故事线】%s\n\n",
                    arc.getChapterFrom(), arc.getChapterTo(), arc.getSummary()));
            }
            sb.append(tokenBudgetService.truncateToBudget(arcSb.toString(), budget.getAllocated("arcSummary")));
        }

        // 近5章局部大纲
        List<NovelChapter> recentChapters = chapterMapper
            .selectRecentOutlines(novelId, chapterNum, 5);
        if (!recentChapters.isEmpty()) {
            StringBuilder outlineSb = new StringBuilder();
            outlineSb.append("【前情参考（保持剧情连贯，注意每章钩子的承接）】\n");
            for (NovelChapter ch : recentChapters) {
                String outline = truncate(ch.getChapterOutline(), 80);
                String hook = ch.getHook() != null ? "  →钩子：" + ch.getHook() : "";
                outlineSb.append(String.format("第%d章《%s》：%s%s\n",
                    ch.getChapterNum(), ch.getChapterTitle(), outline, hook));
            }
            sb.append(tokenBudgetService.truncateToBudget(outlineSb.toString(), budget.getAllocated("recentOutline")));
        }

        // 上一章结尾钩子
        NovelChapter prevChapter = chapterMapper
            .selectByNovelIdAndChapterNum(novelId, chapterNum - 1);
        if (prevChapter != null && prevChapter.getHook() != null) {
            sb.append(String.format(
                "【上一章结尾钩子（本章开头必须直接承接这个悬念）】\n%s\n\n",
                prevChapter.getHook()));
        }

        // === 事件图谱：规则驱动自动检索 ===
        try {
            List<com.novel.system.entity.mysql.ClueBook> pendingClues =
                clueBookService.getUnrecycledClues(novelId);
            List<com.novel.system.entity.mongo.EventNode> retrievedEvents =
                ruleDrivenRetrievalService.autoRetrieve(novelId, chapterNum,
                    chapter.getChapterOutline(), pendingClues);
            if (!retrievedEvents.isEmpty()) {
                String eventsText = eventGraphService.formatEventsForPrompt(
                    retrievedEvents, "历史关键事件回忆（写作时必须保持一致）");
                sb.append(tokenBudgetService.truncateToBudget(eventsText, 1000));
            }
        } catch (Exception e) {
            log.warn("事件图谱检索失败（不影响生成）: {}", e.getMessage());
        }

        // === 故事状态（核心枢纽）===
        try {
            StoryState storyState = storyStateService.getOrCreate(novelId);
            String stateText = storyStateService.formatForPrompt(storyState);
            if (stateText != null && !stateText.isEmpty()) {
                sb.append(tokenBudgetService.truncateToBudget(stateText, budget.getAllocated("storyState")));
            }
        } catch (Exception e) {
            log.warn("获取故事状态失败: {}", e.getMessage());
        }

        // === 伏笔 deadline 紧急提示 ===
        List<ClueBook> pendingClues = clueBookService.getUnrecycledClues(novelId);
        if (!pendingClues.isEmpty()) {
            StringBuilder clueSb = new StringBuilder();
            clueSb.append("【待回收伏笔（写作中应适时安排回收）】\n");
            for (ClueBook clue : pendingClues) {
                String deadline = "";
                if (clue.getDeadline() != null) {
                    deadline = chapterNum >= clue.getDeadline() - 3
                        ? " ⚠️【紧急】建议本章回收（已临近deadline）"
                        : "（建议" + clue.getDeadline() + "章前回收）";
                }
                clueSb.append(String.format("第%d章埋：%s%s\n",
                    clue.getBuryChapter(), clue.getClueContent(), deadline));
            }
            sb.append(tokenBudgetService.truncateToBudget(clueSb.toString(), budget.getAllocated("clueBook")));
        }

        // === 卷级编排约束（如果存在） ===
        try {
            ArcOrchestration.ChapterPlan chapterPlan = arcOrchestrationService.getChapterPlan(novelId, chapterNum);
            if (chapterPlan != null) {
                StringBuilder planSb = new StringBuilder();
                planSb.append("【卷级编排约束——正文必须遵守以下规划】\n");
                planSb.append(String.format("节奏标签：%s（quest=主线推进/fire=高潮冲突/constellation=支线展开）\n",
                        chapterPlan.getRhythmTag()));
                planSb.append(String.format("节奏强度：%d/10\n", chapterPlan.getRhythmIntensity()));
                planSb.append(String.format("叙事视角：%s\n", chapterPlan.getPerspective()));
                planSb.append(String.format("叙事模式：%s\n", chapterPlan.getNarrativeMode()));
                planSb.append(String.format("冲突类型：%s\n", chapterPlan.getConflictType()));
                planSb.append(String.format("结局类型：%s\n", chapterPlan.getOutcomeType()));
                planSb.append(String.format("核心事件：%s\n", chapterPlan.getKeyEvent()));
                planSb.append(String.format("章尾钩子方向：%s\n", chapterPlan.getHookDirection()));

                if (chapterPlan.getCluePlan() != null) {
                    ArcOrchestration.CluePlan cp = chapterPlan.getCluePlan();
                    if (cp.getBury() != null && !cp.getBury().isEmpty()) {
                        planSb.append("本章需埋设伏笔：").append(String.join("；", cp.getBury())).append("\n");
                    }
                    if (cp.getRecycle() != null && !cp.getRecycle().isEmpty()) {
                        planSb.append("本章需回收伏笔：").append(String.join("；", cp.getRecycle())).append("\n");
                    }
                }

                sb.append(tokenBudgetService.truncateToBudget(planSb.toString(), 500));
                sb.append("\n");
            }
        } catch (Exception e) {
            log.warn("获取卷级编排约束失败（不影响生成）: {}", e.getMessage());
        }

        // === 人物当前状态 ===
        String charStates = characterStateService.formatForPrompt(novelId);
        if (charStates != null && !charStates.isEmpty()) {
            sb.append("【人物当前状态（写作中必须与此一致）】\n");
            sb.append(tokenBudgetService.truncateToBudget(charStates, budget.getAllocated("characterState")));
            sb.append("\n\n");
        }

        // === 人物关系图谱 ===
        try {
            String graphStr = characterGraphService.formatGraphAsString(novelId);
            if (graphStr != null && !graphStr.isEmpty() && !"暂无人物关系数据".equals(graphStr)) {
                sb.append(tokenBudgetService.truncateToBudget(
                    "【人物关系图谱】\n" + graphStr + "\n\n",
                    budget.getAllocated("characterState") / 2));
            }
        } catch (Exception e) {
            log.warn("获取人物关系图谱失败: {}", e.getMessage());
        }

        // === 故事势能 ===
        if (novel.getStoryMomentum() != null && !novel.getStoryMomentum().isEmpty()) {
            sb.append("【当前故事势能（写作时保持张力）】\n")
              .append(novel.getStoryMomentum()).append("\n\n");
        }

        // === 基础上下文 ===
        appendNovelPreferences(sb, novel);
        sb.append("===章节大纲===\n").append(chapter.getChapterOutline()).append("\n\n");
        sb.append("===写作方向===\n").append(selectedIdea != null ? selectedIdea : "无特殊要求").append("\n\n");
        sb.append("===小说总纲===\n")
          .append(tokenBudgetService.truncateToBudget(novel.getTotalOutline(), budget.getAllocated("novelOutline"))).append("\n\n");
        sb.append("===世界观===\n")
          .append(tokenBudgetService.truncateToBudget(novel.getWorldSetting(), budget.getAllocated("worldSetting"))).append("\n\n");

        return sb.toString();
    }
    
    /**
     * 追加小说偏好设定到上下文（类型、基调、雷点）
     */
    private void appendNovelPreferences(StringBuilder sb, NovelBase novel) {
        boolean hasPreference = false;
        StringBuilder prefSb = new StringBuilder();

        if (novel.getNovelType() != null && !novel.getNovelType().isEmpty()) {
            prefSb.append("类型标签：").append(formatJsonArray(novel.getNovelType())).append("\n");
            hasPreference = true;
        }
        if (novel.getTone() != null && !novel.getTone().isEmpty()) {
            prefSb.append("故事基调：").append(formatJsonArray(novel.getTone())).append("\n");
            hasPreference = true;
        }
        if (novel.getTabooList() != null && !novel.getTabooList().isEmpty()) {
            prefSb.append("【严格禁止】以下雷点绝对不能出现：").append(formatJsonArray(novel.getTabooList())).append("\n");
            hasPreference = true;
        }

        if (hasPreference) {
            sb.append("===创作偏好设定（必须遵循）===\n")
              .append(prefSb).append("\n");
        }
    }

    /**
     * 将 JSON 数组字符串 ["爽文","穿越"] 格式化为 "爽文、穿越"
     */
    private String formatJsonArray(String json) {
        if (json == null || json.isEmpty()) return "";
        try {
            List<String> list = objectMapper.readValue(json, new TypeReference<List<String>>() {});
            return String.join("、", list);
        } catch (Exception e) {
            return json;
        }
    }

    /**
     * 截断字符串到指定长度
     */
    private String truncate(String str, int maxLength) {
        if (str == null) {
            return "";
        }
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength) + "...";
    }
    
    /**
     * 构建文风参考片段（从已有章节抽取代表性段落）
     * 
     * 【核心作用】
     * 从小说已有章节中抽取最能代表作者风格的2-3段（500-800字）
     * 注入到prompt中作为风格参考，帮助AI模仿写作风格
     * 
     * 【抽取策略】
     * 1. 从前3章抽取1段（建立基调）
     * 2. 从中间章节抽取1段（稳定风格）
     * 3. 从对话密集段落抽取1段（对话风格参考）
     * 
     * @param novelId 小说ID
     * @return 格式化的文风参考文本
     */
    public String buildStyleReference(String novelId) {
        StringBuilder sb = new StringBuilder();
        
        try {
            // 获取已有章节（最多取前10章）
            List<NovelChapter> chapters = chapterMapper.selectByNovelIdLimit(novelId, 10);
            if (chapters.isEmpty()) {
                return "";
            }
            
            sb.append("【文风参考——请模仿此风格写作】\n");
            sb.append("以下片段来自本书前文，请模仿其句式、节奏、修辞习惯：\n\n");
            
            int sampleCount = 0;
            int maxSamples = 3;
            int targetLength = 250; // 每段约250字
            
            // 从前3章抽取
            for (int i = 0; i < Math.min(3, chapters.size()) && sampleCount < maxSamples; i++) {
                String content = chapters.get(i).getContent();
                if (content != null && content.length() > 100) {
                    String sample = extractRepresentativeParagraph(content, targetLength);
                    if (sample != null && !sample.isEmpty()) {
                        sb.append("--- 片段").append(sampleCount + 1).append("（第")
                          .append(chapters.get(i).getChapterNum()).append("章）---\n");
                        sb.append(sample).append("\n\n");
                        sampleCount++;
                    }
                }
            }
            
            // 如果样本不够，从中间章节补充
            if (sampleCount < maxSamples && chapters.size() > 5) {
                String content = chapters.get(chapters.size() / 2).getContent();
                if (content != null && content.length() > 100) {
                    String sample = extractRepresentativeParagraph(content, targetLength);
                    if (sample != null && !sample.isEmpty()) {
                        sb.append("--- 片段").append(sampleCount + 1).append("（第")
                          .append(chapters.get(chapters.size() / 2).getChapterNum()).append("章）---\n");
                        sb.append(sample).append("\n\n");
                        sampleCount++;
                    }
                }
            }
            
            if (sampleCount > 0) {
                sb.append("写作时参考上述片段的：\n");
                sb.append("- 句子长短比例\n");
                sb.append("- 段落密度\n");
                sb.append("- 对话风格\n");
                sb.append("- 修辞偏好\n\n");
                return sb.toString();
            }
            
        } catch (Exception e) {
            log.warn("构建文风参考失败: {}", e.getMessage());
        }
        
        return "";
    }
    
    /**
     * 从文本中提取代表性段落
     * 
     * 【提取策略】
     * 1. 按段落分割
     * 2. 优先选择包含对话的段落（对话更能体现风格）
     * 3. 其次选择长度适中的段落
     * 4. 截取到目标长度
     * 
     * @param text 原始文本
     * @param targetLength 目标长度（字）
     * @return 代表性段落
     */
    private String extractRepresentativeParagraph(String text, int targetLength) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        
        // 按段落分割
        String[] paragraphs = text.split("\\n+");
        
        // 优先选择包含对话的段落（对话更能体现风格）
        for (String para : paragraphs) {
            para = para.trim();
            if (para.length() >= 50 && para.length() <= targetLength * 2) {
                // 检查是否包含对话（引号）
                if (para.contains("\u201c") || para.contains("\u201d") || para.contains("\"")) {
                    return truncate(para, targetLength);
                }
            }
        }
        
        // 如果没有对话段落，选择长度适中的段落
        for (String para : paragraphs) {
            para = para.trim();
            if (para.length() >= 80 && para.length() <= targetLength * 2) {
                return truncate(para, targetLength);
            }
        }
        
        // 如果都不满足，取前N个字
        if (text.length() > 50) {
            return truncate(text.replaceAll("\n+", " "), targetLength);
        }
        
        return "";
    }
}