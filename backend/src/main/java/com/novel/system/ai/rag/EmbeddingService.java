package com.novel.system.ai.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import com.novel.system.config.LangChainConfig;
import com.novel.system.entity.mysql.LlmConfig;
import com.novel.system.service.llm.LlmConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    
    /**
     * 将单个文本转为向量
     */
    public float[] embed(String text) {
        EmbeddingModel model = getEmbeddingModel();
        if (model == null) {
            log.warn("未找到Embedding模型，返回空向量");
            return new float[1536];
        }
        
        // 截断超长文本
        String truncated = text.length() > 8000 ? text.substring(0, 8000) : text;
        Embedding embedding = model.embed(truncated).content();
        return embedding.vector();
    }
    
    /**
     * 批量将文本转为向量
     */
    public List<float[]> embedAll(List<String> texts) {
        EmbeddingModel model = getEmbeddingModel();
        if (model == null) {
            log.warn("未找到Embedding模型，返回空向量列表");
            List<float[]> result = new ArrayList<>();
            for (int i = 0; i < texts.size(); i++) {
                result.add(new float[1536]);
            }
            return result;
        }
        
        List<TextSegment> segments = new ArrayList<>();
        for (String text : texts) {
            String truncated = text.length() > 8000 ? text.substring(0, 8000) : text;
            segments.add(TextSegment.from(truncated));
        }
        
        List<Embedding> embeddings = model.embedAll(segments).content();
        List<float[]> result = new ArrayList<>();
        for (Embedding e : embeddings) {
            result.add(e.vector());
        }
        return result;
    }
    
    /**
     * 计算两个向量的余弦相似度
     */
    public static double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) return 0.0;
        
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        
        if (normA == 0 || normB == 0) return 0.0;
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
    
    /**
     * 获取EmbeddingModel
     */
    private EmbeddingModel getEmbeddingModel() {
        try {
            // 修复：使用 getAnyActiveConfig 获取任意活跃配置
            LlmConfig config = llmConfigService.getAnyActiveConfig("all");
            if (config == null) return null;
            return langChainConfig.createEmbeddingModel(config);
        } catch (Exception e) {
            log.error("获取EmbeddingModel失败: {}", e.getMessage());
            return null;
        }
    }
}
