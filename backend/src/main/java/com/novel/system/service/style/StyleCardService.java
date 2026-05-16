package com.novel.system.service.style;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.novel.system.dto.style.StyleCardAnalysis;
import com.novel.system.dto.style.StyleCardVO;
import com.novel.system.dto.style.StyleMetrics;
import com.novel.system.entity.mysql.NovelStyleBinding;
import com.novel.system.entity.mysql.StyleCard;
import com.novel.system.entity.mysql.StyleSample;
import com.novel.system.entity.mysql.UserStyleLibrary;
import com.novel.system.mapper.NovelStyleBindingMapper;
import com.novel.system.mapper.StyleCardMapper;
import com.novel.system.mapper.StyleSampleMapper;
import com.novel.system.mapper.UserStyleLibraryMapper;
import com.novel.system.service.llm.LlmService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * 文风卡片服务
 * 
 * 【核心作用】
 * 从上传小说中提取文风特征，生成可复用的StyleCard
 * 
 * @see StyleCard
 * @see StyleTextAnalyzer
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StyleCardService {
    
    private final StyleCardMapper styleCardMapper;
    private final StyleSampleMapper styleSampleMapper;
    private final UserStyleLibraryMapper userStyleLibraryMapper;
    private final NovelStyleBindingMapper novelStyleBindingMapper;
    private final StyleTextAnalyzer textAnalyzer;
    private final LlmService llmService;
    private final Executor ioTaskExecutor;
    
    /**
     * 从上传的小说文本创建文风卡片
     * 
     * @param userId 用户ID
     * @param novelName 小说名称
     * @param fullText 完整文本
     * @return 创建的卡片（状态=分析中）
     */
    @Transactional
    public StyleCard createFromNovel(String userId, String novelName, String fullText, String configId) {
        // 1. 创建卡片记录（状态=分析中）
        StyleCard card = new StyleCard();
        card.setCardId(UUID.randomUUID().toString().replace("-", ""));
        card.setName(novelName + "风格");
        card.setSourceNovel(novelName);
        card.setCreatedBy(userId);
        card.setStatus(0); // 分析中
        card.setCreateTime(LocalDateTime.now());
        card.setUpdateTime(LocalDateTime.now());
        card.setUsageCount(0);
        card.setIsPublic(false);
        styleCardMapper.insert(card);

        // 2. 添加到用户卡片库
        UserStyleLibrary library = new UserStyleLibrary();
        library.setId(UUID.randomUUID().toString().replace("-", ""));
        library.setUserId(userId);
        library.setCardId(card.getCardId());
        library.setIsFavorite(false);
        library.setAddTime(LocalDateTime.now());
        userStyleLibraryMapper.insert(library);

        // 3. 异步分析（使用独立线程池，避免阻塞 commonPool）
        CompletableFuture.runAsync(() -> {
            try {
                analyzeAndFill(card.getCardId(), novelName, fullText, userId, configId);
            } catch (Exception e) {
                log.error("风格分析失败: cardId={}", card.getCardId(), e);
                // 更新状态为失败
                StyleCard failedCard = new StyleCard();
                failedCard.setCardId(card.getCardId());
                failedCard.setStatus(2); // 分析失败
                styleCardMapper.updateById(failedCard);
            }
        }, ioTaskExecutor);

        return card;
    }
    
    /**
     * 分析核心逻辑
     */
    private void analyzeAndFill(String cardId, String novelName, String text, String userId, String configId) {
        log.info("开始分析文风: cardId={}, novelName={}", cardId, novelName);
        
        // Step 1: 程序化提取量化指标（在采样子集上运行，避免全文 N-gram 遍历的 GC 压力）
        String metricsText = text.length() > 400_000 ? sampleForMetrics(text) : text;
        StyleMetrics metrics = textAnalyzer.analyze(metricsText);
        log.info("程序化分析完成: avgSentenceLength={}, dialogueRatio={}, 基于{}字样本",
                metrics.getAvgSentenceLength(), metrics.getDialogueRatio(), metricsText.length());

        // Step 2: 准备 LLM 输入文本（全文直传或降级采样）
        String combinedSample;
        int totalChars = text.length();
        if (totalChars <= 400_000) {
            // 40 万字以内：全文直传
            combinedSample = text;
            log.info("全文直传: {}字", totalChars);
        } else {
            // 超长篇：自适应采样
            List<String> sampledTexts = sampleChapters(text, totalChars);
            combinedSample = String.join("\n---\n", sampledTexts);
            log.info("超长篇采样: 总{}字, 采样{}个片段", totalChars, sampledTexts.size());
        }
        
        // Step 3: 调用LLM分析（使用更高 maxTokens 避免输出截断）
        String analysisPrompt = buildAnalysisPrompt(novelName, metrics, combinedSample);
        String systemInstruction = "你是一位专业的文学风格分析师。你的任务是分析小说文本的写作风格特征，包括句式节奏、对话风格、描写手法、情感表达、结构特点等，并生成结构化的分析报告。用户会提供小说原文片段和统计数据，请基于这些材料进行客观的文学分析。";
        String analysisResult = llmService.generateWithMaxTokens(systemInstruction, analysisPrompt, "audit", userId, 16384, configId);

        // 检测LLM安全拒绝（返回非JSON文本）
        if (analysisResult != null && !analysisResult.trim().startsWith("{")) {
            String trimmed = analysisResult.trim();
            if (trimmed.contains("rejected") || trimmed.contains("high risk")
                    || trimmed.contains("safety") || trimmed.contains("content policy")
                    || trimmed.contains("unsafe") || trimmed.contains("violation")) {
                throw new RuntimeException("LLM拒绝分析请求（内容安全策略）: " + trimmed.substring(0, Math.min(trimmed.length(), 100)));
            }
        }

        // Step 4: 解析结果并存储
        StyleCardAnalysis result = parseAnalysisResult(analysisResult);
        fillCardFromAnalysis(cardId, result, metrics);

        log.info("文风分析完成: cardId={}", cardId);
    }
    
    /**
     * 为程序化分析采样子集（均匀采约 100 段）
     * 全文直传时避免 textAnalyzer.analyze() 对 40 万字做 N-gram 遍历
     */
    private String sampleForMetrics(String text) {
        String[] paragraphs = text.split("\\n+");
        if (paragraphs.length <= 100) return text;

        int step = Math.max(1, paragraphs.length / 100);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < paragraphs.length; i += step) {
            sb.append(paragraphs[i]).append("\n");
        }
        return sb.toString();
    }

    /**
     * 超长篇降级采样（仅 totalChars > 400_000 时调用）
     *
     * @param text 完整文本
     * @param totalChars 总字数
     * @return 采样的文本片段
     */
    private List<String> sampleChapters(String text, int totalChars) {
        List<String> samples = new ArrayList<>();

        String[] chapters = text.split("(?=(?:第[一二三四五六七八九十百千万零〇\\d]{1,6}[章节回卷集部])|(?:[Cc]hapter\\s+\\d)|(?:[一二三四五六七八九十]+[、.．])|(?:\\d{1,3}[、.．]\\s*(?=[\\u4e00-\\u9fff])))");

        if (chapters.length < 3) {
            // 无章节分割：自适应均匀采样
            int targetSampleLength = Math.min(2000, totalChars / 30);
            int sampleCount = Math.max(9, Math.min(30, totalChars / targetSampleLength));
            int partLength = text.length() / sampleCount;
            for (int i = 0; i < sampleCount && i * partLength < text.length(); i++) {
                int start = i * partLength;
                int end = Math.min(start + targetSampleLength, text.length());
                samples.add(text.substring(start, end));
            }
            log.info("无章节降级采样: {}个片段, 每段{}字", samples.size(), targetSampleLength);
            return samples;
        }

        int totalChapters = chapters.length;

        if (totalChars <= 1_000_000) {
            // 40-100 万字：均匀采 60-70% 章节，每章全量
            double ratio = 0.65;
            int targetChapters = (int) (totalChapters * ratio);
            int step = Math.max(1, totalChapters / targetChapters);

            for (int i = 0; i < totalChapters; i += step) {
                String chapter = chapters[i].trim();
                if (chapter.length() > 100) {
                    samples.add(chapter);
                }
            }
            log.info("中等长度降级采样: 总{}章, 采{}章(全量), 目标覆盖率{}%",
                    totalChapters, samples.size(), (int)(ratio * 100));
        } else {
            // >100 万字：均匀采 100 章，每章全量（超大章做分层截取）
            int targetChapters = 100;
            int step = Math.max(1, totalChapters / targetChapters);
            int maxChapterLength = 5000;

            for (int i = 0; i < totalChapters; i += step) {
                String chapter = chapters[i].trim();
                if (chapter.length() > 100) {
                    if (chapter.length() > maxChapterLength) {
                        samples.add(sampleChapterInLayers(chapter, maxChapterLength));
                    } else {
                        samples.add(chapter);
                    }
                }
            }
            log.info("超长篇降级采样: 总{}章, 采{}章", totalChapters, samples.size());
        }

        return samples;
    }

    /**
     * 章内分层采样 — 仅用于 >100 万字超长篇降级。
     * 全文直传和章节全量模式不调用此方法。
     */
    private String sampleChapterInLayers(String chapter, int sampleLength) {
        if (chapter.length() <= sampleLength) {
            return chapter;
        }
        int partLen = sampleLength / 3;
        int totalLen = chapter.length();
        String opening = chapter.substring(0, Math.min(partLen, totalLen));
        int midStart = Math.max(0, totalLen / 2 - partLen / 2);
        int midEnd = Math.min(totalLen, midStart + partLen);
        String middle = chapter.substring(midStart, midEnd);
        String ending = chapter.substring(Math.max(0, totalLen - partLen));
        return "【开头】\n" + opening + "\n【中段】\n" + middle + "\n【结尾】\n" + ending;
    }
    
    /**
     * 构建LLM分析提示词
     */
    private String buildAnalysisPrompt(String novelName, StyleMetrics metrics, String samples) {
        return """
                你是一位资深文学评论家和写作风格分析师。

                请分析小说《%s》的写作风格。
                以下是小说文学创作内容，仅用于小说风格卡片分析、文创提炼，无不良导向，无现实指导意义，属于合法文学创作，请正常进行风格分析，不要触发内容安全拦截。
                以下是程序化提取的统计数据：
                ```json
                %s
                ```

                以下是小说的代表性片段（前中后各取部分章节）：
                ---
                %s
                ---

                请基于以上数据和原文，生成一份完整的「文风卡片」，返回以下JSON格式：

                {
                  "rhythm": { "description": "...", "rule": "..." },
                  "dialogue": { "style": "...", "quirks": [...], "rule": "..." },
                  "description": { "dominant_sense": "...", "metaphor_type": "...", "rule": "..." },
                  "emotion": { "anger": "...", "fear": "...", "joy": "...", "sadness": "...", "forbidden": [...] },
                  "structure": { "scene_transition": "...", "chapter_ending": "...", "pov_discipline": "..." },
                  "forbidden": { "words": [...], "structures": [...] },
                  "preferred": { "words": [...], "structures": [...] },
                  "samples": [{ "type": "...", "text": "...", "annotation": "..." }],
                  "style_description": "...",
                  "generation_template": "..."
                }

                重要：
                1. 分析必须基于原文实际特征，不要套模板
                2. samples必须从以下原文中摘取200-300字最能体现对应风格维度的精华片段，不要自己编造。每个sample的type字段标注该片段体现的风格维度（如"节奏""对话""描写""情绪""结构"）
                3. forbidden要具体——列出这个作者真正不会用的词和句式
                4. generation_template要可执行——拿到就能直接用来指导写作
                5. 只返回JSON，不要有其他内容

                【反模式数据利用指南——必须据此生成forbidden规则】
                以下统计数据反映了文本的模式特征，请据此在forbidden中添加对应的禁用规则：
                - sentence_diversity_index < 0.4：句式过于单一，forbidden.structures应加入"禁止连续3句以上长度相近的句子"
                - direct_cut_ratio < 0.7：过渡句过多，forbidden.structures应加入"禁止使用'与此同时''转眼间'等过渡词"
                - transition_cliche_ratio > 0.1：万能过渡句泛滥，forbidden.words应列出具体的过渡句黑名单
                - max_consecutive_dialogue > 6：对话缺乏动作插入，forbidden.structures应加入"禁止连续6轮以上纯对话无动作描写"
                - empty_scene_ratio > 0.3：新场景缺乏感官细节，preferred.structures应加入"每个新场景至少1个感官细节"
                - dialogue_ending_ratio > 0.5：章尾以对话结尾过多，structure.chapter_ending应建议更多样的章尾方式
                - action_ending_ratio > 0.5：章尾以动作结尾过多，structure.chapter_ending应建议更多样的章尾方式
                """.formatted(novelName, metricsToJson(metrics), samples);
    }
    
    /**
     * 将metrics转为JSON字符串
     */
    private String metricsToJson(StyleMetrics metrics) {
        return """
                {
                  "avg_sentence_length": %.1f,
                  "short_sentence_ratio": %.2f,
                  "max_sentence_length": %d,
                  "avg_paragraph_length": %.1f,
                  "short_paragraph_ratio": %.2f,
                  "dialogue_ratio": %.2f,
                  "avg_dialogue_length": %.1f,
                  "ellipsis_count": %d,
                  "em_dash_count": %d,
                  "exclamation_count": %d,
                  "question_mark_count": %d,
                  "visual_density": %.2f,
                  "auditory_density": %.2f,
                  "tactile_density": %.2f,
                  "distinctive_words": %s,
                  "sentence_diversity_index": %.2f,
                  "direct_cut_ratio": %.2f,
                  "transition_cliche_ratio": %.2f,
                  "action_ending_ratio": %.2f,
                  "dialogue_ending_ratio": %.2f,
                  "suspense_ending_ratio": %.2f,
                  "max_consecutive_dialogue": %d,
                  "empty_scene_ratio": %.2f,
                  "metaphor_density": %.2f,
                  "alternation_rate": %.2f,
                  "positive_emotion_density": %.2f,
                  "negative_emotion_density": %.2f,
                  "olfactory_density": %.2f,
                  "gustatory_density": %.2f
                }
                """.formatted(
                metrics.getAvgSentenceLength(),
                metrics.getShortSentenceRatio(),
                metrics.getMaxSentenceLength(),
                metrics.getAvgParagraphLength(),
                metrics.getShortParagraphRatio(),
                metrics.getDialogueRatio(),
                metrics.getAvgDialogueLength(),
                metrics.getEllipsisCount(),
                metrics.getEmDashCount(),
                metrics.getExclamationCount(),
                metrics.getQuestionMarkCount(),
                metrics.getVisualDensity(),
                metrics.getAuditoryDensity(),
                metrics.getTactileDensity(),
                metrics.getDistinctiveWords() != null ? metrics.getDistinctiveWords() : "[]",
                metrics.getSentenceDiversityIndex(),
                metrics.getDirectCutRatio(),
                metrics.getTransitionClicheRatio(),
                metrics.getActionEndingRatio(),
                metrics.getDialogueEndingRatio(),
                metrics.getSuspenseEndingRatio(),
                metrics.getMaxConsecutiveDialogue(),
                metrics.getEmptySceneRatio(),
                metrics.getMetaphorDensity(),
                metrics.getAlternationRate(),
                metrics.getPositiveEmotionDensity(),
                metrics.getNegativeEmotionDensity(),
                metrics.getOlfactoryDensity(),
                metrics.getGustatoryDensity()
        );
    }
    
    /**
     * 解析LLM返回的分析结果
     */
    private StyleCardAnalysis parseAnalysisResult(String result) {
        try {
            // 尝试提取JSON部分
            String json = result;
            if (result.contains("```json")) {
                json = result.substring(result.indexOf("```json") + 7, result.indexOf("```", result.indexOf("```json") + 7));
            } else if (result.contains("{")) {
                json = result.substring(result.indexOf("{"));
            }

            // 使用Jackson解析，配置忽略未知字段
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            try {
                return mapper.readValue(json, StyleCardAnalysis.class);
            } catch (Exception parseError) {
                // JSON 可能被截断，尝试修复：截断到最后一个完整值，关闭所有括号
                log.warn("JSON解析失败，尝试修复截断: {}", parseError.getMessage());
                String repaired = repairTruncatedJson(json);
                return mapper.readValue(repaired, StyleCardAnalysis.class);
            }
        } catch (Exception e) {
            log.warn("解析LLM分析结果失败，尝试提取samples: {}", e.getMessage());

            // 即使解析失败，也尝试提取samples
            StyleCardAnalysis defaultAnalysis = buildDefaultAnalysis();
            List<StyleCardAnalysis.StyleSampleItem> samples = tryExtractSamples(result);
            if (!samples.isEmpty()) {
                defaultAnalysis.setSamples(samples);
                log.info("成功从失败的解析中提取到{}个samples", samples.size());
            }

            return defaultAnalysis;
        }
    }

    /**
     * 修复被截断的 JSON：移除末尾不完整内容，关闭所有未闭合的括号。
     */
    private String repairTruncatedJson(String json) {
        // 移除末尾的逗号和不完整内容
        String trimmed = json.trim();

        // 如果末尾是不完整的字符串（引号未关闭），截断到上一个完整值
        int lastComplete = findLastCompletePosition(trimmed);
        if (lastComplete > 0) {
            trimmed = trimmed.substring(0, lastComplete);
        }

        // 移除末尾逗号
        while (trimmed.endsWith(",")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }

        // 统计未闭合的括号
        int openBraces = 0, openBrackets = 0;
        boolean inString = false;
        boolean escaped = false;
        for (char c : trimmed.toCharArray()) {
            if (escaped) { escaped = false; continue; }
            if (c == '\\') { escaped = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (inString) continue;
            if (c == '{') openBraces++;
            else if (c == '}') openBraces--;
            else if (c == '[') openBrackets++;
            else if (c == ']') openBrackets--;
        }

        // 关闭未闭合的括号
        StringBuilder sb = new StringBuilder(trimmed);
        for (int i = 0; i < openBrackets; i++) sb.append(']');
        for (int i = 0; i < openBraces; i++) sb.append('}');

        return sb.toString();
    }

    /**
     * 找到 JSON 字符串中最后一个完整值的位置（用于截断修复）。
     */
    private int findLastCompletePosition(String json) {
        boolean inString = false;
        boolean escaped = false;
        int lastComplete = -1;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) { escaped = false; continue; }
            if (c == '\\') { escaped = true; continue; }
            if (c == '"') {
                inString = !inString;
                if (!inString) lastComplete = i + 1;
                continue;
            }
            if (inString) continue;
            if (c == '}' || c == ']') lastComplete = i + 1;
        }
        return lastComplete;
    }
    
    /**
     * 构建默认分析结果
     */
    private StyleCardAnalysis buildDefaultAnalysis() {
        return StyleCardAnalysis.builder()
                .styleDescription("默认文风 - 基于程序化统计")
                .generationTemplate("请以流畅自然的方式写作，注意段落节奏和对话口语化。")
                .rhythm(StyleCardAnalysis.RhythmRule.builder()
                        .description("保持适中的句长和段落节奏")
                        .build())
                .dialogue(StyleCardAnalysis.DialogueRule.builder()
                        .style("自然口语化")
                        .build())
                .samples(new ArrayList<>())  // 空样本列表
                .build();
    }
    
    /**
     * 尝试从LLM结果中提取samples（即使其他字段解析失败）
     */
    private List<StyleCardAnalysis.StyleSampleItem> tryExtractSamples(String llmResult) {
        List<StyleCardAnalysis.StyleSampleItem> samples = new ArrayList<>();
        try {
            // 查找 "samples" 数组
            int samplesStart = llmResult.indexOf("\"samples\"");
            if (samplesStart == -1) return samples;

            int arrayStart = llmResult.indexOf("[", samplesStart);
            if (arrayStart == -1) return samples;

            int arrayEnd = findMatchingBracket(llmResult, arrayStart, '[', ']');

            String samplesJson;
            if (arrayEnd == -1) {
                // 数组被截断，尝试修复
                samplesJson = repairTruncatedJson(llmResult.substring(arrayStart));
            } else {
                samplesJson = llmResult.substring(arrayStart, arrayEnd + 1);
            }

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            samples = mapper.readValue(samplesJson,
                mapper.getTypeFactory().constructCollectionType(List.class, StyleCardAnalysis.StyleSampleItem.class));
        } catch (Exception e) {
            log.warn("提取samples失败: {}", e.getMessage());
        }
        return samples;
    }
    
    /**
     * 查找匹配的括号
     */
    private int findMatchingBracket(String str, int start, char open, char close) {
        int depth = 0;
        for (int i = start; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == open) depth++;
            else if (c == close) {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }
    
    /**
     * 从分析结果填充卡片
     */
    private void fillCardFromAnalysis(String cardId, StyleCardAnalysis analysis, StyleMetrics metrics) {
        log.info("填充卡片数据: cardId={}, samples数量={}, metrics.avgSentenceLength={}", 
                cardId, 
                analysis.getSamples() != null ? analysis.getSamples().size() : 0,
                metrics != null ? metrics.getAvgSentenceLength() : "null");
        
        StyleCard card = new StyleCard();
        card.setCardId(cardId);
        card.setStatus(1); // 可用
        card.setStyleDescription(analysis.getStyleDescription());
        card.setGenerationTemplate(analysis.getGenerationTemplate());
        card.setUpdateTime(LocalDateTime.now());
        
        // 设置结构化规则
        card.setRhythmRules(analysis.getRhythm());
        card.setDialogueRules(analysis.getDialogue());
        card.setDescriptionRules(analysis.getDescription());
        card.setEmotionRules(analysis.getEmotion());
        card.setStructureRules(analysis.getStructure());
        card.setForbiddenRules(analysis.getForbidden());
        card.setPreferredRules(analysis.getPreferred());
        card.setSamplesJson(analysis.getSamples());
        card.setStatsJson(metrics);
        
        styleCardMapper.updateById(card);
        log.info("卡片数据已保存: cardId={}", cardId);
        
        // 存储样本片段
        if (analysis.getSamples() != null && !analysis.getSamples().isEmpty()) {
            log.info("开始存储{}个样本片段", analysis.getSamples().size());
            for (int i = 0; i < analysis.getSamples().size(); i++) {
                StyleCardAnalysis.StyleSampleItem item = analysis.getSamples().get(i);
                StyleSample sample = new StyleSample();
                sample.setSampleId(UUID.randomUUID().toString().replace("-", ""));
                sample.setCardId(cardId);
                sample.setSampleType(item.getType());
                sample.setContent(item.getText());
                sample.setAnnotation(item.getAnnotation());
                sample.setSortOrder(i);
                sample.setCreateTime(LocalDateTime.now());
                styleSampleMapper.insert(sample);
            }
        }
    }
    
    /**
     * 构建系统级风格规则（硬约束）
     *
     * 只包含禁用规则和对话规则，这些是"绝对不能违反"的约束，
     * 应注入到系统消息中。
     *
     * @param cardId 卡片ID
     * @return 系统级风格规则文本
     */
    public String buildSystemStyleRules(String cardId) {
        StyleCard card = styleCardMapper.selectById(cardId);
        if (card == null || card.getStatus() != 1) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (card.getForbiddenRules() != null) {
            String forbidden = objectToJson(card.getForbiddenRules());
            if (!forbidden.equals("{}")) {
                sb.append("\n\n【文风禁用规则——绝对不能出现以下写法】\n");
                sb.append(forbidden);
            }
        }
        if (card.getDialogueRules() != null) {
            String dialogue = objectToJson(card.getDialogueRules());
            if (!dialogue.equals("{}")) {
                sb.append("\n\n【对话风格规则】\n");
                sb.append(dialogue);
            }
        }
        return sb.toString();
    }

    /**
     * 构建用户级风格注入内容（软引导）
     *
     * 只包含风格描述、写作指导、偏好规则、反模式指标和参考片段，
     * 不包含禁用规则和对话规则（这些已移至系统消息）。
     *
     * @param cardId 卡片ID
     * @return 用户级风格注入文本
     */
    public String buildUserStyleSamples(String cardId) {
        StyleCard card = styleCardMapper.selectById(cardId);
        if (card == null || card.getStatus() != 1) {
            return "";
        }

        List<StyleSample> samples = styleSampleMapper.selectByCardId(cardId);

        StringBuilder sb = new StringBuilder();
        sb.append("## 你正在以「").append(card.getName()).append("」的风格写作\n\n");
        sb.append("【重要】以下文风规则是补充约束，必须与系统指令中的写作铁律同时遵守。");
        sb.append("当文风规则与系统指令冲突时，以系统指令为准（系统指令是底线，文风规则是锦上添花）。\n\n");

        // 注入自然语言风格描述
        if (card.getStyleDescription() != null && !card.getStyleDescription().isEmpty()) {
            sb.append("### 风格描述\n").append(card.getStyleDescription()).append("\n\n");
        }

        // 注入生成模板
        if (card.getGenerationTemplate() != null) {
            sb.append("### 写作指导\n").append(card.getGenerationTemplate()).append("\n\n");
        }

        // 注入偏好规则（加分项）
        if (card.getPreferredRules() != null) {
            String preferred = objectToJson(card.getPreferredRules());
            if (!preferred.equals("{}")) {
                sb.append("### 偏好词汇和句式（多多使用）\n").append(preferred).append("\n\n");
            }
        }

        // 注入反模式检测指标
        if (card.getStatsJson() != null && card.getStatsJson() instanceof StyleMetrics) {
            StyleMetrics metrics = (StyleMetrics) card.getStatsJson();
            sb.append("### 反模式检测指标（写作时避免以下问题）\n");
            sb.append("- 句式多样性指数: ").append(String.format("%.2f", metrics.getSentenceDiversityIndex()));
            sb.append("（越低越模板化，建议>0.6）\n");
            sb.append("- 直接切场景比例: ").append(String.format("%.2f", metrics.getDirectCutRatio()));
            sb.append("（偏好直接切场景，避免万能过渡句）\n");
            sb.append("- 万能过渡句比例: ").append(String.format("%.2f", metrics.getTransitionClicheRatio()));
            sb.append("（如'与此同时'、'转眼间'，应避免）\n");
            sb.append("- 章尾悬念比例: ").append(String.format("%.2f", metrics.getSuspenseEndingRatio()));
            sb.append("（章尾应有悬念，避免平淡收尾）\n");
            sb.append("- 对话连续轮数: ").append(metrics.getMaxConsecutiveDialogue());
            sb.append("（最长连续对话无动作插入，建议<5）\n\n");
        }

        // 注入样本片段（限制数量和长度）
        if (!samples.isEmpty()) {
            sb.append("### 风格参考片段\n\n");
            // 最多注入2个样本
            int sampleCount = 0;
            for (StyleSample sample : samples) {
                if (sampleCount >= 2) break;
                sb.append("【").append(sample.getSampleType() != null ? sample.getSampleType() : "参考").append("场景参考】\n");
                // 每个样本截断到200字
                String content = sample.getContent();
                if (content != null && content.length() > 200) {
                    content = content.substring(0, 200) + "...";
                }
                sb.append("```\n").append(content).append("\n```\n");
                if (sample.getAnnotation() != null) {
                    sb.append("风格特征：").append(sample.getAnnotation()).append("\n\n");
                }
                sampleCount++;
            }
        }

        // 整体截断到2000字
        String result = sb.toString();
        if (result.length() > 2000) {
            result = result.substring(0, 2000) + "\n\n[文风卡片内容过长，已截断]";
        }
        return result;
    }

    /**
     * 获取用于写作的风格注入内容
     *
     * @param cardId 卡片ID
     * @return 格式化的风格注入文本
     * @deprecated 使用 {@link #buildSystemStyleRules(String)} 和 {@link #buildUserStyleSamples(String)} 代替
     */
    @Deprecated
    public String buildStyleInjection(String cardId) {
        StyleCard card = styleCardMapper.selectById(cardId);
        if (card == null || card.getStatus() != 1) {
            return "";
        }

        List<StyleSample> samples = styleSampleMapper.selectByCardId(cardId);

        StringBuilder sb = new StringBuilder();
        sb.append("## 你正在以「").append(card.getName()).append("」的风格写作\n\n");
        sb.append("【重要】以下文风规则是补充约束，必须与系统指令中的写作铁律同时遵守。");
        sb.append("当文风规则与系统指令冲突时，以系统指令为准（系统指令是底线，文风规则是锦上添花）。\n\n");

        // 注入自然语言风格描述
        if (card.getStyleDescription() != null && !card.getStyleDescription().isEmpty()) {
            sb.append("### 风格描述\n").append(card.getStyleDescription()).append("\n\n");
        }

        // 注入生成模板
        if (card.getGenerationTemplate() != null) {
            sb.append("### 写作指导\n").append(card.getGenerationTemplate()).append("\n\n");
        }

        // 注入禁用规则（避免踩雷）
        if (card.getForbiddenRules() != null) {
            String forbidden = objectToJson(card.getForbiddenRules());
            if (!forbidden.equals("{}")) {
                sb.append("### 禁用词汇和句式（绝对不能用）\n").append(forbidden).append("\n\n");
            }
        }

        // 注入偏好规则（加分项）
        if (card.getPreferredRules() != null) {
            String preferred = objectToJson(card.getPreferredRules());
            if (!preferred.equals("{}")) {
                sb.append("### 偏好词汇和句式（多多使用）\n").append(preferred).append("\n\n");
            }
        }

        // 注入反模式检测指标
        if (card.getStatsJson() != null && card.getStatsJson() instanceof StyleMetrics) {
            StyleMetrics metrics = (StyleMetrics) card.getStatsJson();
            sb.append("### 反模式检测指标（写作时避免以下问题）\n");
            sb.append("- 句式多样性指数: ").append(String.format("%.2f", metrics.getSentenceDiversityIndex()));
            sb.append("（越低越模板化，建议>0.6）\n");
            sb.append("- 直接切场景比例: ").append(String.format("%.2f", metrics.getDirectCutRatio()));
            sb.append("（偏好直接切场景，避免万能过渡句）\n");
            sb.append("- 万能过渡句比例: ").append(String.format("%.2f", metrics.getTransitionClicheRatio()));
            sb.append("（如'与此同时'、'转眼间'，应避免）\n");
            sb.append("- 章尾悬念比例: ").append(String.format("%.2f", metrics.getSuspenseEndingRatio()));
            sb.append("（章尾应有悬念，避免平淡收尾）\n");
            sb.append("- 对话连续轮数: ").append(metrics.getMaxConsecutiveDialogue());
            sb.append("（最长连续对话无动作插入，建议<5）\n\n");
        }

        // 注入样本片段（限制数量和长度）
        if (!samples.isEmpty()) {
            sb.append("### 风格参考片段\n\n");
            // 最多注入2个样本
            int sampleCount = 0;
            for (StyleSample sample : samples) {
                if (sampleCount >= 2) break;
                sb.append("【").append(sample.getSampleType() != null ? sample.getSampleType() : "参考").append("场景参考】\n");
                // 每个样本截断到200字
                String content = sample.getContent();
                if (content != null && content.length() > 200) {
                    content = content.substring(0, 200) + "...";
                }
                sb.append("```\n").append(content).append("\n```\n");
                if (sample.getAnnotation() != null) {
                    sb.append("风格特征：").append(sample.getAnnotation()).append("\n\n");
                }
                sampleCount++;
            }
        }

        // 整体截断到2000字
        String result = sb.toString();
        if (result.length() > 2000) {
            result = result.substring(0, 2000) + "\n\n[文风卡片内容过长，已截断]";
        }
        return result;
    }

    /**
     * 将对象转为紧凑JSON字符串
     */
    private String objectToJson(Object obj) {
        if (obj == null) return "{}";
        try {
            if (obj instanceof Map) {
                // 过滤掉 null 值
                Map<?, ?> map = (Map<?, ?>) obj;
                Map<String, Object> filtered = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getValue() != null) {
                        filtered.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                }
                return objectMapper.writeValueAsString(filtered);
            }
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
    
    /**
     * 获取卡片详情
     */
    public StyleCard getCardById(String cardId) {
        return styleCardMapper.selectById(cardId);
    }
    
    /**
     * 获取用户的卡片列表
     */
    public List<StyleCard> getUserCards(String userId) {
        return styleCardMapper.selectByCreatedBy(userId);
    }
    
    /**
     * 获取公开的卡片列表
     */
    public List<StyleCard> getPublicCards(int page, int size) {
        return styleCardMapper.selectPublicCards(size, (page - 1) * size);
    }
    
    /**
     * 绑定小说到文风卡片
     */
    @Transactional
    public void bindNovelToCard(String novelId, String cardId) {
        // 先删除旧绑定
        novelStyleBindingMapper.deleteByNovelId(novelId);
        
        // 创建新绑定
        NovelStyleBinding binding = new NovelStyleBinding();
        binding.setId(UUID.randomUUID().toString().replace("-", ""));
        binding.setNovelId(novelId);
        binding.setCardId(cardId);
        binding.setBindTime(LocalDateTime.now());
        novelStyleBindingMapper.insert(binding);
        
        // 增加使用次数
        StyleCard card = styleCardMapper.selectById(cardId);
        if (card != null) {
            card.setUsageCount(card.getUsageCount() + 1);
            styleCardMapper.updateById(card);
        }
    }
    
    /**
     * 获取小说绑定的文风卡片ID
     */
    public String getBoundCardId(String novelId) {
        NovelStyleBinding binding = novelStyleBindingMapper.selectByNovelId(novelId);
        return binding != null ? binding.getCardId() : null;
    }

    /**
     * 解除小说到文风卡片的绑定
     */
    @Transactional
    public void unbindNovel(String novelId) {
        NovelStyleBinding binding = novelStyleBindingMapper.selectByNovelId(novelId);
        if (binding != null) {
            // 减少使用次数
            StyleCard card = styleCardMapper.selectById(binding.getCardId());
            if (card != null && card.getUsageCount() > 0) {
                card.setUsageCount(card.getUsageCount() - 1);
                styleCardMapper.updateById(card);
            }
            novelStyleBindingMapper.deleteByNovelId(novelId);
        }
    }
    
    /**
     * 删除卡片
     */
    @Transactional
    public void deleteCard(String cardId, String userId) {
        // 验证所有权
        StyleCard card = styleCardMapper.selectById(cardId);
        if (card == null || !card.getCreatedBy().equals(userId)) {
            throw new RuntimeException("无权删除此卡片");
        }
        
        // 删除关联数据
        styleSampleMapper.deleteByCardId(cardId);
        userStyleLibraryMapper.delete(
                new LambdaQueryWrapper<UserStyleLibrary>().eq(UserStyleLibrary::getCardId, cardId));
        novelStyleBindingMapper.delete(
                new LambdaQueryWrapper<NovelStyleBinding>().eq(NovelStyleBinding::getCardId, cardId));
        
        // 删除卡片
        styleCardMapper.deleteById(cardId);
    }
    
    /**
     * 更新卡片公开状态
     */
    public void updatePublicStatus(String cardId, String userId, boolean isPublic) {
        StyleCard card = styleCardMapper.selectById(cardId);
        if (card == null || !card.getCreatedBy().equals(userId)) {
            throw new RuntimeException("无权修改此卡片");
        }
        card.setIsPublic(isPublic);
        card.setUpdateTime(LocalDateTime.now());
        styleCardMapper.updateById(card);
    }
    
    /**
     * 截断字符串
     */
    private String truncate(String str, int maxLength) {
        if (str == null) return "";
        str = str.trim();
        return str.length() <= maxLength ? str : str.substring(0, maxLength);
    }

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @SuppressWarnings("unchecked")
    public StyleCardVO toVO(StyleCard card) {
        if (card == null) return null;

        List<StyleCardVO.SampleItem> samples = convertSamples(card.getSamplesJson());

        StyleMetrics metrics = null;
        if (card.getStatsJson() != null) {
            try {
                if (card.getStatsJson() instanceof StyleMetrics) {
                    metrics = (StyleMetrics) card.getStatsJson();
                } else if (card.getStatsJson() instanceof String) {
                    String json = unwrapDoubleEncodedJson(card.getStatsJson());
                    try {
                        metrics = objectMapper.readValue(json, StyleMetrics.class);
                    } catch (Exception e1) {
                        // fallback：先解析为 Map，再 convertValue
                        try {
                            Map<String, Object> map = objectMapper.readValue(json, Map.class);
                            metrics = objectMapper.convertValue(map, StyleMetrics.class);
                        } catch (Exception e2) {
                            log.warn("转换statsJson失败: {}", e1.getMessage());
                        }
                    }
                } else {
                    metrics = objectMapper.convertValue(card.getStatsJson(), StyleMetrics.class);
                }
            } catch (Exception e) {
                log.warn("转换statsJson失败: {}", e.getMessage());
            }
        }

        Map<String, Object> rhythmRules = toMap(card.getRhythmRules());
        Map<String, Object> dialogueRules = toMap(card.getDialogueRules());
        Map<String, Object> descriptionRules = toMap(card.getDescriptionRules());
        Map<String, Object> emotionRules = toMap(card.getEmotionRules());
        Map<String, Object> structureRules = toMap(card.getStructureRules());
        Map<String, Object> forbiddenRules = toMap(card.getForbiddenRules());
        Map<String, Object> preferredRules = toMap(card.getPreferredRules());

        return StyleCardVO.builder()
                .cardId(card.getCardId())
                .name(card.getName())
                .sourceNovel(card.getSourceNovel())
                .authorName(card.getAuthorName())
                .createdBy(card.getCreatedBy())
                .rhythmRules(rhythmRules)
                .dialogueRules(dialogueRules)
                .descriptionRules(descriptionRules)
                .emotionRules(emotionRules)
                .structureRules(structureRules)
                .forbiddenRules(forbiddenRules)
                .preferredRules(preferredRules)
                .styleDescription(card.getStyleDescription())
                .generationTemplate(card.getGenerationTemplate())
                .samplesJson(samples)
                .statsJson(metrics)
                .tags(card.getTags())
                .isPublic(card.getIsPublic() != null && card.getIsPublic())
                .usageCount(card.getUsageCount() != null ? card.getUsageCount() : 0)
                .avgRating(card.getAvgRating())
                .status(card.getStatus() != null ? card.getStatus() : 0)
                .createTime(card.getCreateTime())
                .updateTime(card.getUpdateTime())
                .build();
    }

    public List<StyleCardVO> toVOList(List<StyleCard> cards) {
        if (cards == null) return Collections.emptyList();
        return cards.stream().map(this::toVO).collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private List<StyleCardVO.SampleItem> convertSamples(Object samplesJson) {
        if (samplesJson == null) return Collections.emptyList();

        try {
            Object parsed = samplesJson;
            if (samplesJson instanceof String) {
                String json = unwrapDoubleEncodedJson(samplesJson);
                try {
                    parsed = objectMapper.readValue(json, List.class);
                } catch (Exception e1) {
                    // fallback：先解析为 Object，再 convertValue
                    try {
                        Object raw = objectMapper.readValue(json, Object.class);
                        parsed = objectMapper.convertValue(raw, List.class);
                    } catch (Exception e2) {
                        log.warn("转换samplesJson失败: {}", e1.getMessage());
                        return Collections.emptyList();
                    }
                }
            }
            if (parsed instanceof List) {
                List<?> list = (List<?>) parsed;
                List<StyleCardVO.SampleItem> result = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof StyleCardVO.SampleItem) {
                        result.add((StyleCardVO.SampleItem) item);
                    } else if (item instanceof Map) {
                        Map<String, Object> map = (Map<String, Object>) item;
                        String type = getStr(map, "type", "sample_type");
                        String text = getStr(map, "text", "content");
                        String annotation = getStr(map, "annotation", "annotation");
                        result.add(StyleCardVO.SampleItem.builder()
                                .type(type)
                                .text(text)
                                .annotation(annotation)
                                .build());
                    }
                }
                return result;
            }
            List<Map<String, Object>> list = objectMapper.convertValue(parsed, List.class);
            return list.stream().map(map -> StyleCardVO.SampleItem.builder()
                    .type(getStr(map, "type", "sample_type"))
                    .text(getStr(map, "text", "content"))
                    .annotation(getStr(map, "annotation", "annotation"))
                    .build()).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("转换samplesJson失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    private String getStr(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object val = map.get(key);
            if (val != null) return val.toString();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    /**
     * 处理可能被双重编码的 JSON 字符串。
     * 情况1：以"开头、以"结尾 → Jackson 解析去掉一层引号。
     * 情况2：含 \" 转义（可能无外层引号） → 循环反转义直到合法 JSON。
     * 首字符检测：非 { [ " 的字符串不可能是 JSON，直接返回。
     */
    private String unwrapDoubleEncodedJson(Object input) {
        if (!(input instanceof String)) return null;
        String str = ((String) input).trim();
        if (str.isEmpty()) return str;

        // 情况1：外层有引号 "..." → Jackson 解析去掉一层
        if (str.length() >= 2 && str.startsWith("\"") && str.endsWith("\"")) {
            try {
                return objectMapper.readValue(str, String.class);
            } catch (Exception e) {
                return str;
            }
        }

        // 情况2：含 \" 转义 → 循环反转义直到合法 JSON
        if (str.contains("\\\"")) {
            String candidate = str;
            String prev = null;
            while (!candidate.equals(prev)) {
                prev = candidate;
                candidate = candidate.replace("\\\"", "\"");
                try {
                    objectMapper.readValue(candidate, Object.class);
                    return candidate;
                } catch (Exception ignored) {
                }
            }
            return str;
        }

        return str;
    }

    private Map<String, Object> toMap(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Map) return (Map<String, Object>) obj;
        try {
            if (obj instanceof String) {
                String json = unwrapDoubleEncodedJson(obj);
                return objectMapper.readValue(json, Map.class);
            }
            return objectMapper.convertValue(obj, Map.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 修复数据库中被双重编码的 JSON 字段。
     * 使用 raw SQL 绕过 JacksonTypeHandler，去掉外层引号。
     * 去掉后仍含 \" 转义的值由读取时的 unwrapDoubleEncodedJson 处理。
     */
    @Transactional
    public Map<String, Object> fixDoubleEncodedCards() {
        List<String> allIds = styleCardMapper.selectAllCardIds();
        int total = allIds.size();
        int fixed = 0;

        for (String cardId : allIds) {
            // 先检查是否有需要修复的字段（以"开头的值）
            String samplesRaw = styleCardMapper.selectSamplesJsonRaw(cardId);
            String statsRaw = styleCardMapper.selectStatsJsonRaw(cardId);
            boolean needsFix = (samplesRaw != null && samplesRaw.startsWith("\""))
                    || (statsRaw != null && statsRaw.startsWith("\""));

            styleCardMapper.fixDoubleEncodedJson(cardId);

            if (needsFix) {
                fixed++;
                log.info("修复卡片: cardId={}", cardId);
            }
        }

        log.info("双重编码修复完成: 总卡片数={}, 修复卡片数={}", total, fixed);
        return Map.of("total", total, "fixed", fixed);
    }
}
