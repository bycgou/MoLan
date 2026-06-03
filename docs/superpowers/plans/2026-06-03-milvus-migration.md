# Milvus 向量数据库迁移 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将墨澜项目的向量数据库从 Redis Stack 迁移到 Milvus Standalone，提升向量检索的性能和可扩展性。

**Architecture:** 重命名 `RedisVectorService` 接口为 `VectorStoreService`，新建 `MilvusVectorStoreServiceImpl` 实现。使用 Milvus Partition Key（`novelId`）自动分区，HNSW COSINE 索引。从 MongoDB `ChapterSummary.embedding` 灌入历史数据。Redis Stack 降级为普通 Redis 用于缓存。

**Tech Stack:** Java 17, Spring Boot 3.2, Milvus SDK Java 2.5.4, Docker Compose

---

## File Structure

| 文件 | 操作 | 说明 |
|------|------|------|
| `backend/src/main/java/com/novel/system/ai/rag/VectorStoreService.java` | **新建** | 通用向量存储接口（替代 RedisVectorService） |
| `backend/src/main/java/com/novel/system/ai/rag/VectorStoreService.java` 内部类 `VectorSearchResult` | **新建** | 搜索结果 DTO |
| `backend/src/main/java/com/novel/system/config/MilvusVectorConfig.java` | **新建** | Milvus 配置类 |
| `backend/src/main/java/com/novel/system/ai/rag/MilvusVectorStoreServiceImpl.java` | **新建** | Milvus 实现，含数据迁移逻辑 |
| `backend/src/main/java/com/novel/system/ai/rag/RedisVectorService.java` | **删除** | 旧接口 |
| `backend/src/main/java/com/novel/system/ai/rag/RedisVectorServiceImpl.java` | **删除** | 旧实现 |
| `backend/src/main/java/com/novel/system/config/RedisVectorConfig.java` | **删除** | 旧配置 |
| `backend/src/main/java/com/novel/system/ai/rag/RagServiceImpl.java` | **修改** | 引用改为 VectorStoreService |
| `backend/src/main/java/com/novel/system/ai/rag/EmbeddingService.java` | **修改** | 引用改为 MilvusVectorConfig |
| `backend/pom.xml` | **修改** | 添加 milvus-sdk-java 依赖 |
| `backend/src/main/resources/application.yml` | **修改** | 替换 redis-stack.vector.* 为 milvus.* |
| `docker-compose.yml` | **修改** | redis-stack → redis:7-alpine + milvus |

---

### Task 1: 添加 Milvus SDK 依赖

**Files:**
- Modify: `backend/pom.xml:80`（在 LangChain4j 依赖之后添加）

- [ ] **Step 1: 在 pom.xml 中添加 milvus-sdk-java 依赖**

在 `backend/pom.xml` 的 `<dependencies>` 中，在 LangChain4j 依赖块之后添加：

```xml
<!-- Milvus 向量数据库 -->
<dependency>
    <groupId>io.milvus</groupId>
    <artifactId>milvus-sdk-java</artifactId>
    <version>2.5.4</version>
</dependency>
```

- [ ] **Step 2: 验证依赖下载**

Run: `cd D:/dev/moyun/backend && ./mvnw dependency:resolve -Dartifact=io.milvus:milvus-sdk-java:2.5.4`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
cd D:/dev/moyun
git add backend/pom.xml
git commit -m "deps: add milvus-sdk-java 2.5.4 dependency"
```

---

### Task 2: 创建 MilvusVectorConfig 配置类

**Files:**
- Create: `backend/src/main/java/com/novel/system/config/MilvusVectorConfig.java`

- [ ] **Step 1: 创建 MilvusVectorConfig.java**

```java
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
```

- [ ] **Step 2: 验证编译**

Run: `cd D:/dev/moyun/backend && ./mvnw compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
cd D:/dev/moyun
git add backend/src/main/java/com/novel/system/config/MilvusVectorConfig.java
git commit -m "feat: add MilvusVectorConfig configuration class"
```

---

### Task 3: 创建 VectorStoreService 接口

**Files:**
- Create: `backend/src/main/java/com/novel/system/ai/rag/VectorStoreService.java`

- [ ] **Step 1: 创建 VectorStoreService.java**

```java
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
```

- [ ] **Step 2: 验证编译**

Run: `cd D:/dev/moyun/backend && ./mvnw compile -q`
Expected: BUILD SUCCESS（旧接口仍存在，不冲突）

- [ ] **Step 3: Commit**

```bash
cd D:/dev/moyun
git add backend/src/main/java/com/novel/system/ai/rag/VectorStoreService.java
git commit -m "feat: add VectorStoreService interface (replaces RedisVectorService)"
```

---

### Task 4: 创建 MilvusVectorStoreServiceImpl 实现

**Files:**
- Create: `backend/src/main/java/com/novel/system/ai/rag/MilvusVectorStoreServiceImpl.java`

- [ ] **Step 1: 创建 MilvusVectorStoreServiceImpl.java**

```java
package com.novel.system.ai.rag;

import com.novel.system.config.MilvusVectorConfig;
import com.novel.system.entity.mongo.ChapterSummary;
import com.novel.system.repository.mongo.ChapterSummaryRepository;
import io.milvus.client.MilvusClientV2;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.collection.*;
import io.milvus.param.control.QueryCountParam;
import io.milvus.param.dml.*;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.response.SearchResultsWrapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Milvus 向量存储服务实现
 */
@Slf4j
@Service
public class MilvusVectorStoreServiceImpl implements VectorStoreService {

    private final MilvusVectorConfig config;
    private final ChapterSummaryRepository chapterSummaryRepository;
    private final MongoTemplate mongoTemplate;
    private MilvusClientV2 client;

    public MilvusVectorStoreServiceImpl(MilvusVectorConfig config,
                                         ChapterSummaryRepository chapterSummaryRepository,
                                         MongoTemplate mongoTemplate) {
        this.config = config;
        this.chapterSummaryRepository = chapterSummaryRepository;
        this.mongoTemplate = mongoTemplate;
    }

    @PostConstruct
    public void init() {
        try {
            ConnectParam connectParam = ConnectParam.newBuilder()
                .withUri(config.getUri())
                .withConnectTimeoutMs(config.getConnectTimeoutMs())
                .withKeepAliveTimeMs(config.getKeepAliveTimeMs())
                .withMaxIdleConnectionCount(config.getMaxIdleConnections())
                .withMaxConnectionPoolSize(config.getMaxConnectionPoolSize())
                .build();
            this.client = new MilvusClientV2(connectParam);
            log.info("Milvus 客户端连接成功: {}", config.getUri());
            ensureCollection();
        } catch (Exception e) {
            log.warn("Milvus 连接失败，向量检索将不可用: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void destroy() {
        if (client != null) {
            try {
                client.close();
                log.info("Milvus 客户端已关闭");
            } catch (Exception e) {
                log.warn("关闭 Milvus 客户端失败: {}", e.getMessage());
            }
        }
    }

    @Override
    public void ensureCollection() {
        if (client == null) {
            log.warn("Milvus 客户端未初始化，跳过 ensureCollection");
            return;
        }

        try {
            // 检查 collection 是否存在
            Boolean hasCollection = client.hasCollection(
                HasCollectionParam.newBuilder()
                    .withCollectionName(config.getCollectionName())
                    .build()
            ).getData();

            if (Boolean.TRUE.equals(hasCollection)) {
                log.debug("Milvus collection 已存在: {}", config.getCollectionName());
                return;
            }

            // 创建 collection schema
            CreateCollectionParam createParam = CreateCollectionParam.newBuilder()
                .withCollectionName(config.getCollectionName())
                .withDescription("Novel chapter summary vectors for RAG retrieval")
                .withShardsNum(1)
                .build();

            // 使用 MilvusClientV2 的简化创建方式
            // 先创建 schema，再建索引，再加载
            List<FieldType> fieldTypes = new ArrayList<>();

            // 主键字段
            FieldType idField = FieldType.newBuilder()
                .withName("id")
                .withDataType(io.milvus.common.DataType.VarChar)
                .withMaxLength(256)
                .withPrimaryKey(true)
                .withAutoID(false)
                .build();
            fieldTypes.add(idField);

            // novelId - 同时作为 Partition Key
            FieldType novelIdField = FieldType.newBuilder()
                .withName("novelId")
                .withDataType(io.milvus.common.DataType.VarChar)
                .withMaxLength(128)
                .withPartitionKey(true)
                .build();
            fieldTypes.add(novelIdField);

            // chapterNum
            FieldType chapterNumField = FieldType.newBuilder()
                .withName("chapterNum")
                .withDataType(io.milvus.common.DataType.Int64)
                .build();
            fieldTypes.add(chapterNumField);

            // content
            FieldType contentField = FieldType.newBuilder()
                .withName("content")
                .withDataType(io.milvus.common.DataType.VarChar)
                .withMaxLength(65535)
                .build();
            fieldTypes.add(contentField);

            // keywords
            FieldType keywordsField = FieldType.newBuilder()
                .withName("keywords")
                .withDataType(io.milvus.common.DataType.VarChar)
                .withMaxLength(1024)
                .build();
            fieldTypes.add(keywordsField);

            // priority
            FieldType priorityField = FieldType.newBuilder()
                .withName("priority")
                .withDataType(io.milvus.common.DataType.Int64)
                .build();
            fieldTypes.add(priorityField);

            // embedding 向量字段
            FieldType embeddingField = FieldType.newBuilder()
                .withName("embedding")
                .withDataType(io.milvus.common.DataType.FloatVector)
                .withDimension(config.getDimension())
                .build();
            fieldTypes.add(embeddingField);

            CollectionSchemaParam schemaParam = CollectionSchemaParam.newBuilder()
                .withFieldTypes(fieldTypes)
                .enableDynamicField(false)
                .build();

            CreateCollectionParam collectionParam = CreateCollectionParam.newBuilder()
                .withCollectionName(config.getCollectionName())
                .withSchema(schemaParam)
                .withShardsNum(1)
                .build();

            client.createCollection(collectionParam);
            log.info("Milvus collection 创建成功: {}", config.getCollectionName());

            // 创建 HNSW 向量索引
            client.createIndex(
                CreateIndexParam.newBuilder()
                    .withCollectionName(config.getCollectionName())
                    .withFieldName("embedding")
                    .withIndexType(IndexType.HNSW)
                    .withMetricType(MetricType.COSINE)
                    .withExtraParam(String.format("{\"M\":%d,\"efConstruction\":%d}",
                        config.getHnswM(), config.getHnswEfConstruction()))
                    .withSyncMode(Boolean.TRUE)
                    .build()
            );
            log.info("Milvus HNSW 索引创建成功");

            // 加载 collection 到内存
            client.loadCollection(
                LoadCollectionParam.newBuilder()
                    .withCollectionName(config.getCollectionName())
                    .build()
            );
            log.info("Milvus collection 已加载到内存");

        } catch (Exception e) {
            log.error("Milvus ensureCollection 失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public void dropCollection() {
        if (client == null) return;
        try {
            client.dropCollection(
                DropCollectionParam.newBuilder()
                    .withCollectionName(config.getCollectionName())
                    .build()
            );
            log.info("Milvus collection 已删除: {}", config.getCollectionName());
        } catch (Exception e) {
            log.error("删除 Milvus collection 失败: {}", e.getMessage());
        }
    }

    @Override
    public void saveVector(String key, float[] vector, Map<String, Object> metadata) {
        EmbeddingService.validateVector(vector);
        if (client == null) {
            log.warn("Milvus 客户端未初始化，跳过 saveVector: {}", key);
            return;
        }

        try {
            List<InsertParam.Field> fields = new ArrayList<>();
            fields.add(new InsertParam.Field("id", List.of(key)));
            fields.add(new InsertParam.Field("novelId",
                List.of(metadata.getOrDefault("novelId", "").toString())));
            fields.add(new InsertParam.Field("chapterNum",
                List.of(((Number) metadata.getOrDefault("chapterNum", 0)).longValue())));
            fields.add(new InsertParam.Field("content",
                List.of(metadata.getOrDefault("content", "").toString())));
            fields.add(new InsertParam.Field("keywords",
                List.of(metadata.getOrDefault("keywords", "").toString())));
            fields.add(new InsertParam.Field("priority",
                List.of(((Number) metadata.getOrDefault("priority", 3)).longValue())));
            fields.add(new InsertParam.Field("embedding", List.of(vector)));

            client.insert(
                InsertParam.newBuilder()
                    .withCollectionName(config.getCollectionName())
                    .withFields(fields)
                    .build()
            );

            log.info("Milvus 向量保存成功: key={}, dimension={}", key, vector.length);

        } catch (Exception e) {
            log.error("Milvus 保存向量失败: key={}, error={}", key, e.getMessage());
            throw new RuntimeException("Milvus 保存向量失败", e);
        }
    }

    @Override
    public List<VectorSearchResult> search(float[] queryVector, int topK, String filter) {
        EmbeddingService.validateVector(queryVector);
        if (client == null) {
            log.warn("Milvus 客户端未初始化，返回空结果");
            return Collections.emptyList();
        }

        try {
            // 将 filter 转换为 Milvus 表达式
            String expr = buildFilterExpr(filter);

            List<String> outputFields = List.of("novelId", "chapterNum", "content", "keywords", "priority");
            String searchParams = "{\"ef\":" + Math.max(topK * 2, 64) + "}";

            SearchParam searchParam = SearchParam.newBuilder()
                .withCollectionName(config.getCollectionName())
                .withVectors(List.of(queryVector))
                .withVectorFieldName("embedding")
                .withExpr(expr)
                .withTopK(topK)
                .withMetricType(MetricType.COSINE)
                .withOutFields(outputFields)
                .withParams(searchParams)
                .build();

            var response = client.search(searchParam);
            SearchResultsWrapper wrapper = new SearchResultsWrapper(response.getData().getResults());

            List<VectorSearchResult> results = new ArrayList<>();
            List<SearchResultsWrapper.IDScore> scores = wrapper.getIDScore(0);

            for (SearchResultsWrapper.IDScore score : scores) {
                String id = score.get("id").toString();
                // Milvus COSINE 距离: 0=完全相同, 2=完全相反
                // 转换为相似度: similarity = 1 - distance, 范围 [-1, 1]
                double distance = score.getScore();
                double similarity = 1.0 - distance;

                Map<String, Object> resultMetadata = new HashMap<>();
                resultMetadata.put("novelId", score.get("novelId") != null ? score.get("novelId").toString() : "");
                resultMetadata.put("chapterNum", score.get("chapterNum") != null ? ((Number) score.get("chapterNum")).intValue() : 0);
                resultMetadata.put("content", score.get("content") != null ? score.get("content").toString() : "");
                resultMetadata.put("keywords", score.get("keywords") != null ? score.get("keywords").toString() : "");

                results.add(new VectorSearchResult(id, similarity, resultMetadata));
            }

            log.info("Milvus 搜索完成: filter={}, topK={}, 返回数={}", filter, topK, results.size());
            return results;

        } catch (Exception e) {
            log.error("Milvus 搜索失败: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public void deleteVector(String key) {
        if (client == null) return;
        try {
            client.delete(
                DeleteParam.newBuilder()
                    .withCollectionName(config.getCollectionName())
                    .withExpr("id == \"" + key.replace("\"", "\\\"") + "\"")
                    .build()
            );
            log.debug("Milvus 向量删除成功: {}", key);
        } catch (Exception e) {
            log.error("Milvus 删除向量失败: key={}, error={}", key, e.getMessage());
        }
    }

    /**
     * 将 filter 字符串转换为 Milvus 布尔表达式
     * 支持格式: "@novelId:{xxx}" → "novelId == \"xxx\""
     */
    private String buildFilterExpr(String filter) {
        if (filter == null || filter.isEmpty()) {
            return "";
        }
        // 兼容 Redis 格式的 filter
        if (filter.startsWith("@novelId:{")) {
            String novelId = filter.substring(10, filter.length() - 1);
            return "novelId == \"" + novelId.replace("\"", "\\\"") + "\"";
        }
        // 已经是 Milvus 表达式格式
        return filter;
    }
}
```

- [ ] **Step 2: 验证编译**

Run: `cd D:/dev/moyun/backend && ./mvnw compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
cd D:/dev/moyun
git add backend/src/main/java/com/novel/system/ai/rag/MilvusVectorStoreServiceImpl.java
git commit -m "feat: add MilvusVectorStoreServiceImpl with HNSW COSINE index"
```

---

### Task 5: 添加 application.yml Milvus 配置

**Files:**
- Modify: `backend/src/main/resources/application.yml`

- [ ] **Step 1: 替换 redis-stack.vector 配置为 milvus 配置**

在 `application.yml` 中找到 `# Redis Stack 向量数据库配置` 段（约第 86-92 行），替换为：

```yaml
# Milvus 向量数据库配置
milvus:
  uri: http://localhost:19530
  collection-name: novel_summary
  dimension: 1024
  metric-type: COSINE
  hnsw-m: 16
  hnsw-ef-construction: 256
  connect-timeout-ms: 10000
  keep-alive-time-ms: 55000
  max-idle-connections: 10
  max-connection-pool-size: 60
```

- [ ] **Step 2: 验证配置格式**

Run: `cd D:/dev/moyun/backend && ./mvnw compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
cd D:/dev/moyun
git add backend/src/main/resources/application.yml
git commit -m "config: replace redis-stack.vector with milvus configuration"
```

---

### Task 6: 修改 EmbeddingService 引用

**Files:**
- Modify: `backend/src/main/java/com/novel/system/ai/rag/EmbeddingService.java:7,30,38`

- [ ] **Step 1: 修改 import 语句**

将第 7 行：
```java
import com.novel.system.config.RedisVectorConfig;
```
改为：
```java
import com.novel.system.config.MilvusVectorConfig;
```

- [ ] **Step 2: 修改字段注入**

将第 30 行：
```java
private final RedisVectorConfig vectorConfig;
```
改为：
```java
private final MilvusVectorConfig vectorConfig;
```

- [ ] **Step 3: 验证编译**

Run: `cd D:/dev/moyun/backend && ./mvnw compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
cd D:/dev/moyun
git add backend/src/main/java/com/novel/system/ai/rag/EmbeddingService.java
git commit -m "refactor: EmbeddingService uses MilvusVectorConfig instead of RedisVectorConfig"
```

---

### Task 7: 修改 RagServiceImpl 引用

**Files:**
- Modify: `backend/src/main/java/com/novel/system/ai/rag/RagServiceImpl.java:9,39,129,134`

- [ ] **Step 1: 修改 import 语句**

将第 9 行：
```java
import com.novel.system.ai.rag.RedisVectorService;
```
改为：
```java
import com.novel.system.ai.rag.VectorStoreService;
```

- [ ] **Step 2: 修改字段注入**

将第 39 行：
```java
private final RedisVectorService redisVectorService;
```
改为：
```java
private final VectorStoreService vectorStoreService;
```

- [ ] **Step 3: 修改 vectorSearch 方法中的引用**

将第 129-134 行：
```java
List<RedisVectorService.VectorSearchResult> results = 
    redisVectorService.search(queryVector, topK, filter);

// 获取对应的ChapterSummary
List<ChapterSummary> summaries = new ArrayList<>();
for (RedisVectorService.VectorSearchResult result : results) {
```
改为：
```java
List<VectorStoreService.VectorSearchResult> results = 
    vectorStoreService.search(queryVector, topK, filter);

// 获取对应的ChapterSummary
List<ChapterSummary> summaries = new ArrayList<>();
for (VectorStoreService.VectorSearchResult result : results) {
```

- [ ] **Step 4: 修改 saveSummary 方法中的引用**

将第 378 行：
```java
redisVectorService.saveVector(key, embedding, metadata);
```
改为：
```java
vectorStoreService.saveVector(key, embedding, metadata);
```

- [ ] **Step 5: 验证编译**

Run: `cd D:/dev/moyun/backend && ./mvnw compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
cd D:/dev/moyun
git add backend/src/main/java/com/novel/system/ai/rag/RagServiceImpl.java
git commit -m "refactor: RagServiceImpl uses VectorStoreService instead of RedisVectorService"
```

---

### Task 8: 删除旧 Redis 向量服务文件

**Files:**
- Delete: `backend/src/main/java/com/novel/system/ai/rag/RedisVectorService.java`
- Delete: `backend/src/main/java/com/novel/system/ai/rag/RedisVectorServiceImpl.java`
- Delete: `backend/src/main/java/com/novel/system/config/RedisVectorConfig.java`

- [ ] **Step 1: 删除 RedisVectorService.java**

```bash
rm backend/src/main/java/com/novel/system/ai/rag/RedisVectorService.java
```

- [ ] **Step 2: 删除 RedisVectorServiceImpl.java**

```bash
rm backend/src/main/java/com/novel/system/ai/rag/RedisVectorServiceImpl.java
```

- [ ] **Step 3: 删除 RedisVectorConfig.java**

```bash
rm backend/src/main/java/com/novel/system/config/RedisVectorConfig.java
```

- [ ] **Step 4: 验证编译**

Run: `cd D:/dev/moyun/backend && ./mvnw compile -q`
Expected: BUILD SUCCESS（所有引用已改为新接口/配置）

- [ ] **Step 5: Commit**

```bash
cd D:/dev/moyun
git add -A
git commit -m "refactor: remove RedisVectorService, RedisVectorServiceImpl, RedisVectorConfig"
```

---

### Task 9: 更新 docker-compose.yml

**Files:**
- Modify: `docker-compose.yml`

- [ ] **Step 1: 替换 redis-stack 为 redis:7-alpine 并添加 milvus**

将 `docker-compose.yml` 整个文件替换为：

```yaml
services:
  mysql:
    image: mysql:8.0
    container_name: moyun-mysql
    restart: unless-stopped
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:-123456}
      MYSQL_DATABASE: ${MYSQL_DATABASE:-moyun_novel_system}
    ports:
      - "3306:3306"
    volumes:
      - mysql-data:/var/lib/mysql
      - ./backend/moyun_novel_system.sql:/docker-entrypoint-initdb.d/init.sql:ro
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-u", "root", "-p${MYSQL_ROOT_PASSWORD:-123456}"]
      interval: 10s
      timeout: 5s
      retries: 5

  mongodb:
    image: mongo:7
    container_name: moyun-mongodb
    restart: unless-stopped
    ports:
      - "27017:27017"
    volumes:
      - mongo-data:/data/db
    healthcheck:
      test: ["CMD", "mongosh", "--eval", "db.adminCommand('ping')"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    container_name: moyun-redis
    restart: unless-stopped
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

  milvus:
    image: milvusdb/milvus:v2.5.4
    container_name: moyun-milvus
    restart: unless-stopped
    ports:
      - "19530:19530"
      - "9091:9091"
    environment:
      ETCD_USE_EMBED: "true"
      COMMON_STORAGETYPE: "local"
    volumes:
      - milvus-data:/var/lib/milvus
    command: ["milvus", "run", "standalone"]
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9091/healthz"]
      interval: 10s
      timeout: 5s
      retries: 5

volumes:
  mysql-data:
  mongo-data:
  redis-data:
  milvus-data:
```

- [ ] **Step 2: 验证 docker-compose 语法**

Run: `cd D:/dev/moyun && docker compose config --quiet`
Expected: 无输出（语法正确）

- [ ] **Step 3: Commit**

```bash
cd D:/dev/moyun
git add docker-compose.yml
git commit -m "infra: replace redis-stack with redis:7-alpine + milvus standalone"
```

---

### Task 10: 添加数据迁移逻辑

**Files:**
- Modify: `backend/src/main/java/com/novel/system/ai/rag/MilvusVectorStoreServiceImpl.java`

- [ ] **Step 1: 在 MilvusVectorStoreServiceImpl 中添加迁移方法**

在 `init()` 方法的 `ensureCollection()` 调用之后，添加迁移调用：

```java
@PostConstruct
public void init() {
    try {
        ConnectParam connectParam = ConnectParam.newBuilder()
            .withUri(config.getUri())
            .withConnectTimeoutMs(config.getConnectTimeoutMs())
            .withKeepAliveTimeMs(config.getKeepAliveTimeMs())
            .withMaxIdleConnectionCount(config.getMaxIdleConnections())
            .withMaxConnectionPoolSize(config.getMaxConnectionPoolSize())
            .build();
        this.client = new MilvusClientV2(connectParam);
        log.info("Milvus 客户端连接成功: {}", config.getUri());
        ensureCollection();
    } catch (Exception e) {
        log.warn("Milvus 连接失败，向量检索将不可用: {}", e.getMessage());
    }
}
```

在类中添加以下字段和迁移方法：

```java
private final ChapterSummaryRepository chapterSummaryRepository;
private final MongoTemplate mongoTemplate;

public MilvusVectorStoreServiceImpl(MilvusVectorConfig config,
                                     ChapterSummaryRepository chapterSummaryRepository,
                                     MongoTemplate mongoTemplate) {
    this.config = config;
    this.chapterSummaryRepository = chapterSummaryRepository;
    this.mongoTemplate = mongoTemplate;
}
```

在 `ensureCollection()` 方法末尾（`log.info("Milvus collection 已加载到内存")` 之后）添加：

```java
// 触发一次性数据迁移
migrateFromMongoDbIfNeeded();
```

添加迁移方法（注意：构造函数和 MongoDB 依赖已在 Task 4 中添加）：

```java
/**
 * 从 MongoDB ChapterSummary 迁移 embedding 数据到 Milvus（一次性）
 */
private void migrateFromMongoDbIfNeeded() {
    if (client == null) return;

    try {
        // 检查 Milvus 中是否已有数据
        QueryCountParam countParam = QueryCountParam.newBuilder()
            .withCollectionName(config.getCollectionName())
            .build();
        long count = client.queryCount(countParam).getData();
        if (count > 0) {
            log.info("Milvus collection 已有 {} 条数据，跳过迁移", count);
            return;
        }

        log.info("开始从 MongoDB 迁移 embedding 数据到 Milvus...");
        List<ChapterSummary> allSummaries = mongoTemplate.findAll(ChapterSummary.class);

        int migrated = 0;
        int skipped = 0;
        int batchSize = 100;

        List<InsertParam.Field> batchFields = new ArrayList<>();
        List<String> batchIds = new ArrayList<>();
        List<String> batchNovelIds = new ArrayList<>();
        List<Long> batchChapterNums = new ArrayList<>();
        List<String> batchContents = new ArrayList<>();
        List<String> batchKeywords = new ArrayList<>();
        List<Long> batchPriorities = new ArrayList<>();
        List<float[]> batchEmbeddings = new ArrayList<>();

        for (ChapterSummary summary : allSummaries) {
            if (summary.getEmbedding() == null || summary.getEmbedding().isEmpty()) {
                skipped++;
                continue;
            }

            String key = summary.getNovelId() + ":ch" + summary.getChapterNum();
            float[] vector = floatListToArray(summary.getEmbedding());

            batchIds.add(key);
            batchNovelIds.add(summary.getNovelId() != null ? summary.getNovelId() : "");
            batchChapterNums.add(summary.getChapterNum() != null ? summary.getChapterNum().longValue() : 0L);
            batchContents.add(summary.getContent() != null ? summary.getContent() : "");
            batchKeywords.add(summary.getKeywords() != null ? String.join(",", summary.getKeywords()) : "");
            batchPriorities.add(summary.getPriority() != null ? summary.getPriority().longValue() : 3L);
            batchEmbeddings.add(vector);

            if (batchIds.size() >= batchSize) {
                insertBatch(batchIds, batchNovelIds, batchChapterNums, batchContents,
                           batchKeywords, batchPriorities, batchEmbeddings);
                migrated += batchIds.size();
                log.info("迁移进度: {}/{}", migrated, allSummaries.size() - skipped);
                batchIds.clear();
                batchNovelIds.clear();
                batchChapterNums.clear();
                batchContents.clear();
                batchKeywords.clear();
                batchPriorities.clear();
                batchEmbeddings.clear();
            }
        }

        // 处理剩余数据
        if (!batchIds.isEmpty()) {
            insertBatch(batchIds, batchNovelIds, batchChapterNums, batchContents,
                       batchKeywords, batchPriorities, batchEmbeddings);
            migrated += batchIds.size();
        }

        log.info("数据迁移完成: 迁移 {} 条, 跳过 {} 条（无 embedding）", migrated, skipped);

    } catch (Exception e) {
        log.error("数据迁移失败: {}", e.getMessage(), e);
    }
}

private void insertBatch(List<String> ids, List<String> novelIds, List<Long> chapterNums,
                         List<String> contents, List<String> keywords, List<Long> priorities,
                         List<float[]> embeddings) {
    List<InsertParam.Field> fields = new ArrayList<>();
    fields.add(new InsertParam.Field("id", new ArrayList<>(ids)));
    fields.add(new InsertParam.Field("novelId", new ArrayList<>(novelIds)));
    fields.add(new InsertParam.Field("chapterNum", new ArrayList<>(chapterNums)));
    fields.add(new InsertParam.Field("content", new ArrayList<>(contents)));
    fields.add(new InsertParam.Field("keywords", new ArrayList<>(keywords)));
    fields.add(new InsertParam.Field("priority", new ArrayList<>(priorities)));
    fields.add(new InsertParam.Field("embedding", new ArrayList<>(embeddings)));

    client.insert(
        InsertParam.newBuilder()
            .withCollectionName(config.getCollectionName())
            .withFields(fields)
            .build()
    );
}

private float[] floatListToArray(List<Float> floatList) {
    float[] arr = new float[floatList.size()];
    for (int i = 0; i < floatList.size(); i++) {
        arr[i] = floatList.get(i);
    }
    return arr;
}
```

- [ ] **Step 2: 验证编译**

Run: `cd D:/dev/moyun/backend && ./mvnw compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
cd D:/dev/moyun
git add backend/src/main/java/com/novel/system/ai/rag/MilvusVectorStoreServiceImpl.java
git commit -m "feat: add one-time data migration from MongoDB to Milvus"
```

---

### Task 11: 端到端验证

- [ ] **Step 1: 启动 Milvus**

Run: `cd D:/dev/moyun && docker compose up -d milvus`
Expected: Container moyun-milvus started

- [ ] **Step 2: 等待 Milvus 就绪**

Run: `sleep 10 && docker compose exec milvus curl -f http://localhost:9091/healthz`
Expected: OK

- [ ] **Step 3: 启动后端**

Run: `cd D:/dev/moyun/backend && ./mvnw spring-boot:run`
Expected: 日志中出现 "Milvus 客户端连接成功" 和 "Milvus collection 创建成功"（首次）或 "Milvus collection 已存在"（后续）

- [ ] **Step 4: 验证向量检索**

通过现有 API 生成一章，然后检查日志中是否有 "Milvus 搜索完成" 和 "Milvus 向量保存成功" 的记录。

- [ ] **Step 5: Commit 最终状态**

```bash
cd D:/dev/moyun
git add -A
git commit -m "feat: complete Milvus vector DB migration from Redis Stack"
```
