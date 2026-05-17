package com.novel.system.ai.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.system.config.RedisVectorConfig;
import com.novel.system.ai.rag.RedisVectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

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
        try {
            Map<String, Object> doc = new HashMap<>(metadata);
            
            List<Float> embeddingList = new ArrayList<>();
            for (float f : vector) {
                embeddingList.add(f);
            }
            doc.put("embedding", embeddingList);
            
            String jsonDoc = objectMapper.writeValueAsString(doc);
            String redisKey = METADATA_PREFIX + key;
            
            redisTemplate.opsForValue().set(redisKey, jsonDoc);

            // 维护索引缓存
            String novelId = (String) metadata.get("novelId");
            if (novelId != null) {
                String idxKey = INDEX_PREFIX + novelId;
                redisTemplate.opsForSet().add(idxKey, key);
            }

            log.info("向量保存成功: key={}, 向量维度={}, novelId={}", key, vector.length, novelId);
            
        } catch (Exception e) {
            log.error("保存向量失败: {}", e.getMessage());
            throw new RuntimeException("保存向量失败", e);
        }
    }
    
    @Override
    public List<VectorSearchResult> search(float[] queryVector, int topK, String filter) {
        try {
            List<VectorSearchResult> results = new ArrayList<>();
            
            // 根据过滤条件优化搜索策略
            String targetNovelId = extractNovelId(filter);
            
            if (targetNovelId != null) {
                // 已知novelId，使用索引缓存
                String idxKey = INDEX_PREFIX + targetNovelId;
                Set<String> keys = redisTemplate.opsForSet().members(idxKey);
                
                if (keys != null) {
                    for (String key : keys) {
                        processVectorKey(key, queryVector, filter, results);
                    }
                }
            } else {
                // 修复：未知novelId时，禁止全库扫描进行内存相似度计算，这会导致OOM
                log.warn("未提供novelId过滤条件，为防止OOM，已拒绝全表扫描降级。如果配置了RediSearch请使用FT.SEARCH。");
                // 暂时仅返回空结果，避免生产环境崩溃
                return Collections.emptyList();
            }
            
            // 按相似度排序并返回前topK个结果
            results.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
            List<VectorSearchResult> topResults = results.stream().limit(topK).collect(Collectors.toList());
            log.info("向量搜索完成: novelId={}, 候选数={}, 返回数={}", targetNovelId, results.size(), topResults.size());
            return topResults;
            
        } catch (Exception e) {
            log.error("向量搜索失败: {}", e.getMessage());
            return Collections.emptyList();
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
                    String key = new String(scanCursor.next());
                    processVectorKey(key, queryVector, filter, results);
                }
            }
            return null;
        });
    }
    
    private void processVectorKey(String key, float[] queryVector, String filter, 
                                  List<VectorSearchResult> results) {
        try {
            String jsonDoc = redisTemplate.opsForValue().get(key);
            if (jsonDoc == null) return;
            
            Map<String, Object> doc = objectMapper.readValue(jsonDoc, Map.class);
            
            // 过滤检查
            if (!passesFilter(doc, filter)) return;
            
            List<Double> embeddingList = (List<Double>) doc.get("embedding");
            if (embeddingList != null && embeddingList.size() == queryVector.length) {
                double similarity = calculateCosineSimilarity(queryVector, embeddingList);
                
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
    
    private double calculateCosineSimilarity(float[] vector1, List<Double> vector2) {
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        
        for (int i = 0; i < vector1.length; i++) {
            dotProduct += vector1[i] * vector2.get(i);
            norm1 += vector1[i] * vector1[i];
            norm2 += vector2.get(i) * vector2.get(i);
        }
        
        norm1 = Math.sqrt(norm1);
        norm2 = Math.sqrt(norm2);
        
        if (norm1 == 0 || norm2 == 0) {
            return 0.0;
        }
        
        return dotProduct / (norm1 * norm2);
    }
    
    @Override
    public void deleteVector(String key) {
        try {
            String fullKey = METADATA_PREFIX + key;
            Map<String, Object> doc = new HashMap<>();
            
            // 获取novelId用于清理索引
            try {
                String jsonDoc = redisTemplate.opsForValue().get(fullKey);
                if (jsonDoc != null) {
                    doc = objectMapper.readValue(jsonDoc, Map.class);
                    String novelId = (String) doc.get("novelId");
                    if (novelId != null) {
                        redisTemplate.opsForSet().remove(INDEX_PREFIX + novelId, key);
                    }
                }
            } catch (Exception ignored) {}
            
            redisTemplate.delete(fullKey);
            log.debug("向量删除成功: {}", key);
        } catch (Exception e) {
            log.error("删除向量失败: {}", e.getMessage());
        }
    }
}
