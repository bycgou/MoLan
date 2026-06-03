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
            Boolean hasCollection = client.hasCollection(
                HasCollectionParam.newBuilder()
                    .withCollectionName(config.getCollectionName())
                    .build()
            ).getData();

            if (Boolean.TRUE.equals(hasCollection)) {
                log.debug("Milvus collection 已存在: {}", config.getCollectionName());
                migrateFromMongoDbIfNeeded();
                return;
            }

            List<FieldType> fieldTypes = new ArrayList<>();

            FieldType idField = FieldType.newBuilder()
                .withName("id")
                .withDataType(io.milvus.common.DataType.VarChar)
                .withMaxLength(256)
                .withPrimaryKey(true)
                .withAutoID(false)
                .build();
            fieldTypes.add(idField);

            FieldType novelIdField = FieldType.newBuilder()
                .withName("novelId")
                .withDataType(io.milvus.common.DataType.VarChar)
                .withMaxLength(128)
                .withPartitionKey(true)
                .build();
            fieldTypes.add(novelIdField);

            FieldType chapterNumField = FieldType.newBuilder()
                .withName("chapterNum")
                .withDataType(io.milvus.common.DataType.Int64)
                .build();
            fieldTypes.add(chapterNumField);

            FieldType contentField = FieldType.newBuilder()
                .withName("content")
                .withDataType(io.milvus.common.DataType.VarChar)
                .withMaxLength(65535)
                .build();
            fieldTypes.add(contentField);

            FieldType keywordsField = FieldType.newBuilder()
                .withName("keywords")
                .withDataType(io.milvus.common.DataType.VarChar)
                .withMaxLength(1024)
                .build();
            fieldTypes.add(keywordsField);

            FieldType priorityField = FieldType.newBuilder()
                .withName("priority")
                .withDataType(io.milvus.common.DataType.Int64)
                .build();
            fieldTypes.add(priorityField);

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

            client.loadCollection(
                LoadCollectionParam.newBuilder()
                    .withCollectionName(config.getCollectionName())
                    .build()
            );
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
        if (filter.startsWith("@novelId:{")) {
            String novelId = filter.substring(10, filter.length() - 1);
            return "novelId == \"" + novelId.replace("\"", "\\\"") + "\"";
        }
        return filter;
    }

    /**
     * 从 MongoDB ChapterSummary 迁移 embedding 数据到 Milvus（一次性）
     */
    private void migrateFromMongoDbIfNeeded() {
        if (client == null) return;

        try {
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
}
