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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
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
    
    /**
     * 从上传的小说文本创建文风卡片
     * 
     * @param userId 用户ID
     * @param novelName 小说名称
     * @param fullText 完整文本
     * @return 创建的卡片（状态=分析中）
     */
    @Transactional
    public StyleCard createFromNovel(String userId, String novelName, String fullText) {
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
        
        // 3. 异步分析
        CompletableFuture.runAsync(() -> {
            try {
                analyzeAndFill(card.getCardId(), novelName, fullText);
            } catch (Exception e) {
                log.error("风格分析失败: cardId={}", card.getCardId(), e);
                // 更新状态为失败
                StyleCard failedCard = new StyleCard();
                failedCard.setCardId(card.getCardId());
                failedCard.setStatus(2); // 分析失败
                styleCardMapper.updateById(failedCard);
            }
        });
        
        return card;
    }
    
    /**
     * 分析核心逻辑
     */
    private void analyzeAndFill(String cardId, String novelName, String text) {
        log.info("开始分析文风: cardId={}, novelName={}", cardId, novelName);
        
        // Step 1: 程序化提取量化指标
        StyleMetrics metrics = textAnalyzer.analyze(text);
        log.info("程序化分析完成: avgSentenceLength={}, dialogueRatio={}", 
                metrics.getAvgSentenceLength(), metrics.getDialogueRatio());
        
        // Step 2: 采样文本片段（前3章+中间3章+后3章）
        List<String> sampledTexts = sampleChapters(text, 9, 500);
        String combinedSample = String.join("\n---\n", sampledTexts);
        log.info("采样完成: 共{}个片段", sampledTexts.size());
        
        // Step 3: 调用LLM分析
        String analysisPrompt = buildAnalysisPrompt(novelName, metrics, combinedSample);
        String analysisResult = llmService.generate(analysisPrompt, "audit");
        
        // Step 4: 解析结果并存储
        StyleCardAnalysis result = parseAnalysisResult(analysisResult);
        fillCardFromAnalysis(cardId, result, metrics);
        
        log.info("文风分析完成: cardId={}", cardId);
    }
    
    /**
     * 从文本中采样章节片段
     * 
     * @param text 完整文本
     * @param sampleCount 采样数量
     * @param sampleLength 每个采样的长度（字）
     * @return 采样的文本片段
     */
    private List<String> sampleChapters(String text, int sampleCount, int sampleLength) {
        List<String> samples = new ArrayList<>();
        
        // 按章节分割
        String[] chapters = text.split("(?=第[一二三四五六七八九十百千\\d]+章)");
        
        if (chapters.length < 3) {
            // 如果没有明确的章节分割，按长度均匀采样
            int partLength = text.length() / sampleCount;
            for (int i = 0; i < sampleCount && i * partLength < text.length(); i++) {
                int start = i * partLength;
                int end = Math.min(start + sampleLength, text.length());
                samples.add(text.substring(start, end));
            }
        } else {
            int totalChapters = chapters.length;
            
            // 自适应采样：根据总章节数调整
            int actualSampleCount;
            int actualSampleLength;
            
            if (totalChapters <= 10) {
                // 短篇：全采
                actualSampleCount = totalChapters;
                actualSampleLength = 1500;
            } else if (totalChapters <= 50) {
                // 中篇：采10-15章
                actualSampleCount = Math.min(15, totalChapters / 3);
                actualSampleLength = 2000;
            } else if (totalChapters <= 200) {
                // 长篇：采20章
                actualSampleCount = 20;
                actualSampleLength = 2000;
            } else {
                // 超长篇：采30章
                actualSampleCount = 30;
                actualSampleLength = 2000;
            }
            
            // 均匀分布采样索引
            List<Integer> indices = new ArrayList<>();
            
            // 开头部分（前10%）：采20%
            int startEnd = Math.max(1, totalChapters / 10);
            for (int i = 0; i < Math.min(3, startEnd); i++) {
                indices.add(i);
            }
            
            // 中间部分（10%-90%）：采60%
            int midStart = startEnd;
            int midEnd = totalChapters - startEnd;
            int midCount = (int) (actualSampleCount * 0.6);
            for (int i = 0; i < midCount; i++) {
                int idx = midStart + (int) ((double) i / midCount * (midEnd - midStart));
                if (!indices.contains(idx)) {
                    indices.add(idx);
                }
            }
            
            // 结尾部分（最后10%）：采20%
            for (int i = Math.max(0, totalChapters - 3); i < totalChapters; i++) {
                if (!indices.contains(i)) {
                    indices.add(i);
                }
            }
            
            // 去重并排序
            indices = indices.stream().distinct().sorted().collect(Collectors.toList());
            
            // 限制在actualSampleCount以内
            if (indices.size() > actualSampleCount) {
                // 均匀保留
                List<Integer> filtered = new ArrayList<>();
                double step = (double) indices.size() / actualSampleCount;
                for (int i = 0; i < actualSampleCount; i++) {
                    filtered.add(indices.get((int) (i * step)));
                }
                indices = filtered;
            }
            
            // 提取样本
            for (int idx : indices) {
                if (idx >= 0 && idx < chapters.length) {
                    String chapter = chapters[idx].trim();
                    if (chapter.length() > 100) {
                        samples.add(truncate(chapter, actualSampleLength));
                    }
                }
            }
            
            log.info("自适应采样: 总章节数={}, 采样章节数={}, 每章字数={}", 
                    totalChapters, samples.size(), actualSampleLength);
        }
        
        return samples;
    }
    
    /**
     * 构建LLM分析提示词
     */
    private String buildAnalysisPrompt(String novelName, StyleMetrics metrics, String samples) {
        return """
                你是一位资深文学评论家和写作风格分析师。
                
                请分析小说《%s》的写作风格。
                
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
                2. samples必须是原文真实片段（200-300字），不要自己编
                3. forbidden要具体——列出这个作者真正不会用的词和句式
                4. generation_template要可执行——拿到就能直接用来指导写作
                5. 只返回JSON，不要有其他内容
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
                  "visual_density": %.2f,
                  "auditory_density": %.2f,
                  "tactile_density": %.2f,
                  "distinctive_words": %s
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
                metrics.getVisualDensity(),
                metrics.getAuditoryDensity(),
                metrics.getTactileDensity(),
                metrics.getDistinctiveWords() != null ? metrics.getDistinctiveWords() : "[]"
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
                json = result.substring(result.indexOf("{"), result.lastIndexOf("}") + 1);
            }
            
            // 使用Jackson解析，配置忽略未知字段
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            return mapper.readValue(json, StyleCardAnalysis.class);
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
            if (arrayEnd == -1) return samples;
            
            String samplesJson = llmResult.substring(arrayStart, arrayEnd + 1);
            
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
     * 获取用于写作的风格注入内容
     * 
     * @param cardId 卡片ID
     * @return 格式化的风格注入文本
     */
    public String buildStyleInjection(String cardId) {
        StyleCard card = styleCardMapper.selectById(cardId);
        if (card == null || card.getStatus() != 1) {
            return "";
        }

        List<StyleSample> samples = styleSampleMapper.selectByCardId(cardId);

        StringBuilder sb = new StringBuilder();
        sb.append("## 你正在以「").append(card.getName()).append("」的风格写作\n\n");

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

        // 注入样本片段
        if (!samples.isEmpty()) {
            sb.append("### 风格参考片段\n\n");
            for (StyleSample sample : samples) {
                sb.append("【").append(sample.getSampleType() != null ? sample.getSampleType() : "参考").append("场景参考】\n");
                sb.append("```\n").append(sample.getContent()).append("\n```\n");
                if (sample.getAnnotation() != null) {
                    sb.append("风格特征：").append(sample.getAnnotation()).append("\n\n");
                }
            }
        }

        return sb.toString();
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
            if (samplesJson instanceof List) {
                List<?> list = (List<?>) samplesJson;
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
            List<Map<String, Object>> list = objectMapper.convertValue(samplesJson, List.class);
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
    private Map<String, Object> toMap(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Map) return (Map<String, Object>) obj;
        try {
            return objectMapper.convertValue(obj, Map.class);
        } catch (Exception e) {
            return null;
        }
    }
}
