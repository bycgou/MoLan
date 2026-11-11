package com.novel.system.ai.rag;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.novel.system.config.MilvusVectorConfig;
import com.novel.system.entity.mongo.ChapterSummary;
import com.novel.system.repository.mongo.ChapterSummaryRepository;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.*;
import io.milvus.v2.service.index.request.CreateIndexReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Milvus 向量存储服务实现（使用 Milvus SDK v2 API）
 */
@Slf4j
@Service
public class MilvusVectorStoreServiceImpl implements VectorStoreService {

    private static final java.util.regex.Pattern SAFE_ID_PATTERN =
        java.util.regex.Pattern.compile("^[a-zA-Z0-9\\-]+$");

    private final MilvusVectorConfig config;
    private final ChapterSummaryRepository chapterSummaryRepository;
    private final MongoTemplate mongoTemplate;
    private MilvusClientV2 client;
    private final Gson gson = new Gson();
    private volatile boolean degraded = false;

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
            ConnectConfig.ConnectConfigBuilder<?, ?> builder = ConnectConfig.builder()
                .uri(config.getUri())
                .connectTimeoutMs(config.getConnectTimeoutMs())
                .keepAliveTimeMs(config.getKeepAliveTimeMs());
            if (config.getUsername() != null && !config.getUsername().isBlank()) {
                builder.username(config.getUsername())
                    .password(config.getPassword() == null ? "" : config.getPassword());
            }
            if (config.getDatabase() != null && !config.getDatabase().isBlank()) {
                builder.dbName(config.getDatabase());
            }
            ConnectConfig connectConfig = builder.build();
            this.client = new MilvusClientV2(connectConfig);
            this.degraded = false;
            log.info("Milvus 客户端连接成功: {} (db={})", config.getUri(), config.getDatabase());
            ensureCollection();
        } catch (Exception e) {
            degraded = true;
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

    @Scheduled(fixedDelay = 300000)
    public void tryReconnect() {
        if (!degraded) return;
        try {
            init();
            if (client != null) degraded = false;
        } catch (Exception e) {
            log.warn("Milvus 重连失败: {}", e.getMessage());
        }
    }

    @Override
    public void ensureCollection() {
        if (client == null) {
            log.warn("Milvus 客户端未初始化，跳过 ensureCollection");
            return;
        }

        try {
            Boolean hasCollection = client.hasCollection(
                HasCollectionReq.builder()
                    .collectionName(config.getCollectionName())
                    .build()
            );

            if (Boolean.TRUE.equals(hasCollection)) {
                log.debug("Milvus collection 已存在: {}", config.getCollectionName());
                migrateFromMongoDbIfNeeded();
                return;
            }

            // 构建 collection schema
            CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder()
                .build();

            // 主键字段
            schema.addField(AddFieldReq.builder()
                .fieldName("id")
                .dataType(DataType.VarChar)
                .maxLength(256)
                .isPrimaryKey(true)
                .autoID(false)
                .build());

            // novelId - Partition Key
            schema.addField(AddFieldReq.builder()
                .fieldName("novelId")
                .dataType(DataType.VarChar)
                .maxLength(128)
                .isPartitionKey(true)
                .build());

            // chapterNum
            schema.addField(AddFieldReq.builder()
                .fieldName("chapterNum")
                .dataType(DataType.Int64)
                .build());

            // content
            schema.addField(AddFieldReq.builder()
                .fieldName("content")
                .dataType(DataType.VarChar)
                .maxLength(65535)
                .build());

            // keywords
            schema.addField(AddFieldReq.builder()
                .fieldName("keywords")
                .dataType(DataType.VarChar)
                .maxLength(1024)
                .build());

            // priority
            schema.addField(AddFieldReq.builder()
                .fieldName("priority")
                .dataType(DataType.Int64)
                .build());

            // embedding 向量字段
            schema.addField(AddFieldReq.builder()
                .fieldName("embedding")
                .dataType(DataType.FloatVector)
                .dimension(config.getDimension())
                .build());

            // 创建 HNSW 索引参数
            Map<String, Object> extraParams = new HashMap<>();
            extraParams.put("M", config.getHnswM());
            extraParams.put("efConstruction", config.getHnswEfConstruction());

            IndexParam indexParam = IndexParam.builder()
                .fieldName("embedding")
                .indexType(IndexParam.IndexType.HNSW)
                .metricType(IndexParam.MetricType.COSINE)
                .extraParams(extraParams)
                .build();

            // 创建 collection
            CreateCollectionReq createReq = CreateCollectionReq.builder()
                .collectionName(config.getCollectionName())
                .collectionSchema(schema)
                .indexParams(List.of(indexParam))
                .numShards(1)
                .build();

            client.createCollection(createReq);
            log.info("Milvus collection 创建成功: {}", config.getCollectionName());

            // 加载 collection 到内存
            client.loadCollection(LoadCollectionReq.builder()
                .collectionName(config.getCollectionName())
                .build());
            log.info("Milvus collection 已加载到内存");

            migrateFromMongoDbIfNeeded();

        } catch (Exception e) {
            log.error("Milvus ensureCollection 失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public void dropCollection() {
        if (client == null) return;
        try {
            client.dropCollection(DropCollectionReq.builder()
                .collectionName(config.getCollectionName())
                .build());
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
            // 先删除同 key 旧向量（upsert 语义：存在则替换）
            deleteVector(key);

            JsonObject data = new JsonObject();
            data.addProperty("id", key);
            data.addProperty("novelId", metadata.getOrDefault("novelId", "").toString());
            data.addProperty("chapterNum", ((Number) metadata.getOrDefault("chapterNum", 0)).longValue());
            data.addProperty("content", metadata.getOrDefault("content", "").toString());
            data.addProperty("keywords", metadata.getOrDefault("keywords", "").toString());
            data.addProperty("priority", ((Number) metadata.getOrDefault("priority", 3)).longValue());

            // Gson 不直接支持 float[]，需要转换为 JsonArray
            com.google.gson.JsonArray embeddingArray = new com.google.gson.JsonArray(vector.length);
            for (float f : vector) {
                embeddingArray.add(f);
            }
            data.add("embedding", embeddingArray);

            InsertReq insertReq = InsertReq.builder()
                .collectionName(config.getCollectionName())
                .data(List.of(data))
                .build();

            client.insert(insertReq);
            log.info("Milvus 向量保存成功: key={}, dimension={}", key, vector.length);

        } catch (Exception e) {
            log.error("Milvus 保存向量失败: key={}, error={}", key, e.getMessage());
            throw new RuntimeException("Milvus 保存向量失败", e);
        }
    }

    @Override
    public List<VectorSearchResult> search(float[] queryVector, int topK, String filter) {
        EmbeddingService.validateVector(queryVector);
        if (degraded) {
            log.warn("Milvus 处于降级状态，跳过向量搜索");
            return Collections.emptyList();
        }
        if (client == null) {
            log.warn("Milvus 客户端未初始化，返回空结果");
            return Collections.emptyList();
        }

        try {
            String expr = buildFilterExpr(filter);

            Map<String, Object> searchParams = new HashMap<>();
            searchParams.put("ef", Math.max(topK * 2, 64));

            SearchReq searchReq = SearchReq.builder()
                .collectionName(config.getCollectionName())
                .data(List.of(new FloatVec(queryVector)))
                .annsField("embedding")
                .topK(topK)
                .metricType(IndexParam.MetricType.COSINE)
                .outputFields(List.of("novelId", "chapterNum", "content", "keywords", "priority"))
                .filter(expr)
                .searchParams(searchParams)
                .build();

            SearchResp response = client.search(searchReq);
            List<VectorSearchResult> results = new ArrayList<>();

            List<List<SearchResp.SearchResult>> searchResults = response.getSearchResults();
            if (searchResults != null && !searchResults.isEmpty()) {
                for (SearchResp.SearchResult result : searchResults.get(0)) {
                    String id = result.getId() != null ? result.getId().toString() : "";
                    // Milvus COSINE 距离: 0=完全相同, 2=完全相反
                    // 转换为相似度: similarity = 1 - distance, 范围 [-1, 1]
                    float score = result.getScore();
                    double similarity = 1.0 - score;

                    Map<String, Object> resultMetadata = new HashMap<>();
                    Map<String, Object> entity = result.getEntity();
                    if (entity != null) {
                        resultMetadata.put("novelId", entity.getOrDefault("novelId", ""));
                        Object chapterNum = entity.get("chapterNum");
                        resultMetadata.put("chapterNum", chapterNum instanceof Number ? ((Number) chapterNum).intValue() : 0);
                        resultMetadata.put("content", entity.getOrDefault("content", ""));
                        resultMetadata.put("keywords", entity.getOrDefault("keywords", ""));
                    }

                    results.add(new VectorSearchResult(id, similarity, resultMetadata));
                }
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
            DeleteReq deleteReq = DeleteReq.builder()
                .collectionName(config.getCollectionName())
                .filter("id == \"" + key.replace("\"", "\\\"") + "\"")
                .build();
            client.delete(deleteReq);
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
        if (filter.startsWith("@novelId:{")) {
            String novelId = filter.substring(10, filter.length() - 1);
            if (!SAFE_ID_PATTERN.matcher(novelId).matches()) {
                throw new IllegalArgumentException("非法 novelId 格式: " + novelId);
            }
            return "novelId == \"" + novelId + "\"";
        }
        return filter;
    }

    /**
     * 从 MongoDB ChapterSummary 迁移 embedding 数据到 Milvus（一次性）
     */
    private void migrateFromMongoDbIfNeeded() {
        if (client == null) return;

        try {
            // 查询 Milvus 中已有数据量（通过 query 获取）
            io.milvus.v2.service.vector.request.QueryReq countReq = io.milvus.v2.service.vector.request.QueryReq.builder()
                .collectionName(config.getCollectionName())
                .filter("id != \"\"")
                .outputFields(List.of("id"))
                .limit(1)
                .build();
            var countResp = client.query(countReq);
            // 如果能查到数据，说明已有数据，跳过迁移
            if (countResp != null && countResp.getQueryResults() != null && !countResp.getQueryResults().isEmpty()) {
                log.info("Milvus collection 已有数据，跳过迁移");
                return;
            }

            log.info("开始从 MongoDB 迁移 embedding 数据到 Milvus...");
            List<ChapterSummary> allSummaries = mongoTemplate.findAll(ChapterSummary.class);

            int migrated = 0;
            int skipped = 0;
            int batchSize = 100;

            List<JsonObject> batchData = new ArrayList<>();

            for (ChapterSummary summary : allSummaries) {
                if (summary.getEmbedding() == null || summary.getEmbedding().isEmpty()) {
                    skipped++;
                    continue;
                }

                String key = summary.getNovelId() + ":ch" + summary.getChapterNum();
                float[] vector = floatListToArray(summary.getEmbedding());

                JsonObject data = new JsonObject();
                data.addProperty("id", key);
                data.addProperty("novelId", summary.getNovelId() != null ? summary.getNovelId() : "");
                data.addProperty("chapterNum", summary.getChapterNum() != null ? summary.getChapterNum().longValue() : 0L);
                data.addProperty("content", summary.getContent() != null ? summary.getContent() : "");
                data.addProperty("keywords", summary.getKeywords() != null ? String.join(",", summary.getKeywords()) : "");
                data.addProperty("priority", summary.getPriority() != null ? summary.getPriority().longValue() : 3L);

                com.google.gson.JsonArray embeddingArray = new com.google.gson.JsonArray(vector.length);
                for (float f : vector) {
                    embeddingArray.add(f);
                }
                data.add("embedding", embeddingArray);

                batchData.add(data);

                if (batchData.size() >= batchSize) {
                    insertBatch(batchData);
                    migrated += batchData.size();
                    log.info("迁移进度: {}/{}", migrated, allSummaries.size() - skipped);
                    batchData.clear();
                }
            }

            // 处理剩余数据
            if (!batchData.isEmpty()) {
                insertBatch(batchData);
                migrated += batchData.size();
            }

            log.info("数据迁移完成: 迁移 {} 条, 跳过 {} 条（无 embedding）", migrated, skipped);

        } catch (Exception e) {
            log.error("数据迁移失败: {}", e.getMessage(), e);
        }
    }

    private void insertBatch(List<JsonObject> batchData) {
        InsertReq insertReq = InsertReq.builder()
            .collectionName(config.getCollectionName())
            .data(batchData)
            .build();
        client.insert(insertReq);
    }

    private float[] floatListToArray(List<Float> floatList) {
        float[] arr = new float[floatList.size()];
        for (int i = 0; i < floatList.size(); i++) {
            arr[i] = floatList.get(i);
        }
        return arr;
    }
}
