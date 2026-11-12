package com.novel.system.ai.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.novel.system.config.RedisKeyProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StateCheckpointServiceTest {

    private RedisTemplate<String, String> redisTemplate;
    private StateCheckpointService service;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(RedisTemplate.class);
        when(redisTemplate.opsForValue()).thenReturn(mock(org.springframework.data.redis.core.ValueOperations.class));
        service = new StateCheckpointService(redisTemplate, new ObjectMapper().registerModule(new JavaTimeModule()), new RedisKeyProvider("xpb:"));
    }

    @Test
    void checkpointKeyUsesConfiguredPrefix() {
        NovelAgentState state = NovelAgentState.builder()
                .novelId("n1").chapterNum(3).build();
        service.saveCheckpoint("writing", state);

        var valueOps = redisTemplate.opsForValue();
        org.mockito.Mockito.verify(valueOps).set(
                org.mockito.ArgumentMatchers.eq("xpb:novel:checkpoint:n1:3:writing"),
                anyString(), any(Duration.class));
    }

    @Test
    void loadLatestCheckpointPicksNodeByNameNotChapterNumber() {
        when(redisTemplate.keys("xpb:novel:checkpoint:n1:3:*"))
                .thenReturn(java.util.Set.of(
                        "xpb:novel:checkpoint:n1:3:writing",
                        "xpb:novel:checkpoint:n1:3:outline"));

        var valueOps = redisTemplate.opsForValue();
        when(valueOps.get("xpb:novel:checkpoint:n1:3:writing"))
                .thenReturn("{\"nodeName\":\"writing\",\"stateJson\":\"{}\",\"savedAt\":\"2026-01-01T00:00:00Z\",\"novelId\":\"n1\",\"chapterNum\":3}");
        when(valueOps.get("xpb:novel:checkpoint:n1:3:outline"))
                .thenReturn("{\"nodeName\":\"outline\",\"stateJson\":\"{}\",\"savedAt\":\"2026-01-01T00:00:00Z\",\"novelId\":\"n1\",\"chapterNum\":3}");

        Optional<StateSnapshot> snap = service.loadLatestCheckpoint("n1", 3);
        assertTrue(snap.isPresent());
        // 管线顺序 outline → writing → audit_router，取最靠后的检查点
        assertEquals("writing", snap.get().getNodeName());
    }
}
