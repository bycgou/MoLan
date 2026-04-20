package com.novel.system.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.system.config.RabbitMQConfig;
import com.novel.system.dto.vo.ChapterGenerateMessage;
import com.novel.system.service.chapter.ChapterGenerationRequest;
import com.novel.system.service.chapter.ChapterGenerationResponse;
import com.novel.system.service.chapter.ChapterGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChapterGenerateListener {

    private final ChapterGenerationService chapterGenerationService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @RabbitListener(
        queues = RabbitMQConfig.QUEUE_CHAPTER_GENERATE,
        concurrency = "3-5" // 并发消费，最小3个线程，最大5个线程
    )
    public void receiveGenerateRequest(ChapterGenerateMessage message) {
        log.info("MQ接收到章节生成任务, 任务ID: {}, 小说ID: {}, 章节号: {}", 
            message.getTaskId(), message.getNovelId(), message.getChapterNum());
        
        try {
            // 立即更新任务状态为处理中
            updateTaskStatus(message.getTaskId(), "GENERATING", "章节生成中...");
            
            ChapterGenerationRequest request = new ChapterGenerationRequest();
            request.setNovelId(message.getNovelId());
            request.setChapterNum(message.getChapterNum());
            request.setLlmConfigId(message.getLlmConfigId());
            request.setUserIntent(message.getUserIntent());
            request.setSelectedIdeaIndex(message.getSelectedIdeaIndex());
            
            // 真正执行生成
            ChapterGenerationResponse response = chapterGenerationService.generateChapter(request);
            log.info("MQ处理章节生成任务完成, 任务ID: {}", message.getTaskId());
            
            // 更新任务状态为完成
            updateTaskStatus(message.getTaskId(), response);
            
        } catch (Exception e) {
            log.error("MQ处理章节生成任务失败, 任务ID: " + message.getTaskId(), e);
            // 更新任务状态为失败
            updateTaskStatus(message.getTaskId(), "FAILED", "章节生成失败: " + e.getMessage());
        }
    }
    
    /**
     * 更新任务状态到Redis
     */
    private void updateTaskStatus(String taskId, ChapterGenerationResponse response) {
        try {
            String statusKey = "novel:task:status:" + taskId;
            redisTemplate.opsForValue().set(statusKey, objectMapper.writeValueAsString(response), 24, TimeUnit.HOURS);
        } catch (Exception e) {
            log.error("保存任务状态到Redis失败", e);
        }
    }
    
    /**
     * 更新任务状态到Redis
     */
    private void updateTaskStatus(String taskId, String status, String message) {
        try {
            String statusKey = "novel:task:status:" + taskId;
            ChapterGenerationResponse response = new ChapterGenerationResponse();
            response.setSuccess(status.equals("COMPLETED"));
            response.setStatus(ChapterGenerationResponse.GenerationStatus.valueOf(status));
            response.setErrorMessage(message);
            redisTemplate.opsForValue().set(statusKey, objectMapper.writeValueAsString(response), 24, TimeUnit.HOURS);
        } catch (Exception e) {
            log.error("保存任务状态到Redis失败", e);
        }
    }
}
