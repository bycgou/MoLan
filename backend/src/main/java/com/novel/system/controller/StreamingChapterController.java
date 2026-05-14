package com.novel.system.controller;

import com.novel.system.config.LangChainConfig;
import com.novel.system.entity.mysql.LlmConfig;
import com.novel.system.mapper.NovelBaseMapper;
import com.novel.system.entity.mysql.NovelBase;
import com.novel.system.mapper.NovelChapterMapper;
import com.novel.system.entity.mysql.NovelChapter;
import com.novel.system.entity.mysql.NovelStyleBinding;
import com.novel.system.mapper.NovelStyleBindingMapper;
import com.novel.system.service.llm.LlmConfigService;
import com.novel.system.ai.chain.ValidationService;
import com.novel.system.ai.chain.ValidationRequest;
import com.novel.system.ai.chain.ValidationResult;
import com.novel.system.service.chapter.ChapterSaveService;
import com.novel.system.service.chapter.ContextBuilderService;
import com.novel.system.service.chapter.ChapterPostProcessService;
import com.novel.system.service.story.StoryStateService;
import com.novel.system.service.style.StyleCardService;
import com.novel.system.ai.chain.ChapterGenerationChain;
import com.novel.system.entity.mongo.ChapterSummary;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.output.Response;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * SSE 流式写作控制器
 * 实现 Super-Ma 的 SSE 流式输出和取消写作功能
 * 
 * @author AI Assistant
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/chapter")
@RequiredArgsConstructor
public class StreamingChapterController {
    
    private final StringRedisTemplate redisTemplate;
    private final LangChainConfig langChainConfig;
    private final LlmConfigService llmConfigService;
    private final ChapterSaveService chapterSaveService;
    private final ValidationService validationService;
    private final NovelBaseMapper novelBaseMapper;
    private final NovelChapterMapper novelChapterMapper;
    private final ContextBuilderService contextBuilderService;
    private final ChapterPostProcessService chapterPostProcessService;
    private final NovelStyleBindingMapper novelStyleBindingMapper;
    private final StyleCardService styleCardService;
    private final StoryStateService storyStateService;
    private final ChapterGenerationChain chapterGenerationChain;
    private final ObjectMapper objectMapper;
    @Qualifier("ioTaskExecutor")
    private final Executor ioTaskExecutor;
    
    /**
     * 流式写作请求
     */
    @Data
    public static class StreamWriteRequest {
        private String novelId;
        private Integer chapterNum;
        private String outline;
        private String contextPrompt;
        private String llmConfigId;
        private String styleCardId;
    }
    
    /**
     * SSE 流式写作
     */
    @PostMapping(value = "/stream-write", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamWrite(@RequestBody StreamWriteRequest request) {
        SseEmitter emitter = new SseEmitter(600_000L); // 10min 超时
        String cancelKey = "novel:cancel:" + request.getNovelId();
        SecurityContext securityContext = SecurityContextHolder.getContext();

        CompletableFuture.runAsync(() -> {
            try {
                redisTemplate.delete(cancelKey); // 清除旧的取消标记

                // 获取 LlmConfig
                LlmConfig llmConfig = llmConfigService.getConfigById(request.getLlmConfigId());
                if (llmConfig == null) {
                    emitter.completeWithError(new RuntimeException("找不到LLM配置: " + request.getLlmConfigId()));
                    return;
                }

                // 获取小说信息
                NovelBase novel = novelBaseMapper.selectById(request.getNovelId());
                if (novel == null) {
                    emitter.completeWithError(new RuntimeException("小说不存在: " + request.getNovelId()));
                    return;
                }

                // 构建上下文（如果前端未提供）
                String contextPrompt = request.getContextPrompt();
                if (contextPrompt == null || contextPrompt.isEmpty()) {
                    try {
                        // 创建临时章节实体用于构建上下文
                        NovelChapter currentChapter = new NovelChapter();
                        currentChapter.setChapterNum(request.getChapterNum());
                        currentChapter.setChapterOutline(request.getOutline());

                        contextPrompt = contextBuilderService.buildWritingContext(
                            request.getNovelId(), request.getChapterNum(),
                            currentChapter, novel, request.getOutline());
                    } catch (Exception e) {
                        log.warn("构建上下文失败，使用空上下文: {}", e.getMessage());
                    }
                    if (contextPrompt == null) contextPrompt = "";
                }

                // 获取文风卡片
                String effectiveStyleCardId = request.getStyleCardId();
                if (effectiveStyleCardId == null || effectiveStyleCardId.isEmpty()) {
                    NovelStyleBinding binding = novelStyleBindingMapper.selectByNovelId(request.getNovelId());
                    if (binding != null && binding.getCardId() != null) {
                        effectiveStyleCardId = binding.getCardId();
                    }
                }

                // 注入文风卡片内容
                String styleInjection = "";
                if (effectiveStyleCardId != null && !effectiveStyleCardId.isEmpty()) {
                    try {
                        styleInjection = styleCardService.buildStyleInjection(effectiveStyleCardId);
                    } catch (Exception e) {
                        log.warn("获取文风卡片失败: {}", e.getMessage());
                    }
                }

                // 构建用户消息
                StringBuilder userPromptBuilder = new StringBuilder();
                userPromptBuilder.append(String.format(
                    "小说ID: %s\n章节号: 第%d章\n\n", request.getNovelId(), request.getChapterNum()));

                if (!styleInjection.isEmpty()) {
                    userPromptBuilder.append(styleInjection).append("\n\n");
                }

                userPromptBuilder.append(String.format(
                    "===章纲===\n%s\n\n===上下文信息===\n%s\n\n请根据章纲生成第%d章正文。",
                    request.getOutline(), contextPrompt, request.getChapterNum()));

                String userPrompt = userPromptBuilder.toString();
                
                // 创建流式模型
                StreamingChatLanguageModel streamingModel = langChainConfig.createStreamingChatModel(llmConfig);
                
                // 逐token流式输出
                StringBuilder fullContent = new StringBuilder();
                streamingModel.generate(userPrompt, new StreamingResponseHandler<AiMessage>() {
                    @Override
                    public void onNext(String token) {
                        try {
                            // 检查取消
                            if (Boolean.TRUE.equals(redisTemplate.hasKey(cancelKey))) {
                                emitter.completeWithError(new RuntimeException("用户取消了写作"));
                                return;
                            }
                            fullContent.append(token);
                            emitter.send(SseEmitter.event().data(token));
                        } catch (Exception e) {
                            log.error("发送token失败: {}", e.getMessage());
                            emitter.completeWithError(e);
                        }
                    }
                    
                    @Override
                    public void onComplete(Response<AiMessage> response) {
                        try {
                            String fullText = response.content().text();
                            fullContent.setLength(0);
                            fullContent.append(fullText);

                            // 异步保存 + 校验
                            CompletableFuture.runAsync(() -> {
                                SecurityContextHolder.setContext(securityContext);
                                try {
                                    // 1. 校验（补全上下文）
                                    ValidationRequest valReq = new ValidationRequest();
                                    valReq.setNovelId(request.getNovelId());
                                    valReq.setChapterNum(request.getChapterNum());
                                    valReq.setChapterContent(fullText);
                                    valReq.setChapterOutline(request.getOutline());
                                    valReq.setTotalOutline(novel.getTotalOutline());
                                    valReq.setWorldSetting(novel.getWorldSetting());
                                    ValidationResult valResult = validationService.validateAll(valReq);

                                    if (!valResult.getPassed()) {
                                        try {
                                            List<String> warnings = new ArrayList<>();
                                            if (valResult.getOocResult() != null && !valResult.getOocResult().isPassed()) {
                                                warnings.add("前章OOC问题：总分" + valResult.getOocResult().getTotalScore() + "分（阈值70）");
                                            }
                                            if (valResult.getLogicResult() != null && !valResult.getLogicResult().isPassed()) {
                                                warnings.add("前章逻辑问题：" + valResult.getLogicResult().getConflictDescription());
                                            }
                                            if (valResult.getSettingResult() != null && !valResult.getSettingResult().isPassed()) {
                                                warnings.add("前章设定冲突：" + valResult.getSettingResult().getConflicts());
                                            }
                                            if (!warnings.isEmpty()) {
                                                storyStateService.addWarnings(request.getNovelId(), warnings);
                                            }
                                        } catch (Exception e) {
                                            log.warn("写入校验警告失败: {}", e.getMessage());
                                        }
                                    } else {
                                        try {
                                            storyStateService.clearWarnings(request.getNovelId());
                                        } catch (Exception e) {
                                            log.warn("清除校验警告失败: {}", e.getMessage());
                                        }
                                    }

                                    String valSummary = valResult.getSummary();
                                    log.info("流式生成校验结果: {}", valSummary);

                                    // 发送校验结果SSE事件
                                    try {
                                        Map<String, Object> valEvent = new HashMap<>();
                                        valEvent.put("passed", valResult.getPassed());
                                        valEvent.put("summary", valResult.getSummary());
                                        if (!valResult.getPassed()) {
                                            valEvent.put("oocScore", valResult.getOocResult() != null ? valResult.getOocResult().getTotalScore() : -1);
                                            valEvent.put("logicPassed", valResult.getLogicResult() != null && valResult.getLogicResult().isPassed());
                                            valEvent.put("settingPassed", valResult.getSettingResult() != null && valResult.getSettingResult().isPassed());
                                        }
                                        emitter.send(SseEmitter.event()
                                            .name("validation_result")
                                            .data(objectMapper.writeValueAsString(valEvent)));
                                    } catch (Exception e) {
                                        log.warn("发送校验结果SSE事件失败: {}", e.getMessage());
                                    }

                                    // 2. 生成摘要
                                    String summaryContent = "";
                                    try {
                                        String summaryJson = chapterGenerationChain.executeSummaryChain(
                                            request.getNovelId(), request.getChapterNum(),
                                            fullText, request.getOutline(), request.getLlmConfigId());
                                        // 简单提取摘要内容
                                        if (summaryJson != null && !summaryJson.isEmpty()) {
                                            summaryContent = summaryJson.length() > 300
                                                ? summaryJson.substring(0, 300) : summaryJson;
                                        }
                                    } catch (Exception e) {
                                        log.warn("生成摘要失败: {}", e.getMessage());
                                    }

                                    // 3. 保存到数据库
                                    int wordCount = fullText.replaceAll("\\s+", "").length();
                                    chapterSaveService.saveGenerationResult(
                                        request.getNovelId(), request.getChapterNum(),
                                        "第" + request.getChapterNum() + "章",
                                        request.getOutline(), fullText, wordCount,
                                        request.getContextPrompt(), summaryContent, null);

                                    log.info("流式生成内容已保存, 小说ID: {}, 章节号: {}, 字数: {}",
                                        request.getNovelId(), request.getChapterNum(), wordCount);

                                    // 4. 异步后处理（提取人物/伏笔/时间线）
                                    chapterPostProcessService.postProcess(
                                        request.getNovelId(), request.getChapterNum(),
                                        fullText, request.getOutline());

                                    // 5. 发送完成事件（含校验摘要）
                                    String completeData = valSummary != null ? valSummary : "";
                                    emitter.send(SseEmitter.event().name("complete").data(completeData));
                                    emitter.complete();
                                } catch (Exception e) {
                                    log.error("保存流式内容失败", e);
                                    emitter.completeWithError(e);
                                } finally {
                                    SecurityContextHolder.clearContext();
                                }
                            }, ioTaskExecutor);
                        } catch (Exception e) {
                            emitter.completeWithError(e);
                        }
                    }
                    
                    @Override
                    public void onError(Throwable error) {
                        log.error("流式生成失败: {}", error.getMessage());
                        emitter.completeWithError(error);
                    }
                });
                
            } catch (Exception e) {
                log.error("流式写作初始化失败: {}", e.getMessage());
                emitter.completeWithError(e);
            }
        }, ioTaskExecutor);
        
        return emitter;
    }
    
    /**
     * 取消写作
     */
    @PostMapping("/cancel")
    public ResponseEntity<?> cancelWriting(@RequestParam String novelId) {
        redisTemplate.opsForValue().set("novel:cancel:" + novelId, "1", 10, TimeUnit.MINUTES);
        return ResponseEntity.ok().build();
    }

    /**
     * 保存流式生成的内容（取消后或校验失败后手动保存）
     */
    @PostMapping("/stream-save")
    public ResponseEntity<?> streamSave(@RequestBody Map<String, String> payload) {
        String novelId = payload.get("novelId");
        Integer chapterNum = Integer.parseInt(payload.get("chapterNum"));
        String content = payload.get("content");
        String outline = payload.getOrDefault("outline", "");
        String llmConfigId = payload.get("llmConfigId");

        if (novelId == null || content == null || content.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "内容不能为空"));
        }

        // 生成摘要
        String summaryContent = "";
        try {
            String summaryJson = chapterGenerationChain.executeSummaryChain(
                novelId, chapterNum, content, outline, llmConfigId);
            if (summaryJson != null && !summaryJson.isEmpty()) {
                summaryContent = summaryJson.length() > 300
                    ? summaryJson.substring(0, 300) : summaryJson;
            }
        } catch (Exception e) {
            log.warn("生成摘要失败: {}", e.getMessage());
        }

        int wordCount = content.replaceAll("\\s+", "").length();
        chapterSaveService.saveGenerationResult(
                novelId, chapterNum,
                "第" + chapterNum + "章",
                outline, content, wordCount,
                "", summaryContent, null);

        log.info("流式内容手动保存成功, 小说ID: {}, 章节号: {}, 字数: {}", novelId, chapterNum, wordCount);
        return ResponseEntity.ok(Map.of("success", true, "wordCount", wordCount));
    }
}