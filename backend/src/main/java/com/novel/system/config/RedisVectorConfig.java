package com.novel.system.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

/**
 * Redis Stack 向量数据库配置
 */
@Data
@Configuration
public class RedisVectorConfig {
    
    @Value("${redis-stack.vector.index-name:novel_summary_idx}")
    private String indexName;
    
    @Value("${redis-stack.vector.dimension:1024}")
    private int dimension;
    
    @Value("${redis-stack.vector.distance-metric:cosine}")
    private String distanceMetric;
}
