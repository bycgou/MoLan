package com.novel.system.ai.rag;

import java.util.List;
import java.util.Map;

/**
 * Redis Stack 向量服务接口
 */
public interface RedisVectorService {
    
    /**
     * 创建向量索引
     */
    void createIndex();
    
    /**
     * 删除向量索引
     */
    void dropIndex();
    
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
     * @param filter 过滤条件
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
