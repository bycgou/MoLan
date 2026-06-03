# Milvus 向量数据库迁移设计

> 日期: 2026-06-03
> 状态: 设计完成，待实现

## 背景

墨澜项目当前使用 Redis Stack（RediSearch FT.CREATE/FT.SEARCH + KNN）作为向量数据库，存储章节摘要的 embedding 用于 RAG Layer 6 向量检索。随着数据规模增长和功能需求升级，计划迁移到 Milvus 以获得更好的性能、更丰富的功能和更专业的向量检索能力。

### 迁移动机

- **性能/规模**: Milvus 的 HNSW/IVF 索引更适合大规模向量检索
- **功能需求**: 分区、多向量字段、标量过滤、集合级权限等高级功能
- **运维/部署**: Milvus Standalone Docker 部署简洁，与现有基础设施风格一致
- **技术探索**: 为未来扩展做技术储备

### 迁移策略

**完全替换** — 直接替换 Redis Stack 向量检索，一步到位。

---

## 架构设计

### 接口层：VectorStoreService

将 `RedisVectorService` 重命名为通用的 `VectorStoreService`，语义不绑定任何具体存储。

```java
package com.novel.system.ai.rag;

public interface VectorStoreService {
    /** 确保集合/索引存在（启动时调用，幂等） */
    void ensureCollection();

    /** 删除集合/索引 */
    void dropCollection();

    /** 存储向量 */
    void saveVector(String key, float[] vector, Map<String, Object> metadata);

    /** 向量相似度搜索 */
    List<VectorSearchResult> search(float[] queryVector, int topK, String filter);

    /** 删除向量 */
    void deleteVector(String key);

    /** 搜索结果 */
    class VectorSearchResult {
        private String key;
        private double score;  // 相似度，范围 [-1, 1]（COSINE: 1=完全相同, 0=正交, -1=完全相反）
        private Map<String, Object> metadata;
        // getter/setter 省略
    }
}
```

**变化点**:
- `createIndex()` 合并到 `ensureCollection()`（Milvus 的 collection 创建即包含索引）
- `ensureIndex()` → `ensureCollection()`
- `dropIndex()` → `dropCollection()`
- `VectorSearchResult` 内部类结构不变，修正 score 范围注释为 [-1, 1]
- filter 参数语义保持，由实现层转换为目标存储的查询语法

### 实现层：MilvusVectorStoreServiceImpl

#### Maven 依赖

```xml
<dependency>
    <groupId>io.milvus</groupId>
    <artifactId>milvus-sdk-java</artifactId>
    <version>2.5.4</version>
</dependency>
```

#### Collection Schema

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` (主键) | VarChar | 原 Redis key（如 `novelId:ch1`） |
| `novelId` | VarChar | 小说 ID，**同时作为 Partition Key** |
| `chapterNum` | Int64 | 章节号 |
| `content` | VarChar | 章节摘要文本 |
| `keywords` | VarChar | 关键词（逗号分隔） |
| `priority` | Int64 | 优先级 |
| `embedding` | FloatVector(1024) | 向量字段 |

#### 索引策略

- **向量索引**: HNSW，COSINE 庶量，M=16，efConstruction=256
- **标量索引**: `novelId` 建索引加速过滤

#### 分区策略

使用 **Partition Key**（Milvus 2.5+ 支持），将 `novelId` 设为 partition key。Milvus 自动按 partition key 值分配数据到内部分区，无需手动创建/管理分区，也不受 4096 分区数量限制。

- 查询时 Milvus 自动按 partition key 过滤，性能等同于手动分区
- 小说删除时，通过 `expr: novelId == "xxx"` 批量删除对应数据即可

#### 配置类

```java
@ConfigurationProperties(prefix = "milvus")
public class MilvusVectorConfig {
    private String uri = "http://localhost:19530";
    private String collectionName = "novel_summary";
    private int dimension = 1024;
    private String metricType = "COSINE";

    // HNSW 参数
    private int hnswM = 16;
    private int hnswEfConstruction = 256;

    // 连接池参数
    private int connectTimeoutMs = 10000;
    private int keepAliveTimeMs = 55000;
    private int maxIdleConnections = 10;
    private int maxConnectionPoolSize = 60;
}
```

#### search 方法实现要点

1. 将 filter（`novelId:xxx`）转换为 Milvus 表达式：`novelId == "xxx"`
2. 调用 `MilvusClient.search()`，Milvus 按 partition key 自动限定搜索范围
3. Milvus COSINE 度量返回的距离范围 [0, 2]（0=完全相同，2=完全相反），转换为相似度：`similarity = 1 - distance`，结果范围 [-1, 1]
4. 搜索失败时记录 ERROR 日志，返回空列表（上层 hybridSearch 的 BM25 分支仍可工作）

---

## 调用方改造

### 需要修改的文件

| 文件 | 改动 |
|------|------|
| `RedisVectorService.java` | **删除**，替换为 `VectorStoreService.java` |
| `RedisVectorServiceImpl.java` | **删除** |
| `RedisVectorConfig.java` | **删除**，替换为 `MilvusVectorConfig.java` |
| `MilvusVectorStoreServiceImpl.java` | **新建**，实现 `VectorStoreService` |
| `MilvusVectorConfig.java` | **新建** |
| `RagServiceImpl.java` | `RedisVectorService` → `VectorStoreService` |
| `application.yml` | 移除 `redis-stack.vector.*`，新增 `milvus.*` 配置 |
| `docker-compose.yml` | `redis/redis-stack:latest` 替换为 `redis:7-alpine` + 新增 Milvus Standalone |

**不需要修改的文件**:
- `RagTool.java` — 只依赖 `RagService` 接口，不直接引用 `RedisVectorService`
- `RagMcpTools.java` — 同上
- `EmbeddingService.java` — 不变
- 前端 — 无改动

### docker-compose.yml 变更

**替换**: `redis/redis-stack:latest` → `redis:7-alpine`

```yaml
redis:
  image: redis:7-alpine
  ports:
    - "6379:6379"
  volumes:
    - redis-data:/data
```

端口 6379 不变，`RedisTemplate` 相关代码零改动。Redis 不再提供向量检索能力（已由 Milvus 接管），仅用于缓存/会话。

**新增**:
```yaml
milvus:
  image: milvusdb/milvus:v2.5.4
  ports:
    - "19530:19530"
    - "9091:9091"
  environment:
    ETCD_USE_EMBED: "true"
    COMMON_STORAGETYPE: "local"
  volumes:
    - milvus-data:/var/lib/milvus
  command: ["milvus", "run", "standalone"]
```

---

## 数据迁移方案

### 策略：从 MongoDB 重新灌入

MongoDB 的 `ChapterSummary` 文档中保留了 `embedding` 字段（`List<Float>`），可以直接读取并写入 Milvus，无需重新调用 LLM 生成 embedding。

### 迁移步骤

1. **启动 Milvus**: `docker-compose up -d milvus`
2. **运行迁移脚本**: 启动应用时自动检测 Milvus collection 是否为空，若为空则触发一次性迁移
3. **迁移逻辑**:
   - 从 MongoDB 查询所有 `ChapterSummary` 文档（有 embedding 字段的）
   - 转换为 Milvus insert 格式（key, novelId, chapterNum, content, keywords, priority, embedding）
   - 批量写入 Milvus（batch size = 100）
   - 记录迁移进度，支持断点续传
4. **验证**: 迁移完成后，随机抽取几条记录对比 MongoDB embedding 和 Milvus 中的 embedding 一致性

### 迁移触发方式

在 `MilvusVectorStoreServiceImpl.ensureCollection()` 中添加迁移检查逻辑：

```java
@PostConstruct
public void init() {
    ensureCollection();
    migrateFromMongoDbIfNeeded();
}
```

迁移完成后写入一个标记（Milvus 中的 `_migration_done` meta 或 MongoDB 中的标记文档），避免重复迁移。

---

## 错误处理

| 场景 | 处理方式 |
|------|----------|
| Milvus 连接失败 | 启动时 `ensureCollection()` 失败记录 WARN 日志，不阻断应用启动 |
| 写入失败 | 抛出 RuntimeException，由上层 `RagServiceImpl.saveSummary()` 的 try-catch 处理 |
| 搜索失败 | 记录 ERROR 日志，返回空列表（BM25 分支仍可工作） |
| Collection 不存在 | `ensureCollection()` 自动创建（幂等） |
| 向量维度不匹配 | 记录 WARN 日志，**不自动 drop**（避免数据丢失），需要人工介入处理 |
| 数据迁移失败 | 记录失败的文档 ID，支持下次启动时断点续传 |

---

## 回滚方案

如果 Milvus 出现问题需要回退到 Redis Stack：

1. **docker-compose.yml**: 恢复 `redis/redis-stack:latest`，移除 `milvus` 服务
2. **代码**: `git revert` 本次迁移的 commit
3. **数据**: Redis Stack 中的向量数据在迁移期间不删除，回滚后可直接使用
4. **建议**: 迁移后保留 Redis Stack 数据至少 7 天，确认 Milvus 稳定后再清理

---

## 测试策略

1. **单元测试**: Mock `MilvusClient`，测试 `MilvusVectorStoreServiceImpl` 的逻辑
2. **集成测试**: Testcontainers 启动 Milvus Standalone，测试完整 CRUD + 搜索流程
3. **手动验证**: 本地 docker-compose 启动后，通过现有 API 生成一章 → 触发 saveSummary → 调用 hybridSearch 验证检索结果

---

## 不在范围内

- MongoDB 中 `ChapterSummary.embedding` 字段保留不变（作为迁移数据源和备份）
- BM25 搜索逻辑不变（仍走 MongoDB `$text` 查询）
- RRF 融合和 rerank 逻辑不变
- EmbeddingService 不变（仍使用 LangChain4j OpenAiEmbeddingModel）
- 前端无改动
