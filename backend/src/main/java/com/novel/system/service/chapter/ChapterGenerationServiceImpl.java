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
import com.novel.system.service.style.StyleCheckerService;
import com.novel.system.dto.style.StyleCheckResult;
import com.novel.system.ai.agent.OutlineAgentResponse;
import com.novel.system.ai.agent.SummaryAgentResponse;
import com.novel.system.ai.chain.ValidationRequest;
import com.novel.system.ai.chain.ValidationResult;
import com.novel.system.ai.chain.ValidationService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.novel.system.dto.vo.GenerationContext;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * 章节生成服务实现 - 核心工作流
 * 
 * 流程：章纲生成(OutlineAgent) → 正文生成(WritingAgent) → 摘要生成(SummaryAgent)
 *       → 五重校验(OOC/逻辑/伏笔/设定/节奏) + 文风检查 → 后处理(人物/伏笔/时间线/事件提取)
 * 
 * 上下文构建委托给 ContextBuilderService，实现五层记忆架构：
 * 第一层：故事状态（StoryState）— 核心枢纽，全量注入
 * 第二层：伏笔账本（ClueBook）— 精确SQL查询，按条件注入
 * 第三层：角色档案（CharacterState + CharacterGraph）— 按需注入
 * 第四层：滑动窗口（ArcSummary + 近章大纲 + 上章钩子）— 保证微观衔接
 * 第五层：事件图谱 + 规则驱动检索 — 按规则自动触发历史事件检索
 * 第六层：RAG向量检索 — Agent @Tool 按需调用（当前仍为Push模式，待改为Pull）
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
    private final ValidationService validationService;
    private final StringRedisTemplate redisTemplate;
    private final ContextBuilderService contextBuilderService;
    private final ChapterVersionService chapterVersionService;
    private final com.novel.system.repository.mongo.NovelGraphRepository novelGraphRepository;
    private final ObjectMapper objectMapper;
    private final com.novel.system.ai.chain.ChapterGenerationChain chapterGenerationChain;
    private final ChapterSaveService chapterSaveService;
    private final StyleCheckerService styleCheckerService;
    private final ChapterPostProcessService chapterPostProcessService;
    @Qualifier("ioTaskExecutor")
    private final Executor ioTaskExecutor;

    private static final String CONTEXT_CACHE_PREFIX = "novel:context:";
    private static final long CONTEXT_CACHE_TTL_MINUTES = 30;
    private static final long POST_PROCESS_TIMEOUT_SECONDS = 60;

    /** 最近一次后处理的 Future，供批量生成等待 */
    private volatile CompletableFuture<Void> postProcessFuture;
    
    @Override
    public ChapterGenerationResponse generateOutline(ChapterGenerationRequest request) {
        /**
         * 生成章纲
         * 
         * 【执行流程】
         * 1. 获取小说信息（验证小说存在）
         * 2. 获取卷信息（当前章节属于哪一卷）
         * 3. 获取世界状态（时间、季节、天气）
         * 4. 获取人物状态（所有人物的当前状态）— 第三层：角色档案
         * 5. 获取未回收伏笔（需要在后续章节回收的伏笔）— 第二层：伏笔账本
         * 6. 获取上一章摘要（保持剧情连贯）— 第四层：滑动窗口
         * 7. 获取小说世界观（力量体系、地理环境等）
         * 8. 组装上下文信息（ContextBuilderService 五层记忆架构）
         * 9. 调用ChapterGenerationChain执行章纲生成
         * 10. 解析返回的JSON（提取outline和ideas）
         * 11. 缓存生成上下文到Redis（供后续生成正文使用）
         * 12. 持久化章纲到数据库（防止Redis缓存丢失）
         * 13. 构建响应返回
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
            
            // 4. 获取人物状态（直接调用CharacterStateService，避免重复查询）
            String characterStates = characterStateService.formatForPrompt(request.getNovelId());
            
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
            context.setChapterTitle(outlineResponse.getTitle());

            String cacheKey = CONTEXT_CACHE_PREFIX + generateCacheKey(request.getNovelId(), request.getChapterNum());
            String contextJson = objectMapper.writeValueAsString(context);
            redisTemplate.opsForValue().set(cacheKey, contextJson, CONTEXT_CACHE_TTL_MINUTES, TimeUnit.MINUTES);

            // 10.1 持久化章纲到数据库（防止Redis缓存丢失）
            persistOutlineToDatabase(request.getNovelId(), request.getChapterNum(),
                    outlineResponse.getOutline(), outlineResponse.getImplementationIdeas(), request.getUserIntent(), outlineResponse.getHook());
            
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
                // 如果没有缓存，尝试从数据库恢复章纲
                context = recoverContextFromDatabase(request.getNovelId(), request.getChapterNum());
            }

            if (context == null) {
                // 如果数据库也没有，先生成章纲
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
            
            // 3. 调用 ChapterGenerationChain 写作链
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
            
            // 6. 如果校验失败，重试（最多2次，将失败原因和上次内容注入上下文）
            int retryCount = 0;
            while (!validationResult.getPassed() && retryCount < 2) {
                log.warn("校验未通过，重试第{}次，原因: {}", retryCount + 1, validationResult.getSummary());

                // 重试时注入上次生成的内容和校验反馈
                String retryContext = writingContext
                    + "\n\n===上次生成的内容（存在以下问题，请修改）===\n"
                    + chapterContent
                    + "\n\n===校验反馈===\n"
                    + (validationResult.getSummary() != null ? validationResult.getSummary() : "未知问题")
                    + "\n\n请针对上述问题修改，保留合理的部分，只调整有问题的段落。";

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
            
            // 8. 事务保存所有结果（MySQL同步 + MongoDB异步）+ 后处理链式调用
            final String finalChapterContent = chapterContent;
            final String finalOutline = context.getOutline();
            this.postProcessFuture = chapterSaveService.saveGenerationResultAsync(
                request.getNovelId(), request.getChapterNum(), chapter.getChapterTitle(),
                context.getOutline(), chapterContent,
                chapterContent.replaceAll("\\s+", "").length(),
                request.getUserIntent(), summary.getContent(), summary)
                .thenRun(() -> {
                    // 9. 在MongoDB写入完成后执行后处理（提取人物、伏笔、时间线）
                    try {
                        chapterPostProcessService.postProcess(
                            request.getNovelId(), request.getChapterNum(),
                            finalChapterContent, finalOutline);
                    } catch (Exception e) {
                        log.error("后处理失败: novelId={}, chapterNum={}", request.getNovelId(), request.getChapterNum(), e);
                    }
                })
                .exceptionally(ex -> {
                    log.error("保存或后处理失败: {}", ex.getMessage());
                    return null;
                });
            
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
        SecurityContext securityContext = SecurityContextHolder.getContext();
        CompletableFuture.runAsync(() -> {
            SecurityContextHolder.setContext(securityContext);
            try {
                ChapterGenerationResponse response = generateChapter(request);
                updateTaskStatus(taskId, response);
                log.info("异步章节生成完成，任务ID: {}", taskId);
            } catch (Exception e) {
                log.error("异步章节生成失败，任务ID: " + taskId, e);
                updateTaskStatus(taskId, "FAILED", "章节生成失败: " + e.getMessage());
            } finally {
                SecurityContextHolder.clearContext();
            }
        }, ioTaskExecutor);
        
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
        
        // 调用 ChapterGenerationChain 摘要链（使用用户配置的模型）
        String summaryJson = chapterGenerationChain.executeSummaryChain(
            request.getNovelId(), request.getChapterNum(), content, outline, request.getLlmConfigId());
        
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

        // 补全缺失的上下文字段
        validationRequest.setWorldSetting(context.getWorldSetting());
        validationRequest.setArcOutline(context.getArcOutline());
        if (context.getUnrecycledClues() != null && !context.getUnrecycledClues().isEmpty()) {
            validationRequest.setClueBook(String.join("\n", context.getUnrecycledClues()));
        }

        ValidationResult result = validationService.validateAll(validationRequest);

        // 集成 StyleCheckerService 进行 AI味检测
        if (result.getPassed() && request.getStyleCardId() != null) {
            try {
                StyleCheckResult styleResult = styleCheckerService.quickCheck(content, request.getStyleCardId());
                if (!styleResult.isPass()) {
                    log.warn("文风检查未通过，分数: {}, 问题: {}", styleResult.getScore(), styleResult.getIssues());
                    result.setPassed(false);
                    result.setSummary("文风检查未通过: " + String.join(", ", styleResult.getIssues()));
                } else {
                    log.debug("文风检查通过，分数: {}", styleResult.getScore());
                }
            } catch (Exception e) {
                // 文风检查失败时记录详细错误信息，但不阻止生成
                log.error("文风检查异常，跳过文风校验。styleCardId={}, error={}",
                        request.getStyleCardId(), e.getMessage(), e);
                // 在结果中标注文风检查失败，便于追踪
                if (result.getSummary() == null || result.getSummary().isEmpty()) {
                    result.setSummary("文风检查系统异常，已跳过文风校验");
                } else {
                    result.setSummary(result.getSummary() + "（文风检查异常，已跳过）");
                }
            }
        }

        return result;
    }
    
    private NovelChapter createChapterEntity(ChapterGenerationRequest request, GenerationContext context, String chapterContent) {
        NovelChapter chapter = new NovelChapter();
        chapter.setNovelId(request.getNovelId());
        chapter.setChapterNum(request.getChapterNum());
        // 从章纲解析结果中获取标题，如果存在则拼接
        String chapterTitle = context.getChapterTitle();
        if (chapterTitle != null && !chapterTitle.isEmpty()) {
            chapter.setChapterTitle("第" + request.getChapterNum() + "章 " + chapterTitle);
        } else {
            chapter.setChapterTitle("第" + request.getChapterNum() + "章");
        }
        chapter.setContent(chapterContent);
        return chapter;
    }
    
    private ChapterSummary generateSummary(ChapterGenerationRequest request, GenerationContext context, NovelChapter chapter) {
        // 调用 ChapterGenerationChain 摘要链（使用用户配置的模型）
        String summaryJson = chapterGenerationChain.executeSummaryChain(
            request.getNovelId(), request.getChapterNum(),
            chapter.getContent(), context.getOutline(), request.getLlmConfigId());
        
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
     * 将章纲持久化到数据库
     * 防止Redis缓存丢失后章纲无法恢复
     */
    private void persistOutlineToDatabase(String novelId, int chapterNum, String outline,
                                          List<String> implementationIdeas, String userIntent,
                                          String hook) {
        try {
            NovelChapter chapter = novelChapterMapper.selectByNovelIdAndChapterNum(novelId, chapterNum);
            if (chapter == null) {
                // 创建新的章节记录（状态为待生成）
                chapter = new NovelChapter();
                chapter.setChapterId(UUID.randomUUID().toString());
                chapter.setNovelId(novelId);
                chapter.setChapterNum(chapterNum);
                chapter.setChapterTitle("第" + chapterNum + "章");
                chapter.setChapterOutline(outline);
                chapter.setImplementationIdea(implementationIdeas != null && !implementationIdeas.isEmpty()
                        ? implementationIdeas.get(0) : null);
                chapter.setUserIntent(userIntent);
                chapter.setHook(hook);
                chapter.setGenerationStatus(0); // 待生成
                chapter.setCreateTime(LocalDateTime.now());
                chapter.setUpdateTime(LocalDateTime.now());
                novelChapterMapper.insert(chapter);
                log.info("创建章节记录并保存章纲: novelId={}, chapterNum={}", novelId, chapterNum);
            } else {
                // 更新已有章节的章纲
                chapter.setChapterOutline(outline);
                chapter.setImplementationIdea(implementationIdeas != null && !implementationIdeas.isEmpty()
                        ? implementationIdeas.get(0) : chapter.getImplementationIdea());
                chapter.setHook(hook);
                chapter.setUpdateTime(LocalDateTime.now());
                novelChapterMapper.updateById(chapter);
                log.info("更新章节章纲: novelId={}, chapterNum={}", novelId, chapterNum);
            }
        } catch (Exception e) {
            log.error("持久化章纲失败: novelId={}, chapterNum={}", novelId, chapterNum, e);
            // 不抛出异常，不影响主流程
        }
    }

    /**
     * 从数据库恢复生成上下文
     * 当Redis缓存丢失时，从数据库中的章纲重建上下文
     */
    private GenerationContext recoverContextFromDatabase(String novelId, int chapterNum) {
        try {
            NovelChapter chapter = novelChapterMapper.selectByNovelIdAndChapterNum(novelId, chapterNum);
            if (chapter == null || chapter.getChapterOutline() == null || chapter.getChapterOutline().isEmpty()) {
                return null;
            }

            log.info("从数据库恢复章纲上下文: novelId={}, chapterNum={}", novelId, chapterNum);

            // 获取小说信息
            NovelBase novel = novelBaseMapper.selectById(novelId);
            if (novel == null) {
                return null;
            }

            // 获取卷信息
            ArcInfo arcInfo = getArcInfo(novelId, chapterNum);

            // 获取世界状态
            WorldClockInfo worldClockInfo = getWorldClockInfo(novelId);

            // 获取人物状态（直接调用CharacterStateService，避免重复查询）
            String characterStates = characterStateService.formatForPrompt(novelId);

            // 获取未回收伏笔
            List<String> unrecycledClues = getUnrecycledClues(novelId);

            // 获取上一章摘要
            String lastFingerprint = getLastChapterFingerprint(novelId, chapterNum);

            // 构建上下文
            GenerationContext context = new GenerationContext();
            context.setNovelId(novelId);
            context.setGenre(novel.getGenre());
            context.setTotalOutline(novel.getTotalOutline());
            context.setArcOutline(arcInfo.getArcOutline());
            context.setArcName(arcInfo.getArcName());
            context.setWorldSetting(novel.getWorldSetting() != null ? novel.getWorldSetting() : "世界观待配置");
            context.setCharacterStates(characterStates);
            context.setUnrecycledClues(unrecycledClues);
            context.setOutline(chapter.getChapterOutline());
            context.setImplementationIdeas(chapter.getImplementationIdea() != null
                    ? Arrays.asList(chapter.getImplementationIdea()) : Arrays.asList());
            context.setLastChapterFingerprint(lastFingerprint);

            // 同时更新Redis缓存
            String cacheKey = CONTEXT_CACHE_PREFIX + generateCacheKey(novelId, chapterNum);
            String contextJson = objectMapper.writeValueAsString(context);
            redisTemplate.opsForValue().set(cacheKey, contextJson, CONTEXT_CACHE_TTL_MINUTES, TimeUnit.MINUTES);

            return context;
        } catch (Exception e) {
            log.error("从数据库恢复上下文失败: novelId={}, chapterNum={}", novelId, chapterNum, e);
            return null;
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
            String cleaned = cleanMarkdownJson(json);
            JsonNode node = objectMapper.readTree(cleaned);

            // 优先从结构化字段组装 outline（适配 Agent 实际输出格式）
            StringBuilder outlineBuilder = new StringBuilder();
            if (node.has("title")) {
                outlineBuilder.append("标题：").append(node.get("title").asText()).append("\n");
                result.setTitle(node.get("title").asText());
            }
            if (node.has("summary")) {
                outlineBuilder.append("概要：").append(node.get("summary").asText()).append("\n");
                result.setSummary(node.get("summary").asText());
            }
            if (node.has("key_points") && node.get("key_points").isArray()) {
                outlineBuilder.append("要点：\n");
                List<String> keyPoints = new ArrayList<>();
                for (JsonNode p : node.get("key_points")) {
                    outlineBuilder.append("- ").append(p.asText()).append("\n");
                    keyPoints.add(p.asText());
                }
                result.setKeyPoints(keyPoints);
            }
            if (node.has("hook")) {
                outlineBuilder.append("结尾钩子：").append(node.get("hook").asText()).append("\n");
                result.setHook(node.get("hook").asText());
            }

            if (outlineBuilder.length() > 0) {
                result.setOutline(outlineBuilder.toString());
            } else if (node.has("outline")) {
                // 兼容旧格式
                result.setOutline(node.get("outline").asText());
            } else {
                result.setOutline(json.substring(0, Math.min(json.length(), 500)));
            }

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

    @Override
    public void waitForPostProcessing() {
        CompletableFuture<Void> future = this.postProcessFuture;
        if (future == null) return;
        try {
            future.get(POST_PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("等待后处理超时或中断: {}", e.getMessage());
        }
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
