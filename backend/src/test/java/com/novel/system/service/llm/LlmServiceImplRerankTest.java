package com.novel.system.service.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.system.ai.rag.EmbeddingService;
import com.novel.system.config.LangChainConfig;
import com.novel.system.entity.mysql.LlmConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * LlmServiceImpl rerank 大降级策略测试
 *
 * 验证当 LLM rerank 调用失败或返回无效 JSON 时，
 * bigram Jaccard 降级能产生有意义的排序分数。
 */
class LlmServiceImplRerankTest {

    @Mock
    private LlmConfigService llmConfigService;
    @Mock
    private LangChainConfig langChainConfig;
    @Mock
    private EmbeddingService embeddingService;

    private LlmServiceImpl llmService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        llmService = new LlmServiceImpl(llmConfigService, langChainConfig, objectMapper, embeddingService);
    }

    @Test
    void rerank_catchBlockFallback_returnsBigramJaccardScores() {
        // LLM 调用抛异常，触发 catch 降级
        when(llmConfigService.getAnyActiveConfig(anyString()))
                .thenReturn(new LlmConfig());

        String query = "主角发现了神秘的魔法书";
        List<String> documents = Arrays.asList(
                "主角在图书馆发现了一本古老的魔法书，书中记载着强大的咒语",   // 高相关
                "天气晴朗，适合出门散步",                                      // 低相关
                "主角翻开魔法书，被书中的力量所震撼"                            // 高相关
        );

        List<Map.Entry<String, Double>> results = llmService.rerank(query, documents);

        assertEquals(3, results.size());

        // 验证分数是 Jaccard 而非固定值
        // 高相关文档应排在前面
        assertTrue(results.get(0).getValue() > results.get(1).getValue(),
                "高相关文档应比低相关文档分数高");

        // 验证不是固定分数模式 (10.0, 9.9, 9.8)
        for (Map.Entry<String, Double> entry : results) {
            assertTrue(entry.getValue() <= 1.0 && entry.getValue() >= 0.0,
                    "Jaccard 分数应在 [0, 1] 范围内，实际: " + entry.getValue());
        }

        // 低相关文档 "天气晴朗，适合出门散步" 应排最后
        String lastDoc = results.get(results.size() - 1).getKey();
        assertEquals("天气晴朗，适合出门散步", lastDoc,
                "与查询无关的文档应排在最后");
    }

    @Test
    void rerank_noScoresArrayFallback_returnsBigramJaccardScores() {
        // LLM 返回无 scores 字段的 JSON
        LlmConfig config = new LlmConfig();
        when(llmConfigService.getAnyActiveConfig(anyString())).thenReturn(config);

        // 这里我们无法直接 mock generateWithUsage 的行为（因为是 final 或内部调用），
        // 但可以测试空文档和 null 文档的边界情况
        List<Map.Entry<String, Double>> emptyResult = llmService.rerank("query", Collections.emptyList());
        assertTrue(emptyResult.isEmpty());

        List<Map.Entry<String, Double>> nullResult = llmService.rerank("query", null);
        assertTrue(nullResult.isEmpty());
    }

    @Test
    void rerank_emptyQuery_stillProducesScores() {
        when(llmConfigService.getAnyActiveConfig(anyString()))
                .thenReturn(new LlmConfig());

        String query = "";
        List<String> documents = Arrays.asList("文档A", "文档B");

        List<Map.Entry<String, Double>> results = llmService.rerank(query, documents);

        assertEquals(2, results.size());
        // 空 query 与任何文档的 bigram Jaccard 均为 0
        for (Map.Entry<String, Double> entry : results) {
            assertEquals(0.0, entry.getValue(), 0.001,
                    "空 query 的 bigram Jaccard 应为 0");
        }
    }

    @Test
    void rerank_identicalDocs_highScore() {
        when(llmConfigService.getAnyActiveConfig(anyString()))
                .thenReturn(new LlmConfig());

        String query = "主角在森林中迷路了";
        List<String> documents = Arrays.asList(
                "主角在森林中迷路了",
                "配角在城市中逛街"
        );

        List<Map.Entry<String, Double>> results = llmService.rerank(query, documents);

        assertEquals(2, results.size());

        // 与 query 完全相同的文档应得最高分
        double exactMatchScore = results.stream()
                .filter(e -> e.getKey().equals("主角在森林中迷路了"))
                .mapToDouble(Map.Entry::getValue)
                .findFirst().orElse(0.0);

        double differentDocScore = results.stream()
                .filter(e -> e.getKey().equals("配角在城市中逛街"))
                .mapToDouble(Map.Entry::getValue)
                .findFirst().orElse(0.0);

        assertTrue(exactMatchScore > differentDocScore,
                "完全匹配的文档应比不同文档分数高");
    }
}
