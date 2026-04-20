package com.novel.system.ai.memory;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 小说对话记忆 - 基于Redis的短期记忆
 * 
 * 用于保持Agent在生成章节过程中的上下文连贯性
 * 类似LangChain4j的ChatMemory，但按小说+章节维度管理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NovelChatMemory {
    
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    
    private static final String MEMORY_KEY_PREFIX = "novel:memory:";
    private static final Duration MEMORY_TTL = Duration.ofHours(2);
    private static final int MAX_MESSAGES = 20;
    
    /**
     * 添加消息到记忆
     */
    public void addMessage(String novelId, int chapterNum, String role, String content) {
        String key = MEMORY_KEY_PREFIX + novelId + ":" + chapterNum;
        
        try {
            List<MemoryMessage> messages = getMessages(novelId, chapterNum);
            messages.add(new MemoryMessage(role, content, System.currentTimeMillis()));
            
            // 限制消息数量
            if (messages.size() > MAX_MESSAGES) {
                messages = messages.subList(messages.size() - MAX_MESSAGES, messages.size());
            }
            
            String json = objectMapper.writeValueAsString(messages);
            redisTemplate.opsForValue().set(key, json, MEMORY_TTL);
            
        } catch (Exception e) {
            log.error("添加记忆消息失败: {}", e.getMessage());
        }
    }
    
    /**
     * 获取记忆消息列表
     */
    public List<MemoryMessage> getMessages(String novelId, int chapterNum) {
        String key = MEMORY_KEY_PREFIX + novelId + ":" + chapterNum;
        
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json != null) {
                return Arrays.asList(objectMapper.readValue(json, MemoryMessage[].class));
            }
        } catch (Exception e) {
            log.error("获取记忆消息失败: {}", e.getMessage());
        }
        
        return new ArrayList<>();
    }
    
    /**
     * 获取格式化的记忆文本 - 用于注入到Prompt
     */
    public String getFormattedMemory(String novelId, int chapterNum) {
        List<MemoryMessage> messages = getMessages(novelId, chapterNum);
        if (messages.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("===对话记忆===\n");
        for (MemoryMessage msg : messages) {
            sb.append("[").append(msg.getRole()).append("] ");
            String content = msg.getContent();
            if (content.length() > 500) content = content.substring(0, 500) + "...";
            sb.append(content).append("\n");
        }
        return sb.toString();
    }
    
    /**
     * 清除记忆
     */
    public void clearMemory(String novelId, int chapterNum) {
        String key = MEMORY_KEY_PREFIX + novelId + ":" + chapterNum;
        redisTemplate.delete(key);
        log.info("清除记忆: novelId={}, chapter={}", novelId, chapterNum);
    }
    
    /**
     * 清除小说所有记忆
     */
    public void clearAllMemory(String novelId) {
        String pattern = MEMORY_KEY_PREFIX + novelId + ":*";
        // 使用 SCAN 增量遍历替代 KEYS 阻塞命令
        Set<String> keysToDelete = new HashSet<>();
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            var scanArgs = org.springframework.data.redis.core.ScanOptions.scanOptions()
                .match(pattern)
                .count(100)
                .build();
            try (var scanCursor = connection.scan(scanArgs)) {
                while (scanCursor.hasNext()) {
                    keysToDelete.add(new String(scanCursor.next()));
                }
            }
            return null;
        });
        if (!keysToDelete.isEmpty()) {
            redisTemplate.delete(keysToDelete);
            log.info("清除小说所有记忆: novelId={}, keys={}", novelId, keysToDelete.size());
        }
    }
    
    @Data
    public static class MemoryMessage {
        private String role;
        private String content;
        private long timestamp;
        
        public MemoryMessage() {}
        
        public MemoryMessage(String role, String content, long timestamp) {
            this.role = role;
            this.content = content;
            this.timestamp = timestamp;
        }
    }
}
