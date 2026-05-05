package com.novel.system.controller;

import com.novel.system.config.LangChainConfig;
import com.novel.system.entity.mysql.LlmConfig;
import com.novel.system.mapper.NovelBaseMapper;
import com.novel.system.entity.mysql.NovelBase;
import com.novel.system.mapper.NovelChapterMapper;
import com.novel.system.entity.mysql.NovelChapter;
import com.novel.system.service.llm.LlmConfigService;
import com.novel.system.ai.chain.ValidationService;
import com.novel.system.ai.chain.ValidationRequest;
import com.novel.system.ai.chain.ValidationResult;
import com.novel.system.service.chapter.ChapterSaveService;
import com.novel.system.service.chapter.ContextBuilderService;
import com.novel.system.service.chapter.ChapterPostProcessService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.output.Response;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.CompletableFuture;
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
    }
    
    /**
     * SSE 流式写作
     */
    @PostMapping(value = "/stream-write", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamWrite(@RequestBody StreamWriteRequest request) {
        SseEmitter emitter = new SseEmitter(600_000L); // 10min 超时
        String cancelKey = "novel:cancel:" + request.getNovelId();
        
        CompletableFuture.runAsync(() -> {
            try {
                redisTemplate.delete(cancelKey); // 清除旧的取消标记
                
                // 获取 LlmConfig
                LlmConfig llmConfig = llmConfigService.getConfigById(request.getLlmConfigId());
                if (llmConfig == null) {
                    emitter.completeWithError(new RuntimeException("找不到LLM配置: " + request.getLlmConfigId()));
                    return;
                }
                
                // 构建上下文（如果前端未提供）
                String contextPrompt = request.getContextPrompt();
                if (contextPrompt == null || contextPrompt.isEmpty()) {
                    try {
                        NovelBase novel = novelBaseMapper.selectById(request.getNovelId());
                        if (novel != null) {
                            int prevChapterNum = request.getChapterNum() - 1;
                            if (prevChapterNum > 0) {
                                NovelChapter prevChapter = novelChapterMapper
                                    .selectByNovelIdAndChapterNum(request.getNovelId(), prevChapterNum);
                                if (prevChapter != null) {
                                    contextPrompt = contextBuilderService.buildWritingContext(
                                        request.getNovelId(), request.getChapterNum(),
                                        prevChapter, novel, request.getOutline());
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.warn("构建上下文失败，使用空上下文: {}", e.getMessage());
                    }
                    if (contextPrompt == null) contextPrompt = "";
                }
                
                // 构建用户消息
                String userPrompt = String.format(
                    "小说ID: %s\n章节号: 第%d章\n\n===章纲===\n%s\n\n===上下文信息===\n%s\n\n请根据章纲生成第%d章正文。",
                    request.getNovelId(), request.getChapterNum(), 
                    request.getOutline(), contextPrompt, request.getChapterNum()
                );
                
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
                                try {
                                    // 1. 校验
                                    ValidationRequest valReq = new ValidationRequest();
                                    valReq.setNovelId(request.getNovelId());
                                    valReq.setChapterNum(request.getChapterNum());
                                    valReq.setChapterContent(fullText);
                                    ValidationResult valResult = validationService.validateAll(valReq);
                                    
                                    String valSummary = valResult.getSummary();
                                    log.info("流式生成校验结果: {}", valSummary);
                                    
                                    // 2. 保存到数据库
                                    int wordCount = fullText.replaceAll("\\s+", "").length();
                                    chapterSaveService.saveGenerationResult(
                                        request.getNovelId(), request.getChapterNum(),
                                        "第" + request.getChapterNum() + "章",
                                        request.getOutline(), fullText, wordCount,
                                        request.getContextPrompt(), "", null);
                                    
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
                                }
                            });
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
        });
        
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
}