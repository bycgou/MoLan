package com.novel.system.service.chapter;

import com.novel.system.entity.mysql.*;
import com.novel.system.mapper.ArcSummaryMapper;
import com.novel.system.mapper.NovelChapterMapper;
import com.novel.system.service.character.CharacterStateService;
import com.novel.system.service.clue.ClueBookService;
import com.novel.system.ai.rag.RagService;
import com.novel.system.entity.mongo.ChapterSummary;
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
    private final RagService ragService;
    
    /**
     * 构建章纲生成的上下文 — 三层记忆体系
     * 
     * 第一层：弧段摘要 (全局历史记忆，防止 AI 对早期情节失忆)
     * 第二层：近8章大纲 (微观衔接)
     * 第三层：上批钩子 (本章开头必须承接)
     * 
     * @param novelId 小说ID
     * @param chapterNum 章节号
     * @param novel 小说基本信息
     * @param arcName 卷名称
     * @param arcOutline 卷大纲
     */
    public String buildOutlineContext(String novelId, int chapterNum, 
                                       NovelBase novel, String arcName, String arcOutline) {
        StringBuilder sb = new StringBuilder();
        
        // === 第一层：弧段摘要 ===
        List<ArcSummary> arcSummaries = arcSummaryMapper
            .selectByNovelIdAndRange(novelId, chapterNum);
        if (!arcSummaries.isEmpty()) {
            sb.append("【全书故事线回顾（按时序，必须与此保持一致）】\n");
            for (ArcSummary arc : arcSummaries) {
                sb.append(String.format("【第%d-%d章故事线】%s\n\n", 
                    arc.getChapterFrom(), arc.getChapterTo(), arc.getSummary()));
            }
        }
        
        // === 第二层：近8章局部大纲 ===
        List<NovelChapter> recentChapters = chapterMapper
            .selectRecentOutlines(novelId, chapterNum, 8);
        if (!recentChapters.isEmpty()) {
            sb.append("【前情参考（保持剧情连贯，注意每章钩子的承接）】\n");
            for (NovelChapter ch : recentChapters) {
                String outline = truncate(ch.getChapterOutline(), 80);
                String hook = ch.getHook() != null ? "  →钩子：" + ch.getHook() : "";
                sb.append(String.format("第%d章《%s》：%s%s\n", 
                    ch.getChapterNum(), ch.getChapterTitle(), outline, hook));
            }
            sb.append("\n");
        }
        
        // === 第三层：上一章结尾钩子 ===
        NovelChapter prevChapter = chapterMapper
            .selectByNovelIdAndChapterNum(novelId, chapterNum - 1);
        if (prevChapter != null && prevChapter.getHook() != null) {
            sb.append(String.format(
                "【上一章结尾钩子（本章开头必须直接承接这个悬念）】\n%s\n\n", 
                prevChapter.getHook()));
        }
        
        // === 人物当前状态 ===
        String charStates = characterStateService.formatForPrompt(novelId);
        if (charStates != null && !charStates.isEmpty()) {
            sb.append("【人物当前状态（大纲中必须与此一致）】\n")
              .append(charStates).append("\n\n");
        }
        
        // === 伏笔 deadline 紧急提示 ===
        List<ClueBook> pendingClues = clueBookService.getUnrecycledClues(novelId);
        if (!pendingClues.isEmpty()) {
            sb.append("【待回收伏笔（大纲中应适时安排回收，切勿遗忘）】\n");
            for (ClueBook clue : pendingClues) {
                String deadline = "";
                if (clue.getDeadline() != null) {
                    deadline = chapterNum >= clue.getDeadline() - 5
                        ? " ⚠️【紧急】建议" + clue.getDeadline() + "章前回收（已临近）"
                        : "（建议" + clue.getDeadline() + "章前回收）";
                }
                sb.append(String.format("第%d章埋：%s%s\n", 
                    clue.getBuryChapter(), clue.getClueContent(), deadline));
            }
            sb.append("\n");
        }
        
        // === 故事势能 ===
        if (novel.getStoryMomentum() != null && !novel.getStoryMomentum().isEmpty()) {
            sb.append("【当前故事势能（续写时保持张力）】\n")
              .append(novel.getStoryMomentum()).append("\n\n");
        }
        
        // === 基础上下文 (保留 MoYun 原有) ===
        appendNovelPreferences(sb, novel);
        sb.append("===小说总纲===\n")
          .append(truncate(novel.getTotalOutline(), 4000)).append("\n\n");
        sb.append("===卷信息===\n")
          .append("卷名: ").append(arcName != null ? arcName : "待配置").append("\n")
          .append("卷纲: ").append(truncate(arcOutline, 1500)).append("\n\n");
        sb.append("===世界观===\n").append(novel.getWorldSetting()).append("\n\n");
        
        return sb.toString();
    }
    
    /**
     * 构建章纲生成的上下文（兼容旧接口）
     */
    public String buildOutlineContext(String novelId, int chapterNum, 
                                       NovelBase novel, Object arcInfo) {
        // 尝试从 arcInfo 中提取 arcName 和 arcOutline
        String arcName = null;
        String arcOutline = null;
        
        try {
            // 使用反射获取字段值
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
     * 构建正文写作的上下文 — 增强版
     */
    public String buildWritingContext(String novelId, int chapterNum,
                                       NovelChapter chapter, NovelBase novel,
                                       String selectedIdea) {
        StringBuilder sb = new StringBuilder();
        
        // === 三层记忆（同上，但针对写作场景微调）===
        // 弧段摘要
        List<ArcSummary> arcSummaries = arcSummaryMapper
            .selectByNovelIdAndRange(novelId, chapterNum);
        if (!arcSummaries.isEmpty()) {
            sb.append("【全书故事线回顾（按时序，必须与此保持一致）】\n");
            for (ArcSummary arc : arcSummaries) {
                sb.append(String.format("【第%d-%d章故事线】%s\n\n", 
                    arc.getChapterFrom(), arc.getChapterTo(), arc.getSummary()));
            }
        }
        
        // 近5章局部大纲
        List<NovelChapter> recentChapters = chapterMapper
            .selectRecentOutlines(novelId, chapterNum, 5);
        if (!recentChapters.isEmpty()) {
            sb.append("【前情参考（保持剧情连贯，注意每章钩子的承接）】\n");
            for (NovelChapter ch : recentChapters) {
                String outline = truncate(ch.getChapterOutline(), 80);
                String hook = ch.getHook() != null ? "  →钩子：" + ch.getHook() : "";
                sb.append(String.format("第%d章《%s》：%s%s\n", 
                    ch.getChapterNum(), ch.getChapterTitle(), outline, hook));
            }
            sb.append("\n");
        }
        
        // 上一章结尾钩子
        NovelChapter prevChapter = chapterMapper
            .selectByNovelIdAndChapterNum(novelId, chapterNum - 1);
        if (prevChapter != null && prevChapter.getHook() != null) {
            sb.append(String.format(
                "【上一章结尾钩子（本章开头必须直接承接这个悬念）】\n%s\n\n", 
                prevChapter.getHook()));
        }
        
        // === 伏笔 deadline 紧急提示 ===
        List<ClueBook> pendingClues = clueBookService.getUnrecycledClues(novelId);
        if (!pendingClues.isEmpty()) {
            sb.append("【待回收伏笔（写作中应适时安排回收）】\n");
            for (ClueBook clue : pendingClues) {
                String deadline = "";
                if (clue.getDeadline() != null) {
                    // 写作时 threshold 更小，仅提示临近 deadline 的
                    deadline = chapterNum >= clue.getDeadline() - 3
                        ? " ⚠️【紧急】建议本章回收（已临近deadline）"
                        : "（建议" + clue.getDeadline() + "章前回收）";
                }
                sb.append(String.format("第%d章埋：%s%s\n", 
                    clue.getBuryChapter(), clue.getClueContent(), deadline));
            }
            sb.append("\n");
        }
        
        // === 章节简介蓝图（如已生成）===
        if (chapter.getSynopsisId() != null) {
            // 注意：这里需要 ChapterSynopsisMapper，暂时使用占位
            // ChapterSynopsis synopsis = synopsisMapper.selectById(chapter.getSynopsisId());
            // if (synopsis != null) {
            //     sb.append("【章节简介（详细写作蓝图，必须遵循）】\n")
            //       .append(formatSynopsis(synopsis)).append("\n\n");
            // }
        }
        
        // === 人物当前状态 ===
        String charStates = characterStateService.formatForPrompt(novelId);
        if (charStates != null && !charStates.isEmpty()) {
            sb.append("【人物当前状态（写作中必须与此一致）】\n")
              .append(charStates).append("\n\n");
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
          .append(truncate(novel.getTotalOutline(), 2000)).append("\n\n");
        sb.append("===世界观===\n").append(truncate(novel.getWorldSetting(), 1000)).append("\n\n");

        // === RAG向量检索上下文 ===
        try {
            String queryText = chapter.getChapterOutline() != null ? chapter.getChapterOutline() : "";
            if (!queryText.isEmpty()) {
                List<ChapterSummary> ragResults = ragService.vectorSearch(novelId, queryText, 3);
                if (!ragResults.isEmpty()) {
                    sb.append("【RAG语义检索相关章节（参考写作风格和情节）】\n");
                    for (ChapterSummary cs : ragResults) {
                        sb.append(String.format("第%d章摘要：%s\n",
                                cs.getChapterNum(),
                                truncate(cs.getContent(), 200)));
                    }
                    sb.append("\n");
                    log.debug("RAG检索到{}条相关上下文: 小说={}, 章节={}", ragResults.size(), novelId, chapterNum);
                }
            }
        } catch (Exception e) {
            log.warn("RAG检索失败（不影响写作）: {}", e.getMessage());
        }

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
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            java.util.List<String> list = mapper.readValue(json, java.util.List.class);
            return String.join("、", list);
        } catch (Exception e) {
            // 如果解析失败，原样返回
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