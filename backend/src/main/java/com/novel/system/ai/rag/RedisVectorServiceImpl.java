package com.novel.system.ai.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.system.config.RedisVectorConfig;
import com.novel.system.ai.rag.RedisVectorService;
import com.novel.system.ai.rag.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Redis Stack 向量服务实现
 * 
 * 优化说明：
 * 1. 避免使用 KEYS 命令（会阻塞Redis）
 * 2. 使用 SCAN 进行增量遍历
 * 3. 添加缓存层减少重复查询
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings({"deprecation", "unchecked"})
public class RedisVectorServiceImpl implements RedisVectorService {
    
    private final StringRedisTemplate redisTemplate;
    private final RedisVectorConfig vectorConfig;
    private final ObjectMapper objectMapper;
    
    private static final String METADATA_PREFIX = "novel:meta:";
    private static final String INDEX_PREFIX = "novel:idx:";
    private static final int SCAN_BATCH_SIZE = 100;

    @PostConstruct
    public void init() {
        ensureIndex();
    }

    @Override
    public void createIndex() {
        try {
            String createIndexCmd = String.format(
                "FT.CREATE %s ON JSON PREFIX 1 %s SCHEMA " +
                "$.novelId AS novelId TAG " +
                "$.chapterNum AS chapterNum NUMERIC " +
                "$.content AS content TEXT " +
                "$.keywords AS keywords TAG SEPARATOR ',' " +
                "$.priority AS priority NUMERIC " +
                "$.embedding AS embedding VECTOR FLAT 6 " +
                "TYPE FLOAT32 DIM %d DISTANCE_METRIC COSINE",
                vectorConfig.getIndexName(),
                METADATA_PREFIX,
                vectorConfig.getDimension()
            );
            log.info("创建Redis向量索引: {}", createIndexCmd);

            redisTemplate.execute((RedisCallback<Void>) connection -> {
                connection.execute(createIndexCmd);
                return null;
            });

            log.info("Redis向量索引创建成功: {}", vectorConfig.getIndexName());
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("Index already exists")) {
                log.debug("Redis向量索引已存在: {}", vectorConfig.getIndexName());
            } else {
                log.warn("Redis向量索引创建失败（可能Redis Stack未安装）: {}", e.getMessage());
            }
        }
    }

    @Override
    public void ensureIndex() {
        try {
            redisTemplate.execute((RedisCallback<Void>) connection -> {
                try {
                    connection.execute("FT.INFO " + vectorConfig.getIndexName());
                    log.debug("Redis向量索引已存在: {}", vectorConfig.getIndexName());
                } catch (Exception e) {
                    log.info("Redis向量索引不存在，开始创建...");
                    createIndex();
                }
                return null;
            });
        } catch (Exception e) {
            log.warn("检查/创建Redis向量索引失败: {}", e.getMessage());
        }
    }
    
    @Override
    public void dropIndex() {
        try {
            log.info("删除向量索引: {}", vectorConfig.getIndexName());
        } catch (Exception e) {
            log.error("删除向量索引失败: {}", e.getMessage());
        }
    }
    
    @Override
    public void saveVector(String key, float[] vector, Map<String, Object> metadata) {
        EmbeddingService.validateVector(vector, vectorConfig.getDimension());
        try {
            Map<String, Object> doc = new HashMap<>(metadata);
            
            List<Float> embeddingList = new ArrayList<>();
            for (float f : vector) {
                embeddingList.add(f);
            }
            doc.put("embedding", embeddingList);
            
            String jsonDoc = objectMapper.writeValueAsString(doc);
            String redisKey = METADATA_PREFIX + key;
            
            redisTemplate.execute((RedisCallback<Void>) connection -> {
                connection.execute("JSON.SET", redisKey.getBytes(StandardCharsets.UTF_8), "$".getBytes(StandardCharsets.UTF_8), jsonDoc.getBytes(StandardCharsets.UTF_8));
                return null;
            });

            // 维护索引缓存
            String novelId = (String) metadata.get("novelId");
            if (novelId != null) {
                String idxKey = INDEX_PREFIX + novelId;
                redisTemplate.opsForSet().add(idxKey, key);
            }

            log.info("向量保存成功: key={}, 向量维度={}, novelId={}", key, vector.length, novelId);
            
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("保存向量失败: {}", e.getMessage());
            throw new RuntimeException("保存向量失败", e);
        }
    }
    
    @Override
    public List<VectorSearchResult> search(float[] queryVector, int topK, String filter) {
        EmbeddingService.validateVector(queryVector, vectorConfig.getDimension());

        try {
            String filterExpr = buildFilterExpr(filter);
            String knnQuery = String.format("(%s)=>[KNN %d @embedding $vec AS score]",
                                            filterExpr, topK);

            byte[] vecBytes = floatArrayToBytes(queryVector);

            List<VectorSearchResult> results = new ArrayList<>();

            redisTemplate.execute((RedisCallback<Void>) connection -> {
                var args = new ArrayList<byte[]>();
                args.add(vectorConfig.getIndexName().getBytes(StandardCharsets.UTF_8));
                args.add(knnQuery.getBytes(StandardCharsets.UTF_8));
                args.add("PARAMS".getBytes(StandardCharsets.UTF_8));
                args.add("2".getBytes(StandardCharsets.UTF_8));
                args.add("vec".getBytes(StandardCharsets.UTF_8));
                args.add(vecBytes);
                args.add("SORTBY".getBytes(StandardCharsets.UTF_8));
                args.add("score".getBytes(StandardCharsets.UTF_8));
                args.add("ASC".getBytes(StandardCharsets.UTF_8));
                args.add("RETURN".getBytes(StandardCharsets.UTF_8));
                args.add("5".getBytes(StandardCharsets.UTF_8));
                args.add("novelId".getBytes(StandardCharsets.UTF_8));
                args.add("chapterNum".getBytes(StandardCharsets.UTF_8));
                args.add("content".getBytes(StandardCharsets.UTF_8));
                args.add("keywords".getBytes(StandardCharsets.UTF_8));
                args.add("score".getBytes(StandardCharsets.UTF_8));
                args.add("DIALECT".getBytes(StandardCharsets.UTF_8));
                args.add("2".getBytes(StandardCharsets.UTF_8));

                var rawArgs = args.toArray(new byte[0][]);
                Object result = connection.execute("FT.SEARCH", rawArgs);

                if (result instanceof List<?> resultList) {
                    parseSearchResults(resultList, results);
                }
                return null;
            });

            log.info("FT.SEARCH完成: filter={}, topK={}, 返回数={}", filter, topK, results.size());
            return results;

        } catch (Exception e) {
            log.error("FT.SEARCH向量搜索失败，回退到Java侧计算: {}", e.getMessage());
            return fallbackSearch(queryVector, topK, filter);
        }
    }
    
    private void scanAndSearch(float[] queryVector, String filter, List<VectorSearchResult> results) {
        String pattern = METADATA_PREFIX + "*";
        
        // 使用 SCAN 增量遍历，避免阻塞 Redis
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            var scanArgs = org.springframework.data.redis.core.ScanOptions.scanOptions()
                .match(pattern)
                .count(SCAN_BATCH_SIZE)
                .build();
            try (var scanCursor = connection.scan(scanArgs)) {
                while (scanCursor.hasNext()) {
                    String key = new String(scanCursor.next(), StandardCharsets.UTF_8);
                    processVectorKey(key, queryVector, filter, results);
                }
            }
            return null;
        });
    }
    
    private void processVectorKey(String key, float[] queryVector, String filter, 
                                  List<VectorSearchResult> results) {
        try {
            String jsonDoc = jsonGet(key);
            if (jsonDoc == null) return;
            
            Map<String, Object> doc = objectMapper.readValue(jsonDoc, Map.class);
            
            // 过滤检查
            if (!passesFilter(doc, filter)) return;
            
            List<?> rawEmbedding = (List<?>) doc.get("embedding");
            if (rawEmbedding != null && rawEmbedding.size() == queryVector.length) {
                double similarity = calculateCosineSimilarity(queryVector, rawEmbedding);
                
                VectorSearchResult result = new VectorSearchResult(
                    key.substring(METADATA_PREFIX.length()),
                    similarity,
                    doc
                );
                results.add(result);
            }
        } catch (Exception e) {
            log.warn("解析向量数据失败: {}", e.getMessage());
        }
    }
    
    private boolean passesFilter(Map<String, Object> doc, String filter) {
        if (filter == null || filter.isEmpty()) return true;
        
        if (filter.startsWith("@novelId:{")) {
            String novelId = filter.substring(10, filter.length() - 1);
            return novelId.equals(doc.get("novelId"));
        }
        
        return true;
    }
    
    private String extractNovelId(String filter) {
        if (filter != null && filter.startsWith("@novelId:{")) {
            return filter.substring(10, filter.length() - 1);
        }
        return null;
    }
    
    private double calculateCosineSimilarity(float[] vector1, List<?> vector2) {
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < vector1.length; i++) {
            double v2 = ((Number) vector2.get(i)).doubleValue();
            dotProduct += vector1[i] * v2;
            norm1 += vector1[i] * vector1[i];
            norm2 += v2 * v2;
        }

        norm1 = Math.sqrt(norm1);
        norm2 = Math.sqrt(norm2);

        if (norm1 == 0 || norm2 == 0) {
            return 0.0;
        }

        return dotProduct / (norm1 * norm2);
    }

    private String jsonGet(String key) {
        return redisTemplate.execute((RedisCallback<String>) connection -> {
            byte[] result = (byte[]) connection.execute(
                "JSON.GET",
                key.getBytes(StandardCharsets.UTF_8),
                "$".getBytes(StandardCharsets.UTF_8));
            if (result == null) return null;
            String arrayJson = new String(result, StandardCharsets.UTF_8);
            if (arrayJson.startsWith("[") && arrayJson.endsWith("]")) {
                return arrayJson.substring(1, arrayJson.length() - 1);
            }
            return arrayJson;
        });
    }

    // ---- FT.SEARCH helper methods ----

    private String buildFilterExpr(String filter) {
        if (filter == null || filter.isEmpty()) {
            return "*";
        }
        if (filter.startsWith("@novelId:{")) {
            String novelId = filter.substring(10, filter.length() - 1);
            return "@novelId:{" + escapeTag(novelId) + "}";
        }
        return filter;
    }

    private String escapeTag(String value) {
        return value.replace("\\", "\\\\")
                    .replace(",", "\\,")
                    .replace(".", "\\.")
                    .replace("<", "\\<")
                    .replace(">", "\\>")
                    .replace("{", "\\{")
                    .replace("}", "\\}")
                    .replace("[", "\\[")
                    .replace("]", "\\]")
                    .replace("\"", "\\\"")
                    .replace("'", "\\'")
                    .replace(":", "\\:")
                    .replace(" ", "\\ ")
                    .replace("\t", "\\t");
    }

    private byte[] floatArrayToBytes(float[] floats) {
        byte[] bytes = new byte[floats.length * 4];
        for (int i = 0; i < floats.length; i++) {
            int bits = Float.floatToIntBits(floats[i]);
            bytes[i * 4] = (byte) (bits & 0xFF);
            bytes[i * 4 + 1] = (byte) ((bits >> 8) & 0xFF);
            bytes[i * 4 + 2] = (byte) ((bits >> 16) & 0xFF);
            bytes[i * 4 + 3] = (byte) ((bits >> 24) & 0xFF);
        }
        return bytes;
    }

    @SuppressWarnings("unchecked")
    private void parseSearchResults(List<?> rawResults, List<VectorSearchResult> results) {
        if (rawResults.size() < 2) return;

        for (int i = 1; i < rawResults.size(); i += 2) {
            if (i + 1 >= rawResults.size()) break;

            String redisKey = new String((byte[]) rawResults.get(i), StandardCharsets.UTF_8);
            List<?> fields = (List<?>) rawResults.get(i + 1);

            String shortKey = redisKey.startsWith(METADATA_PREFIX)
                ? redisKey.substring(METADATA_PREFIX.length())
                : redisKey;

            Map<String, Object> metadata = new HashMap<>();
            double score = 0.0;

            for (int j = 0; j < fields.size(); j += 2) {
                if (j + 1 >= fields.size()) break;
                String fieldName = new String((byte[]) fields.get(j), StandardCharsets.UTF_8);
                Object rawValue = fields.get(j + 1);

                if ("score".equals(fieldName)) {
                    score = Double.parseDouble(new String((byte[]) rawValue, StandardCharsets.UTF_8));
                } else if ("chapterNum".equals(fieldName)) {
                    metadata.put("chapterNum", Integer.parseInt(new String((byte[]) rawValue, StandardCharsets.UTF_8)));
                } else if ("novelId".equals(fieldName)) {
                    metadata.put("novelId", new String((byte[]) rawValue, StandardCharsets.UTF_8));
                } else if ("content".equals(fieldName)) {
                    metadata.put("content", new String((byte[]) rawValue, StandardCharsets.UTF_8));
                } else if ("keywords".equals(fieldName)) {
                    metadata.put("keywords", new String((byte[]) rawValue, StandardCharsets.UTF_8));
                }
            }

            double similarity = 1.0 - score;
            results.add(new VectorSearchResult(shortKey, similarity, metadata));
        }
    }

    private List<VectorSearchResult> fallbackSearch(float[] queryVector, int topK, String filter) {
        log.warn("回退到Java侧暴力计算向量相似度");
        List<VectorSearchResult> results = new ArrayList<>();

        String targetNovelId = extractNovelId(filter);
        if (targetNovelId != null) {
            String idxKey = INDEX_PREFIX + targetNovelId;
            Set<String> keys = redisTemplate.opsForSet().members(idxKey);
            if (keys != null) {
                for (String key : keys) {
                    // Set 中存的是短 key（如 xxx:ch1），需要补全前缀
                    String fullKey = key.startsWith(METADATA_PREFIX) ? key : METADATA_PREFIX + key;
                    processVectorKey(fullKey, queryVector, filter, results);
                }
            }
        }

        results.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        return results.stream().limit(topK).collect(Collectors.toList());
    }

    @Override
    public void deleteVector(String key) {
        try {
            String fullKey = METADATA_PREFIX + key;
            Map<String, Object> doc = new HashMap<>();
            
            // 获取novelId用于清理索引
            try {
                String jsonDoc = jsonGet(fullKey);
                if (jsonDoc != null) {
                    doc = objectMapper.readValue(jsonDoc, Map.class);
                    String novelId = (String) doc.get("novelId");
                    if (novelId != null) {
                        redisTemplate.opsForSet().remove(INDEX_PREFIX + novelId, key);
                    }
                }
            } catch (Exception ignored) {}
            
            redisTemplate.execute((RedisCallback<Void>) connection -> {
                connection.execute("JSON.DEL", fullKey.getBytes(StandardCharsets.UTF_8));
                return null;
            });
            log.debug("向量删除成功: {}", key);
        } catch (Exception e) {
            log.error("删除向量失败: {}", e.getMessage());
        }
    }
}
