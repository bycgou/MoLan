package com.novel.system.ai.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import com.novel.system.config.LangChainConfig;
import com.novel.system.config.RedisVectorConfig;
import com.novel.system.entity.mysql.LlmConfig;
import com.novel.system.service.llm.LlmConfigService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 向量化服务 - 基于LangChain4j EmbeddingModel
 * 将文本转为向量，用于RAG语义检索
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {
    
    private final LangChainConfig langChainConfig;
    private final LlmConfigService llmConfigService;
    private final RedisVectorConfig vectorConfig;

    @Value("${embedding.max-input-chars:2000}")
    private int maxInputChars;

    @PostConstruct
    public void init() {
        log.info("EmbeddingService初始化: 向量维度={}, 最大输入字符数={}",
                 vectorConfig.getDimension(), maxInputChars);
    }

    /**
     * 将单个文本转为向量
     */
    public float[] embed(String text) {
        EmbeddingModel model = getEmbeddingModel();
        if (model == null) {
            throw new IllegalStateException(
                "未配置Embedding模型，请在模型管理中添加 usageType=embedding 的配置");
        }

        // 截断超长文本（DashScope text-embedding-v2 上限 2048 tokens）
        String truncated = text.length() > maxInputChars ? text.substring(0, maxInputChars) : text;
        long start = System.currentTimeMillis();
        Embedding embedding = model.embed(truncated).content();
        long elapsed = System.currentTimeMillis() - start;

        float[] vector = embedding.vector();
        validateVector(vector);

        log.info("Embedding完成: 文本长度={}, 向量维度={}, 耗时={}ms",
                 truncated.length(), vector.length, elapsed);
        return vector;
    }
    
    /**
     * 批量将文本转为向量
     */
    public List<float[]> embedAll(List<String> texts) {
        EmbeddingModel model = getEmbeddingModel();
        if (model == null) {
            throw new IllegalStateException(
                "未配置Embedding模型，请在模型管理中添加 usageType=embedding 的配置");
        }

        List<TextSegment> segments = new ArrayList<>();
        for (String text : texts) {
            String truncated = text.length() > maxInputChars ? text.substring(0, maxInputChars) : text;
            segments.add(TextSegment.from(truncated));
        }

        List<Embedding> embeddings = model.embedAll(segments).content();
        List<float[]> result = new ArrayList<>();
        for (Embedding e : embeddings) {
            float[] vector = e.vector();
            validateVector(vector);
            result.add(vector);
        }
        return result;
    }
    
    /**
     * 校验向量有效性：非null、非空、非全零
     * @param vector 待校验向量
     * @throws IllegalArgumentException 向量无效时
     */
    public static void validateVector(float[] vector) {
        if (vector == null) {
            throw new IllegalArgumentException("向量为null");
        }
        if (vector.length == 0) {
            throw new IllegalArgumentException("向量为空数组");
        }
        boolean allZero = true;
        for (float v : vector) {
            if (v != 0.0f) {
                allZero = false;
                break;
            }
        }
        if (allZero) {
            throw new IllegalArgumentException("向量全为零，Embedding模型可能未正确配置");
        }
    }

    /**
     * 获取EmbeddingModel
     * 优先查找 usageType=embedding 的配置，找不到再回退到 all
     */
    private EmbeddingModel getEmbeddingModel() {
        try {
            // 1. 优先查找专门配置的向量化模型
            LlmConfig config = llmConfigService.getAnyActiveConfig("embedding");
            if (config != null) {
                log.info("使用专用向量化模型: {}", config.getModelName());
                return langChainConfig.createEmbeddingModel(config);
            }
            // 2. 回退到 all 用途的配置
            config = llmConfigService.getAnyActiveConfig("all");
            if (config == null) {
                log.warn("未找到任何活跃LLM配置，返回null");
                return null;
            }
            log.warn("未找到usageType=embedding的配置，回退使用聊天模型配置: {}", config.getModelName());
            return langChainConfig.createEmbeddingModel(config);
        } catch (Exception e) {
            log.error("获取EmbeddingModel失败", e);
            return null;
        }
    }
}
