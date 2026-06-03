package com.novel.system.ai.rag;

import java.util.List;
import java.util.Map;

/**
 * 通用向量存储服务接口
 * 不绑定具体存储实现（Redis Stack / Milvus / Qdrant 等）
 */
public interface VectorStoreService {

    /**
     * 确保集合/索引存在（启动时调用，幂等操作）
     */
    void ensureCollection();

    /**
     * 删除集合/索引
     */
    void dropCollection();

    /**
     * 存储向量
     * @param key 唯一标识
     * @param vector 向量数据
     * @param metadata 元数据
     */
    void saveVector(String key, float[] vector, Map<String, Object> metadata);

    /**
     * 向量相似度搜索
     * @param queryVector 查询向量
     * @param topK 返回数量
     * @param filter 过滤条件（格式由实现层定义）
     * @return 搜索结果列表
     */
    List<VectorSearchResult> search(float[] queryVector, int topK, String filter);

    /**
     * 删除向量
     * @param key 唯一标识
     */
    void deleteVector(String key);

    /**
     * 搜索结果
     */
    class VectorSearchResult {
        private String key;
        private double score;
        private Map<String, Object> metadata;

        public VectorSearchResult() {}

        public VectorSearchResult(String key, double score, Map<String, Object> metadata) {
            this.key = key;
            this.score = score;
            this.metadata = metadata;
        }

        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
        public double getScore() { return score; }
        public void setScore(double score) { this.score = score; }
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    }
}
