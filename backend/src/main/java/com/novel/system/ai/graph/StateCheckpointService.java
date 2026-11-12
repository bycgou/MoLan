package com.novel.system.ai.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.system.config.RedisKeyProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "novel.checkpoint", name = "enabled", havingValue = "true")
public class StateCheckpointService {

    private static final Duration TTL = Duration.ofHours(1);
    private static final List<String> CHECKPOINT_ORDER = List.of(
            "outline", "writing", "audit_router");

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final RedisKeyProvider redisKeys;

    public StateCheckpointService(RedisTemplate<String, String> redisTemplate,
                                  ObjectMapper objectMapper,
                                  RedisKeyProvider redisKeys) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.redisKeys = redisKeys;
    }

    public void saveCheckpoint(String nodeName, NovelAgentState state) {
        try {
            String key = buildKey(state.getNovelId(), state.getChapterNum(), nodeName);
            StateSnapshot snapshot = new StateSnapshot(
                    nodeName,
                    objectMapper.writeValueAsString(state),
                    Instant.now(),
                    state.getNovelId(),
                    state.getChapterNum());
            String json = objectMapper.writeValueAsString(snapshot);
            redisTemplate.opsForValue().set(key, json, TTL);
            log.debug("[Checkpoint] 保存检查点: {}", key);
        } catch (Exception e) {
            log.warn("[Checkpoint] 保存检查点失败: {} - {}", nodeName, e.getMessage());
        }
    }

    public Optional<StateSnapshot> loadLatestCheckpoint(String novelId, int chapterNum) {
        try {
            String pattern = buildKey(novelId, chapterNum, "*");
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys == null || keys.isEmpty()) {
                return Optional.empty();
            }

            // 按管线顺序找最新的检查点
            String latestKey = null;
            int latestIndex = -1;
            for (String key : keys) {
                String nodeName = extractNodeName(key);
                int index = CHECKPOINT_ORDER.indexOf(nodeName);
                if (index > latestIndex) {
                    latestIndex = index;
                    latestKey = key;
                }
            }

            if (latestKey == null) return Optional.empty();

            String json = redisTemplate.opsForValue().get(latestKey);
            if (json == null) return Optional.empty();

            return Optional.of(objectMapper.readValue(json, StateSnapshot.class));
        } catch (Exception e) {
            log.warn("[Checkpoint] 加载检查点失败: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public void clearCheckpoints(String novelId, int chapterNum) {
        try {
            String pattern = buildKey(novelId, chapterNum, "*");
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.debug("[Checkpoint] 清理 {} 个检查点", keys.size());
            }
        } catch (Exception e) {
            log.warn("[Checkpoint] 清理检查点失败: {}", e.getMessage());
        }
    }

    private String buildKey(String novelId, int chapterNum, String nodeName) {
        return redisKeys.checkpoint() + novelId + ":" + chapterNum + ":" + nodeName;
    }

    private String extractNodeName(String key) {
        String[] parts = key.split(":");
        return parts.length == 0 ? "" : parts[parts.length - 1];
    }
}
