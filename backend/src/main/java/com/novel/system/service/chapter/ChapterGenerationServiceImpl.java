package com.novel.system.service.chapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.system.entity.mysql.ClueBook;
import com.novel.system.entity.mysql.NovelArc;
import com.novel.system.entity.mysql.NovelBase;
import com.novel.system.entity.mysql.NovelChapter;
import com.novel.system.entity.mysql.NovelStyleBinding;
import com.novel.system.entity.mysql.WorldClock;
import com.novel.system.entity.mongo.CharacterState;
import com.novel.system.entity.mongo.ChapterSummary;
import com.novel.system.mapper.NovelArcMapper;
import com.novel.system.mapper.NovelBaseMapper;
import com.novel.system.mapper.NovelChapterMapper;
import com.novel.system.mapper.NovelStyleBindingMapper;
import com.novel.system.mapper.WorldClockMapper;
import com.novel.system.service.chapter.ChapterGenerationRequest;
import com.novel.system.service.chapter.ChapterGenerationResponse;
import com.novel.system.service.chapter.ChapterGenerationService;
import com.novel.system.service.character.CharacterStateService;
import com.novel.system.service.clue.ClueBookService;
import com.novel.system.ai.agent.OutlineAgentResponse;
import com.novel.system.ai.agent.SummaryAgentResponse;
import com.novel.system.ai.rag.RagService;
import com.novel.system.ai.chain.ValidationRequest;
import com.novel.system.ai.chain.ValidationResult;
import com.novel.system.ai.chain.ValidationService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.novel.system.dto.vo.GenerationContext;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 章节生成服务实现 - 核心工作流
 * 使用Redis缓存生成上下文
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChapterGenerationServiceImpl implements ChapterGenerationService {
    
    private final NovelBaseMapper novelBaseMapper;
    private final NovelChapterMapper novelChapterMapper;
    private final NovelArcMapper novelArcMapper;
    private final NovelStyleBindingMapper novelStyleBindingMapper;
    private final WorldClockMapper worldClockMapper;
    private final CharacterStateService characterStateService;
    private final ClueBookService clueBookService;
    private final RagService ragService;
    private final ValidationService validationService;
    private final StringRedisTemplate redisTemplate;
    private final ContextBuilderService contextBuilderService;
    private final ChapterVersionService chapterVersionService;
    private final com.novel.system.repository.mongo.NovelGraphRepository novelGraphRepository;
    private final ObjectMapper objectMapper;
    private final com.novel.system.ai.chain.ChapterGenerationChain chapterGenerationChain;
    private final ChapterSaveService chapterSaveService;
    
    private static final String CONTEXT_CACHE_PREFIX = "novel:context:";
    private static final long CONTEXT_CACHE_TTL_MINUTES = 30;
    
    @Override
    public ChapterGenerationResponse generateOutline(ChapterGenerationRequest request) {
        /**
         * 生成章纲
         * 
         * 【执行流程】
         * 1. 获取小说信息（验证小说存在）
         * 2. 获取卷信息（当前章节属于哪一卷）
         * 3. 获取世界状态（时间、季节、天气）
         * 4. 获取人物状态（所有人物的当前状态）
         * 5. 获取未回收伏笔（需要在后续章节回收的伏笔）
         * 6. 获取上一章摘要（保持剧情连贯）
         * 7. 获取小说世界观（力量体系、地理环境等）
         * 8. 组装上下文信息（供Chain使用）
         * 9. 调用ChapterGenerationChain执行章纲生成
         * 10. 解析返回的JSON（提取outline和ideas）
         * 11. 缓存生成上下文到Redis（供后续生成正文使用）
         * 12. 构建响应返回
         * 
         * 【上下文信息的作用】
         * Agent需要这些信息才能生成符合要求的内容：
         * - 总纲：确保生成的章纲符合整体剧情
         * - 卷纲：确保符合当前卷的剧情规划
         * - 人物状态：确保人物行为一致
         * - 世界状态：确保时间、环境逻辑正确
         * - 伏笔：确保伏笔得到合理安排
         * - 上一章：确保剧情连贯
         */
        log.info("开始生成章纲，小说ID: {}, 章节号: {}", request.getNovelId(), request.getChapterNum());
        
        try {
            // 1. 获取小说信息
            NovelBase novel = novelBaseMapper.selectById(request.getNovelId());
            if (novel == null) {
                throw new RuntimeException("小说不存在");
            }
            
            // 2. 获取卷信息
            ArcInfo arcInfo = getArcInfo(request.getNovelId(), request.getChapterNum());
            
            // 3. 获取世界状态
            WorldClockInfo worldClockInfo = getWorldClockInfo(request.getNovelId());
            
            // 4. 获取人物状态
            String characterStates = getCharacterStates(request.getNovelId());
            
            // 5. 获取未回收伏笔
            List<String> unrecycledClues = getUnrecycledClues(request.getNovelId());
            
            // 6. 获取上一章摘要
            String lastFingerprint = getLastChapterFingerprint(request.getNovelId(), request.getChapterNum());
            
            // 7. 获取小说世界观
            String worldSetting = novel.getWorldSetting() != null ? novel.getWorldSetting() : "世界观待配置";
            
            // 8. 组装上下文信息供 Chain 使用
            String contextPrompt = contextBuilderService.buildOutlineContext(
                request.getNovelId(), request.getChapterNum(), novel, arcInfo);
            
            // 9. 调用 ChapterGenerationChain（LangChain4j AI Services + @Tool）
            String outlineJson = chapterGenerationChain.executeOutlineChain(
                request.getNovelId(), request.getChapterNum(), contextPrompt, request.getLlmConfigId());
            OutlineAgentResponse outlineResponse = parseOutlineJson(outlineJson);
            
            // 10. 缓存生成上下文到Redis
            GenerationContext context = new GenerationContext();
            context.setNovelId(novel.getNovelId());
            context.setGenre(novel.getGenre());
            context.setTotalOutline(novel.getTotalOutline());
            context.setArcOutline(arcInfo.getArcOutline());
            context.setArcName(arcInfo.getArcName());
            context.setWorldSetting(worldSetting);
            context.setCharacterStates(characterStates);
            context.setUnrecycledClues(unrecycledClues);
            context.setOutline(outlineResponse.getOutline());
            context.setImplementationIdeas(outlineResponse.getImplementationIdeas());
            context.setLastChapterFingerprint(lastFingerprint);
            
            String cacheKey = CONTEXT_CACHE_PREFIX + generateCacheKey(request.getNovelId(), request.getChapterNum());
            String contextJson = objectMapper.writeValueAsString(context);
            redisTemplate.opsForValue().set(cacheKey, contextJson, CONTEXT_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            
            // 11. 构建响应
            ChapterGenerationResponse response = new ChapterGenerationResponse();
            response.setSuccess(true);
            response.setStatus(ChapterGenerationResponse.GenerationStatus.OUTLINE_GENERATED);
            response.setChapterOutline(outlineResponse.getOutline());
            response.setImplementationIdeas(outlineResponse.getImplementationIdeas());
            
            log.info("章纲生成完成，小说ID: {}, 章节号: {}", request.getNovelId(), request.getChapterNum());
            
            return response;
            
        } catch (Exception e) {
            log.error("章纲生成失败", e);
            ChapterGenerationResponse response = new ChapterGenerationResponse();
            response.setSuccess(false);
            response.setStatus(ChapterGenerationResponse.GenerationStatus.FAILED);
            response.setErrorMessage("章纲生成失败: " + e.getMessage());
            return response;
        }
    }
    
    @Override
    public ChapterGenerationResponse generateChapter(ChapterGenerationRequest request) {
        log.info("开始生成章节，小说ID: {}, 章节号: {}", request.getNovelId(), request.getChapterNum());
        
        try {
            // 1. 从Redis获取缓存的上下文
            String cacheKey = CONTEXT_CACHE_PREFIX + generateCacheKey(request.getNovelId(), request.getChapterNum());
            String contextJson = redisTemplate.opsForValue().get(cacheKey);
            
            GenerationContext context = null;
            if (contextJson != null) {
                try {
                    context = objectMapper.readValue(contextJson, GenerationContext.class);
                } catch (JsonProcessingException e) {
                    log.warn("解析缓存上下文失败: {}", e.getMessage());
                }
            }
            
            if (context == null) {
                // 如果没有缓存，先生成章纲
                ChapterGenerationResponse outlineResponse = generateOutline(request);
                if (!outlineResponse.getSuccess()) {
                    return outlineResponse;
                }
                contextJson = redisTemplate.opsForValue().get(cacheKey);
                if (contextJson != null) {
                    context = objectMapper.readValue(contextJson, GenerationContext.class);
                } else {
                    throw new RuntimeException("缓存写入失败，无法获取生成上下文");
                }
            }
            
            // 2. 获取选定的实现思路
            String selectedIdea = null;
            if (request.getSelectedIdeaIndex() != null && context.getImplementationIdeas() != null) {
                int index = request.getSelectedIdeaIndex();
                if (index >= 0 && index < context.getImplementationIdeas().size()) {
                    selectedIdea = context.getImplementationIdeas().get(index);
                }
            }
            if (selectedIdea == null && context.getImplementationIdeas() != null 
                && !context.getImplementationIdeas().isEmpty()) {
                selectedIdea = context.getImplementationIdeas().get(0);
            }
            
            // 3. 组装上下文
            String assembledContext = ragService.assembleContext(
                request.getNovelId(),
                request.getChapterNum(),
                context.getTotalOutline(),
                context.getArcOutline(),
                context.getCharacterStates(),
                formatClues(context.getUnrecycledClues())
            );
            
            // 4. 调用 ChapterGenerationChain 写作链
            // 4.1 保存现有章节的版本快照（如果是重新生成）
            NovelChapter existingChapter = novelChapterMapper.selectByNovelIdAndChapterNum(
                request.getNovelId(), request.getChapterNum());
            if (existingChapter != null) {
                chapterVersionService.saveSnapshot(existingChapter);
            }
            
            // 4.2 获取小说信息用于构建写作上下文
            NovelBase novelInfo = novelBaseMapper.selectById(request.getNovelId());
            NovelChapter tempChapter = new NovelChapter();
            tempChapter.setChapterOutline(context.getOutline());
            
            String writingContext = contextBuilderService.buildWritingContext(
                context.getNovelId(), request.getChapterNum(), tempChapter, novelInfo, selectedIdea);

            // 查询文风卡片ID：优先使用请求中指定的，其次查询小说绑定的
            String effectiveStyleCardId = request.getStyleCardId();
            if (effectiveStyleCardId == null || effectiveStyleCardId.isEmpty()) {
                NovelStyleBinding binding = novelStyleBindingMapper.selectByNovelId(request.getNovelId());
                if (binding != null && binding.getCardId() != null) {
                    effectiveStyleCardId = binding.getCardId();
                    log.info("使用小说绑定的文风卡片: {}", effectiveStyleCardId);
                }
            }

            String chapterContent;
            if (effectiveStyleCardId != null && !effectiveStyleCardId.isEmpty()) {
                chapterContent = chapterGenerationChain.executeWritingChain(
                    request.getNovelId(), request.getChapterNum(),
                    context.getOutline(), writingContext, effectiveStyleCardId, request.getLlmConfigId());
            } else {
                chapterContent = chapterGenerationChain.executeWritingChain(
                    request.getNovelId(), request.getChapterNum(),
                    context.getOutline(), writingContext, request.getLlmConfigId());
            }
            
            // 5. 多重校验
            ValidationResult validationResult = validateContent(request, context, chapterContent);
            
            // 6. 如果校验失败，重试（最多2次，将失败原因注入上下文）
            int retryCount = 0;
            while (!validationResult.getPassed() && retryCount < 2) {
                log.warn("校验未通过，重试第{}次，原因: {}", retryCount + 1, validationResult.getSummary());
                String retryContext = writingContext + "\n\n[校验反馈] 上次生成未通过校验，原因: " 
                    + (validationResult.getSummary() != null ? validationResult.getSummary() : "未知")
                    + "。请针对性修改这些问题。";
                if (effectiveStyleCardId != null && !effectiveStyleCardId.isEmpty()) {
                    chapterContent = chapterGenerationChain.executeWritingChain(
                        request.getNovelId(), request.getChapterNum(),
                        context.getOutline(), retryContext, effectiveStyleCardId, request.getLlmConfigId());
                } else {
                    chapterContent = chapterGenerationChain.executeWritingChain(
                        request.getNovelId(), request.getChapterNum(),
                        context.getOutline(), retryContext, request.getLlmConfigId());
                }
                validationResult = validateContent(request, context, chapterContent);
                retryCount++;
            }
            
            // 7. 构建临时实体并生成摘要
            NovelChapter chapter = createChapterEntity(request, context, chapterContent);
            ChapterSummary summary = generateSummary(request, context, chapter);
            
            // 8. 事务保存所有结果（通过self调用，保证Spring AOP事务生效）
            chapterSaveService.saveGenerationResult(
                request.getNovelId(), request.getChapterNum(), chapter.getChapterTitle(),
                context.getOutline(), chapterContent,
                chapterContent.replaceAll("\\s+", "").length(),
                request.getUserIntent(), summary.getContent(), summary);
            
            // 10. 清除Redis缓存
            redisTemplate.delete(cacheKey);
            
            // 11. 构建响应
            ChapterGenerationResponse response = new ChapterGenerationResponse();
            response.setSuccess(true);
            response.setStatus(ChapterGenerationResponse.GenerationStatus.COMPLETED);
            response.setChapterId(chapter.getChapterId());
            response.setChapterTitle(chapter.getChapterTitle());
            response.setContent(chapter.getContent());
            response.setWordCount(chapter.getWordCount());
            response.setPlotFingerprint(summary.getContent());
            response.setValidationSummary(validationResult.getSummary());
            
            log.info("章节生成完成，小说ID: {}, 章节号: {}, 字数: {}", 
                request.getNovelId(), request.getChapterNum(), chapter.getWordCount());
            
            return response;
            
        } catch (Exception e) {
            log.error("章节生成失败", e);
            ChapterGenerationResponse response = new ChapterGenerationResponse();
            response.setSuccess(false);
            response.setStatus(ChapterGenerationResponse.GenerationStatus.FAILED);
            response.setErrorMessage("章节生成失败: " + e.getMessage());
            return response;
        }
    }
    
    @Override
    public ChapterGenerationResponse regenerateChapter(ChapterGenerationRequest request) {
        log.info("重新生成章节，小说ID: {}, 章节号: {}", request.getNovelId(), request.getChapterNum());
        return generateChapter(request);
    }
    
    @Override
    public String generateChapterAsync(ChapterGenerationRequest request) {
        String taskId = UUID.randomUUID().toString();
        log.info("启动异步章节生成任务，任务ID: {}", taskId);
        
        // 立即更新任务状态
        updateTaskStatus(taskId, "GENERATING", "章节生成中...");
        
        // 异步执行，不阻塞主线程
        CompletableFuture.runAsync(() -> {
            try {
                ChapterGenerationResponse response = generateChapter(request);
                updateTaskStatus(taskId, response);
                log.info("异步章节生成完成，任务ID: {}", taskId);
            } catch (Exception e) {
                log.error("异步章节生成失败，任务ID: " + taskId, e);
                updateTaskStatus(taskId, "FAILED", "章节生成失败: " + e.getMessage());
            }
        });
        
        return taskId;
    }
    
    /**
     * 更新任务状态到Redis
     */
    private void updateTaskStatus(String taskId, String status, String message) {
        try {
            String statusKey = "novel:task:status:" + taskId;
            ChapterGenerationResponse response = new ChapterGenerationResponse();
            response.setSuccess("COMPLETED".equals(status));
            response.setStatus(ChapterGenerationResponse.GenerationStatus.valueOf(status));
            response.setErrorMessage(message);
            redisTemplate.opsForValue().set(statusKey, objectMapper.writeValueAsString(response), 24, TimeUnit.HOURS);
        } catch (Exception e) {
            log.error("保存任务状态到Redis失败", e);
        }
    }
    
    private void updateTaskStatus(String taskId, ChapterGenerationResponse response) {
        try {
            String statusKey = "novel:task:status:" + taskId;
            redisTemplate.opsForValue().set(statusKey, objectMapper.writeValueAsString(response), 24, TimeUnit.HOURS);
        } catch (Exception e) {
            log.error("保存任务状态到Redis失败", e);
        }
    }
    
    @Override
    public ChapterGenerationResponse getGenerationStatus(String taskId) {
        String statusKey = "novel:task:status:" + taskId;
        String statusJson = redisTemplate.opsForValue().get(statusKey);
        
        if (statusJson != null) {
            try {
                return objectMapper.readValue(statusJson, ChapterGenerationResponse.class);
            } catch (JsonProcessingException e) {
                log.error("解析任务状态失败", e);
            }
        }
        
        ChapterGenerationResponse response = new ChapterGenerationResponse();
        response.setSuccess(false);
        response.setStatus(ChapterGenerationResponse.GenerationStatus.FAILED);
        response.setErrorMessage("任务不存在或已过期");
        return response;
    }
    
    @Override
    public SummaryAgentResponse extractSummaryOnly(ChapterGenerationRequest request, String content) {
        NovelBase novel = novelBaseMapper.selectById(request.getNovelId());
        if (novel == null) {
            throw new RuntimeException("小说不存在");
        }
        
        // 获取章纲（如果有）
        String outline = null;
        String cacheKey = CONTEXT_CACHE_PREFIX + generateCacheKey(request.getNovelId(), request.getChapterNum());
        String contextJson = redisTemplate.opsForValue().get(cacheKey);
        if (contextJson != null) {
            try {
                GenerationContext context = objectMapper.readValue(contextJson, GenerationContext.class);
                outline = context.getOutline();
            } catch (Exception ignored) {}
        }
        
        // 调用 ChapterGenerationChain 摘要链
        String summaryJson = chapterGenerationChain.executeSummaryChain(
            request.getNovelId(), request.getChapterNum(), content, outline, null);
        
        return parseSummaryJson(summaryJson);
    }
    
    @Override
    public ChapterGenerationResponse saveManualChapter(ChapterGenerationRequest request, String content, SummaryAgentResponse summaryResponse) {
        log.info("保存手动章节，小说ID: {}, 章节号: {}", request.getNovelId(), request.getChapterNum());
        
        NovelChapter chapter = new NovelChapter();
        chapter.setNovelId(request.getNovelId());
        chapter.setChapterNum(request.getChapterNum());
        chapter.setChapterTitle(request.getChapterTitle() != null && !request.getChapterTitle().isEmpty()
                ? request.getChapterTitle()
                : "第" + request.getChapterNum() + "章");
        chapter.setContent(content);
        
        // 计算字数（统计所有非空白字符，符合网文行业标准）
        int wordCount = content.replaceAll("\\s+", "").length();
        chapter.setWordCount(wordCount);
        chapter.setGenerationStatus(2); // 已生成
        chapter.setUserIntent(request.getUserIntent() != null ? request.getUserIntent() : "手动撰写");
        
        // 处理summaryResponse可能为null的情况
        String fingerprint = "手动撰写的章节";
        if (summaryResponse != null && summaryResponse.getFingerprint() != null) {
            fingerprint = summaryResponse.getFingerprint();
        }
        chapter.setPlotFingerprint(fingerprint);
        
        // 构建并保存摘要
        ChapterSummary summary = new ChapterSummary();
        summary.setNovelId(request.getNovelId());
        summary.setChapterNum(request.getChapterNum());
        summary.setContent(fingerprint);
        
        // 处理summaryResponse可能为null的情况
        if (summaryResponse != null) {
            summary.setKeywords(summaryResponse.getKeywords());
            summary.setCharacters(summaryResponse.getCharacters());
            summary.setLocations(summaryResponse.getLocations());
            summary.setRelations(summaryResponse.getRelations());
        }
        summary.setPriority(2);
        
        chapterSaveService.saveGenerationResult(
            request.getNovelId(), request.getChapterNum(), chapter.getChapterTitle(),
            "手动撰写", content, wordCount,
            request.getUserIntent() != null ? request.getUserIntent() : "手动撰写",
            fingerprint, summary);
        
        // 清除可能存在的缓存
        String cacheKey = CONTEXT_CACHE_PREFIX + generateCacheKey(request.getNovelId(), request.getChapterNum());
        redisTemplate.delete(cacheKey);
        
        ChapterGenerationResponse response = new ChapterGenerationResponse();
        response.setSuccess(true);
        response.setStatus(ChapterGenerationResponse.GenerationStatus.COMPLETED);
        response.setChapterId(chapter.getChapterId());
        response.setChapterTitle(chapter.getChapterTitle());
        response.setContent(chapter.getContent());
        response.setWordCount(chapter.getWordCount());
        response.setPlotFingerprint(fingerprint);
        return response;
    }
    
    // ========== 辅助方法 ==========
    
    private String generateCacheKey(String novelId, Integer chapterNum) {
        return novelId + ":" + chapterNum;
    }
    
    /**
     * 获取卷信息 - 根据章节号查找所属卷
     */
    private ArcInfo getArcInfo(String novelId, Integer chapterNum) {
        ArcInfo info = new ArcInfo();
        
        NovelArc arc = novelArcMapper.findByChapterNum(novelId, chapterNum);
        
        if (arc != null) {
            info.setArcName(arc.getArcName() != null ? arc.getArcName() : "未命名卷");
            info.setArcOutline(arc.getArcOutline() != null ? arc.getArcOutline() : "卷纲待配置");
            info.setStartChapter(arc.getStartChapter());
            info.setEndChapter(arc.getEndChapter());
        } else {
            info.setArcName("第一卷");
            info.setArcOutline("卷纲待配置，请在小说详情页设置卷纲");
            info.setStartChapter(1);
            info.setEndChapter(100);
        }
        
        return info;
    }
    
    /**
     * 获取世界状态信息
     */
    private WorldClockInfo getWorldClockInfo(String novelId) {
        WorldClockInfo info = new WorldClockInfo();
        
        WorldClock clock = worldClockMapper.selectByNovelId(novelId);
        if (clock != null) {
            info.setTime(clock.getCurrentTime() != null ? clock.getCurrentTime() : "未设定");
            info.setSeason(clock.getSeason() != null ? clock.getSeason() : "未设定");
            info.setWeather(clock.getWeather() != null ? clock.getWeather() : "未设定");
            info.setTotalDays(clock.getTotalDays() != null ? clock.getTotalDays() : 0);
        } else {
            info.setTime("未设定");
            info.setSeason("未设定");
            info.setWeather("未设定");
            info.setTotalDays(0);
        }
        
        return info;
    }
    
    private String getCharacterStates(String novelId) {
        List<CharacterState> characters = characterStateService.getCharactersByNovelId(novelId);
        if (characters.isEmpty()) {
            return "暂无人物设定";
        }
        
        StringBuilder sb = new StringBuilder();
        for (CharacterState character : characters) {
            sb.append("【").append(character.getCharacterName()).append("】\n");
            sb.append("  核心特质：").append(character.getCoreTraits()).append("\n");
            sb.append("  说话风格：").append(character.getVoiceStyle()).append("\n");
            if (character.getCurrentStatus() != null) {
                sb.append("  当前状态：").append(character.getCurrentStatus().getStatus()).append("\n");
                sb.append("  当前位置：").append(character.getCurrentStatus().getLocation()).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }
    
    private List<String> getUnrecycledClues(String novelId) {
        List<ClueBook> clues = clueBookService.getHighPriorityUnrecycledClues(novelId);
        List<String> result = new ArrayList<>();
        for (ClueBook clue : clues) {
            result.add(clue.getClueContent());
        }
        return result;
    }
    
    private String getLastChapterFingerprint(String novelId, Integer chapterNum) {
        if (chapterNum <= 1) {
            return "无（首章）";
        }
        NovelChapter prevChapter = novelChapterMapper.selectByNovelIdAndChapterNum(novelId, chapterNum - 1);
        return prevChapter != null ? prevChapter.getPlotFingerprint() : "无";
    }
    
    private String formatClues(List<String> clues) {
        if (clues == null || clues.isEmpty()) {
            return "无未回收伏笔";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < clues.size(); i++) {
            sb.append(i + 1).append(". ").append(clues.get(i)).append("\n");
        }
        return sb.toString();
    }
    
    private ValidationResult validateContent(ChapterGenerationRequest request, 
                                             GenerationContext context, String content) {
        ValidationRequest validationRequest = new ValidationRequest();
        validationRequest.setNovelId(request.getNovelId());
        validationRequest.setChapterNum(request.getChapterNum());
        validationRequest.setChapterContent(content);
        validationRequest.setChapterOutline(context.getOutline());
        validationRequest.setTotalOutline(context.getTotalOutline());
        validationRequest.setCharacterStates(context.getCharacterStates());
        
        return validationService.validateAll(validationRequest);
    }
    
    private NovelChapter createChapterEntity(ChapterGenerationRequest request, GenerationContext context, String chapterContent) {
        NovelChapter chapter = new NovelChapter();
        chapter.setNovelId(request.getNovelId());
        chapter.setChapterNum(request.getChapterNum());
        chapter.setChapterTitle("第" + request.getChapterNum() + "章");
        chapter.setContent(chapterContent);
        return chapter;
    }
    
    private ChapterSummary generateSummary(ChapterGenerationRequest request, GenerationContext context, NovelChapter chapter) {
        // 调用 ChapterGenerationChain 摘要链
        String summaryJson = chapterGenerationChain.executeSummaryChain(
            request.getNovelId(), request.getChapterNum(),
            chapter.getContent(), context.getOutline(), null);
        
        SummaryAgentResponse summaryResponse = parseSummaryJson(summaryJson);
        
        ChapterSummary summary = new ChapterSummary();
        summary.setNovelId(request.getNovelId());
        summary.setChapterNum(request.getChapterNum());
        summary.setContent(summaryResponse.getFingerprint());
        summary.setKeywords(summaryResponse.getKeywords());
        summary.setCharacters(summaryResponse.getCharacters());
        summary.setLocations(summaryResponse.getLocations());
        summary.setRelations(summaryResponse.getRelations());
        summary.setPriority(2);
        return summary;
    }
    
    // ========== Chain 调用辅助方法 ==========
    
    /**
     * 构建章纲生成的上下文提示词
     * 
     * 【核心作用】
     * 将所有需要的信息组装成一个结构化的提示词，供Agent使用。
     * 这是连接业务数据和AI Agent的桥梁。
     * 
     * 【上下文结构】
     * ```
     * ===小说总纲===
     * [小说的整体剧情规划]
     * 
     * ===卷信息===
     * 卷名: [当前卷名称]
     * 卷纲: [当前卷的剧情规划]
     * 章节数: [起始章节]-[结束章节]
     * 
     * ===世界观===
     * [力量体系、地理环境、社会势力等]
     * 
     * ===世界状态===
     * 时间: [当前时间]
     * 季节: [当前季节]
     * 天气: [当前天气]
     * 
     * ===人物状态===
     * [所有人物的当前状态]
     * 
     * ===上一章剧情===
     * [上一章的剧情摘要]
     * 
     * ===用户意图===
     * [用户的特殊要求]
     * 
     * ===未回收伏笔===
     * [需要在后续章节回收的伏笔]
     * ```
     * 
     * 【设计考虑】
     * 1. 信息完整性：包含Agent需要的所有信息
     * 2. 格式清晰：使用分隔符区分不同部分
     * 3. 长度控制：对长文本进行截断，避免超过LLM上下文限制
     * 4. 可读性：使用人类可读的格式，便于调试
     * 
     * @param totalOutline 小说总纲
     * @param arcInfo 卷信息（卷名、卷纲、章节范围）
     * @param worldClockInfo 世界状态（时间、季节、天气）
     * @param worldSetting 世界观设定
     * @param characterStates 人物状态字符串
     * @param lastFingerprint 上一章剧情摘要
     * @param userIntent 用户意图
     * @param unrecycledClues 未回收伏笔列表
     * @return 格式化的上下文提示词字符串
     * 
     * @see ChapterGenerationChain#executeOutlineChain 使用这个上下文的方法
     */
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
     * 解析Agent返回的章纲JSON
     * 
     * 【核心作用】
     * 将Agent返回的JSON字符串解析为OutlineAgentResponse对象。
     * Agent返回的JSON格式：
     * {
     *   "strand_type": "quest",
     *   "outline": "章纲内容...",
     *   "ideas": ["思路1", "思路2"]
     * }
     * 
     * 【容错处理】
     * 由于LLM返回的JSON可能不完全规范，需要进行容错处理：
     * 1. 清理Markdown格式（去除```json标记）
     * 2. 尝试解析JSON
     * 3. 如果解析失败，使用默认值
     * 4. 记录警告日志便于调试
     * 
     * 【默认值】
     * 如果解析失败，使用以下默认值：
     * - outline: 使用原始JSON的前500个字符
     * - ideas: 使用预设的两个默认思路
     * 
     * @param json Agent返回的JSON字符串
     * @return 解析后的OutlineAgentResponse对象
     * @see OutlineAgentResponse 章纲响应的数据结构
     * @see OutlineAgent 生成这个JSON的Agent
     */
    private OutlineAgentResponse parseOutlineJson(String json) {
        OutlineAgentResponse result = new OutlineAgentResponse();
        List<String> defaultIdeas = Arrays.asList(
            "按照章纲直接展开，注重场景描写和人物对话，营造沉浸感",
            "以主角视角为主线，穿插心理活动和环境描写，增强代入感"
        );
        try {
            // 清理Markdown格式
            String cleaned = cleanMarkdownJson(json);
            // 解析JSON
            JsonNode node = objectMapper.readTree(cleaned);
            // 提取outline字段
            result.setOutline(node.has("outline") ? node.get("outline").asText() : json.substring(0, Math.min(json.length(), 500)));
            // 提取ideas字段
            List<String> ideas = new ArrayList<>();
            if (node.has("ideas") && node.get("ideas").isArray()) {
                for (JsonNode n : node.get("ideas")) ideas.add(n.asText());
            }
            result.setImplementationIdeas(ideas.isEmpty() ? defaultIdeas : ideas);
        } catch (Exception e) {
            log.warn("解析大纲JSON失败: {}", e.getMessage());
            result.setOutline(json.substring(0, Math.min(json.length(), 500)));
            result.setImplementationIdeas(defaultIdeas);
        }
        return result;
    }
    
    private SummaryAgentResponse parseSummaryJson(String json) {
        SummaryAgentResponse result = new SummaryAgentResponse();
        try {
            String cleaned = cleanMarkdownJson(json);
            JsonNode node = objectMapper.readTree(cleaned);
            result.setFingerprint(node.has("fingerprint") ? node.get("fingerprint").asText() : truncate(json, 300));
            if (node.has("keywords") && node.get("keywords").isArray()) {
                List<String> keywords = new ArrayList<>();
                for (JsonNode n : node.get("keywords")) keywords.add(n.asText());
                result.setKeywords(keywords);
            }
            if (node.has("characters") && node.get("characters").isArray()) {
                List<String> characters = new ArrayList<>();
                for (JsonNode n : node.get("characters")) characters.add(n.asText());
                result.setCharacters(characters);
            }
            if (node.has("locations") && node.get("locations").isArray()) {
                List<String> locations = new ArrayList<>();
                for (JsonNode n : node.get("locations")) locations.add(n.asText());
                result.setLocations(locations);
            }
            if (node.has("relations") && node.get("relations").isArray()) {
                List<Map<String, String>> relations = new ArrayList<>();
                for (JsonNode n : node.get("relations")) {
                    Map<String, String> rel = new HashMap<>();
                    rel.put("source", n.has("source") ? n.get("source").asText() : "");
                    rel.put("target", n.has("target") ? n.get("target").asText() : "");
                    rel.put("relation", n.has("relation") ? n.get("relation").asText() : "");
                    relations.add(rel);
                }
                result.setRelations(relations);
            }
        } catch (Exception e) {
            log.warn("解析摘要JSON失败: {}", e.getMessage());
            result.setFingerprint(truncate(json, 300));
        }
        return result;
    }
    
    private String cleanMarkdownJson(String text) {
        String s = text.trim();
        if (s.startsWith("```json")) s = s.substring(7);
        else if (s.startsWith("```")) s = s.substring(3);
        if (s.endsWith("```")) s = s.substring(0, s.length() - 3);
        return s.trim();
    }
    
    @Data
    private static class ArcInfo {
        private String arcName;
        private String arcOutline;
        private Integer startChapter;
        private Integer endChapter;
    }
    
    @Data
    private static class WorldClockInfo {
        private String time;
        private String season;
        private String weather;
        private Integer totalDays;
    }
}
