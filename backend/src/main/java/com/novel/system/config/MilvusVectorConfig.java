package com.novel.system.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Milvus 向量数据库配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "milvus")
public class MilvusVectorConfig {

    /** Milvus 服务地址 */
    private String uri = "http://localhost:19530";

    /** Collection 名称 */
    private String collectionName = "novel_summary";

    /** 向量维度 */
    private int dimension = 1024;

    /** 距离度量类型 */
    private String metricType = "COSINE";

    // ---- HNSW 索引参数 ----

    /** HNSW M 参数（每个节点的最大邻居数） */
    private int hnswM = 16;

    /** HNSW efConstruction 参数（构建时的搜索宽度） */
    private int hnswEfConstruction = 256;

    // ---- 连接池参数 ----

    /** 连接超时（毫秒） */
    private int connectTimeoutMs = 10000;

    /** Keepalive 时间（毫秒） */
    private int keepAliveTimeMs = 55000;

    /** 最大空闲连接数 */
    private int maxIdleConnections = 10;

    /** 连接池最大容量 */
    private int maxConnectionPoolSize = 60;
}
