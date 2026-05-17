# RAG Module Refactoring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix vector search always returning empty results by eliminating zero-vector saves, switching to Redis Stack native FT.SEARCH KNN, and unifying the embedding entry point.

**Architecture:** Three-layer fix: (1) EmbeddingService becomes the single embedding entry point with strict validation that rejects null/zero/dimension-mismatched vectors; (2) RedisVectorServiceImpl switches from Java-side brute-force cosine to Redis Stack FT.SEARCH with VECTOR index; (3) Save paths (ChapterSaveService, RagServiceImpl.saveSummary) skip Redis writes when embedding fails instead of storing zero vectors.

**Tech Stack:** Java 17, Spring Boot 3.x, LangChain4j 1.14.1, Redis Stack (RediSearch + RedisJSON), MongoDB, JUnit 5

---

## File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `backend/src/main/java/com/novel/system/ai/rag/EmbeddingService.java` | Modify | Add `validateVector()`, throw on failure instead of returning zeros |
| `backend/src/main/java/com/novel/system/service/llm/LlmServiceImpl.java` | Modify | `getEmbedding()` delegates to EmbeddingService |
| `backend/src/main/java/com/novel/system/ai/rag/RedisVectorServiceImpl.java` | Modify | Rewrite `createIndex()` with VECTOR field, rewrite `search()` with FT.SEARCH KNN, add `saveVector()` validation |
| `backend/src/main/java/com/novel/system/ai/rag/RedisVectorService.java` | Modify | Add `ensureIndex()` method to interface |
| `backend/src/main/java/com/novel/system/service/chapter/ChapterSaveService.java` | Modify | Skip Redis write when embedding fails |
| `backend/src/main/java/com/novel/system/ai/rag/RagServiceImpl.java` | Modify | Skip Redis write in `saveSummary()` when embedding fails |
| `backend/src/test/java/com/novel/system/ai/rag/EmbeddingServiceTest.java` | Create | Unit tests for validation logic |
| `backend/src/test/java/com/novel/system/ai/rag/RedisVectorServiceImplTest.java` | Create | Unit tests for FT.SEARCH query building |

---

### Task 1: Add vector validation to EmbeddingService

**Files:**
- Modify: `backend/src/main/java/com/novel/system/ai/rag/EmbeddingService.java`
- Create: `backend/src/test/java/com/novel/system/ai/rag/EmbeddingServiceTest.java`

- [ ] **Step 1: Write the failing test**

```java
// backend/src/test/java/com/novel/system/ai/rag/EmbeddingServiceTest.java
package com.novel.system.ai.rag;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EmbeddingServiceTest {

    @Test
    void validateVector_throwsOnNull() {
        assertThrows(IllegalArgumentException.class, () ->
            EmbeddingService.validateVector(null));
    }

    @Test
    void validateVector_throwsOnEmpty() {
        assertThrows(IllegalArgumentException.class, () ->
            EmbeddingService.validateVector(new float[0]));
    }

    @Test
    void validateVector_throwsOnAllZeros() {
        assertThrows(IllegalArgumentException.class, () ->
            EmbeddingService.validateVector(new float[1536]));
    }

    @Test
    void validateVector_throwsOnWrongDimension() {
        float[] wrongDim = new float[768];
        for (int i = 0; i < 768; i++) wrongDim[i] = 0.01f;
        assertThrows(IllegalArgumentException.class, () ->
            EmbeddingService.validateVector(wrongDim));
    }

    @Test
    void validateVector_passesOnValidVector() {
        float[] valid = new float[1536];
        for (int i = 0; i < 1536; i++) valid[i] = 0.01f;
        assertDoesNotThrow(() -> EmbeddingService.validateVector(valid));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -pl . -Dtest=EmbeddingServiceTest -q`
Expected: FAIL — `validateVector` method does not exist

- [ ] **Step 3: Implement validateVector and modify embed()**

```java
// backend/src/main/java/com/novel/system/ai/rag/EmbeddingService.java
// Add this static method after the existing cosineSimilarity method:

    /** 向量维度常量 */
    public static final int EXPECTED_DIMENSION = 1536;

    /**
     * 校验向量有效性：非null、非空、非全零、维度正确
     * @throws IllegalArgumentException 向量无效时抛出
     */
    public static void validateVector(float[] vector) {
        if (vector == null) {
            throw new IllegalArgumentException("向量为null");
        }
        if (vector.length == 0) {
            throw new IllegalArgumentException("向量为空数组");
        }
        if (vector.length != EXPECTED_DIMENSION) {
            throw new IllegalArgumentException(
                "向量维度不匹配: 期望=" + EXPECTED_DIMENSION + ", 实际=" + vector.length);
        }
        boolean allZero = true;
        for (float v : vector) {
            if (v != 0.0f) {
                allZero = false;
                break;
            }
        }
        if (allZero) {
            throw new IllegalArgumentException("向量全为零，Embedding模型可能未正确配置");
        }
    }
```

Then modify the `embed()` method — replace the current implementation:

```java
// OLD (lines 31-45):
//     public float[] embed(String text) {
//         EmbeddingModel model = getEmbeddingModel();
//         if (model == null) {
//             log.warn("未找到Embedding模型，返回空向量");
//             return new float[1536];
//         }
//         String truncated = text.length() > 2000 ? text.substring(0, 2000) : text;
//         long start = System.currentTimeMillis();
//         Embedding embedding = model.embed(truncated).content();
//         long elapsed = System.currentTimeMillis() - start;
//         log.info("Embedding完成: 文本长度={}, 向量维度={}, 耗时={}ms", truncated.length(), embedding.vector().length, elapsed);
//         return embedding.vector();
//     }

// NEW:
    public float[] embed(String text) {
        EmbeddingModel model = getEmbeddingModel();
        if (model == null) {
            throw new IllegalStateException(
                "未配置Embedding模型，请在模型管理中添加 usageType=embedding 的配置");
        }

        String truncated = text.length() > 2000 ? text.substring(0, 2000) : text;
        long start = System.currentTimeMillis();
        Embedding embedding = model.embed(truncated).content();
        long elapsed = System.currentTimeMillis() - start;

        float[] vector = embedding.vector();
        validateVector(vector);

        log.info("Embedding完成: 文本长度={}, 向量维度={}, 耗时={}ms",
                 truncated.length(), vector.length, elapsed);
        return vector;
    }
```

Also modify `embedAll()` to validate each vector:

```java
// Replace the existing embedAll method (lines 50-73):
    public List<float[]> embedAll(List<String> texts) {
        EmbeddingModel model = getEmbeddingModel();
        if (model == null) {
            throw new IllegalStateException(
                "未配置Embedding模型，请在模型管理中添加 usageType=embedding 的配置");
        }

        List<TextSegment> segments = new ArrayList<>();
        for (String text : texts) {
            String truncated = text.length() > 2000 ? text.substring(0, 2000) : text;
            segments.add(TextSegment.from(truncated));
        }

        List<Embedding> embeddings = model.embedAll(segments).content();
        List<float[]> result = new ArrayList<>();
        for (Embedding e : embeddings) {
            float[] vector = e.vector();
            validateVector(vector);
            result.add(vector);
        }
        return result;
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn test -pl . -Dtest=EmbeddingServiceTest -q`
Expected: All 5 tests PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/novel/system/ai/rag/EmbeddingService.java \
        backend/src/test/java/com/novel/system/ai/rag/EmbeddingServiceTest.java
git commit -m "feat(rag): add vector validation, reject null/zero/wrong-dimension vectors"
```

---

### Task 2: Delegate LlmServiceImpl.getEmbedding() to EmbeddingService

**Files:**
- Modify: `backend/src/main/java/com/novel/system/service/llm/LlmServiceImpl.java`

- [ ] **Step 1: Add EmbeddingService dependency**

Add import and field to `LlmServiceImpl`:

```java
// Add import at top:
import com.novel.system.ai.rag.EmbeddingService;

// Add field (after the existing langChainConfig field):
    private final EmbeddingService embeddingService;
```

Note: Since `LlmServiceImpl` uses `@RequiredArgsConstructor`, adding the field automatically injects it via constructor. However, `EmbeddingService` depends on `LangChainConfig` and `LlmConfigService`, which are also dependencies of `LlmServiceImpl` — no circular dependency.

- [ ] **Step 2: Replace getEmbedding() implementation**

Replace the entire `getEmbedding()` method (lines 120-156):

```java
// OLD:
//     @Override
//     public float[] getEmbedding(String text) {
//         LlmConfig config = null;
//         try {
//             config = llmConfigService.getAnyActiveConfig("embedding");
//             ... 60 lines of config resolution and embedding ...
//         } catch (Exception e) {
//             ...
//             return new float[0];
//         }
//     }

// NEW:
    @Override
    public float[] getEmbedding(String text) {
        return embeddingService.embed(text);
    }
```

- [ ] **Step 3: Verify no compilation errors**

Run: `cd backend && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/novel/system/service/llm/LlmServiceImpl.java
git commit -m "refactor(rag): delegate getEmbedding() to EmbeddingService, eliminate duplicate config resolution"
```

---

### Task 3: Rewrite RedisVectorServiceImpl.createIndex() with VECTOR field

**Files:**
- Modify: `backend/src/main/java/com/novel/system/ai/rag/RedisVectorService.java`
- Modify: `backend/src/main/java/com/novel/system/ai/rag/RedisVectorServiceImpl.java`

- [ ] **Step 1: Add ensureIndex() to interface**

```java
// backend/src/main/java/com/novel/system/ai/rag/RedisVectorService.java
// Add after the existing createIndex() declaration:

    /**
     * 确保索引存在（启动时调用，幂等操作）
     */
    void ensureIndex();
```

- [ ] **Step 2: Rewrite createIndex() and add ensureIndex()**

Replace the `createIndex()` method and add `ensureIndex()` in `RedisVectorServiceImpl`:

```java
// Replace the existing createIndex() (lines 38-64):
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
            // 先检查索引是否存在
            redisTemplate.execute((RedisCallback<Void>) connection -> {
                try {
                    connection.execute("FT.INFO " + vectorConfig.getIndexName());
                    log.debug("Redis向量索引已存在: {}", vectorConfig.getIndexName());
                } catch (Exception e) {
                    // 索引不存在，创建它
                    log.info("Redis向量索引不存在，开始创建...");
                    createIndex();
                }
                return null;
            });
        } catch (Exception e) {
            log.warn("检查/创建Redis向量索引失败: {}", e.getMessage());
        }
    }
```

- [ ] **Step 3: Call ensureIndex() on startup**

Add a `@PostConstruct` or `ApplicationRunner` to `RedisVectorServiceImpl`. Add this method after the constructor (the class uses `@RequiredArgsConstructor`, so add after the field declarations):

```java
// Add import at top of file:
import jakarta.annotation.PostConstruct;

// Add after field declarations (after line 35):
    @PostConstruct
    public void init() {
        ensureIndex();
    }
```

- [ ] **Step 4: Verify compilation**

Run: `cd backend && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/novel/system/ai/rag/RedisVectorService.java \
        backend/src/main/java/com/novel/system/ai/rag/RedisVectorServiceImpl.java
git commit -m "feat(rag): add VECTOR field to FT.CREATE index, auto-create on startup"
```

---

### Task 4: Add validation to RedisVectorServiceImpl.saveVector()

**Files:**
- Modify: `backend/src/main/java/com/novel/system/ai/rag/RedisVectorServiceImpl.java`

- [ ] **Step 1: Add validation at the top of saveVector()**

Modify the `saveVector()` method (lines 76-104) — add validation before the existing logic:

```java
// Replace the existing saveVector method:
    @Override
    public void saveVector(String key, float[] vector, Map<String, Object> metadata) {
        // 校验向量有效性
        EmbeddingService.validateVector(vector);

        try {
            Map<String, Object> doc = new HashMap<>(metadata);

            // 将float[]转为List<Float>用于JSON序列化
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

        } catch (IllegalArgumentException e) {
            // validateVector 抛出的异常，直接传播
            throw e;
        } catch (Exception e) {
            log.error("保存向量失败: {}", e.getMessage());
            throw new RuntimeException("保存向量失败", e);
        }
    }
```

- [ ] **Step 2: Add import for EmbeddingService**

```java
// Add import at top of RedisVectorServiceImpl.java:
import com.novel.system.ai.rag.EmbeddingService;
```

- [ ] **Step 3: Verify compilation**

Run: `cd backend && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/novel/system/ai/rag/RedisVectorServiceImpl.java
git commit -m "feat(rag): validate vectors in saveVector(), reject zero/null vectors"
```

---

### Task 5: Rewrite RedisVectorServiceImpl.search() to use FT.SEARCH KNN

**Files:**
- Modify: `backend/src/main/java/com/novel/system/ai/rag/RedisVectorServiceImpl.java`

- [ ] **Step 1: Replace search() with FT.SEARCH KNN implementation**

Replace the entire `search()` method (lines 107-141):

```java
    @Override
    public List<VectorSearchResult> search(float[] queryVector, int topK, String filter) {
        // 校验查询向量
        EmbeddingService.validateVector(queryVector);

        try {
            // 构建 KNN 查询
            // RediSearch 语法: "(@filter)=>[KNN topK @embedding $vec AS score]"
            String filterExpr = buildFilterExpr(filter);
            String knnQuery = String.format("(%s)=>[KNN %d @embedding $vec AS score]",
                                            filterExpr, topK);

            // 将 float[] 序列化为 little-endian byte[] (FLOAT32)
            byte[] vecBytes = floatArrayToBytes(queryVector);

            List<VectorSearchResult> results = new ArrayList<>();

            redisTemplate.execute((RedisCallback<Void>) connection -> {
                // 使用 raw command 执行 FT.SEARCH
                var args = new ArrayList<byte[]>();
                args.add(vectorConfig.getIndexName().getBytes());
                args.add(knnQuery.getBytes());
                args.add("PARAMS".getBytes());
                args.add("2".getBytes());
                args.add("vec".getBytes());
                args.add(vecBytes);
                args.add("SORTBY".getBytes());
                args.add("score".getBytes());
                args.add("ASC".getBytes());  // cosine distance: 越小越相似
                args.add("RETURN".getBytes());
                args.add("5".getBytes());
                args.add("novelId".getBytes());
                args.add("chapterNum".getBytes());
                args.add("content".getBytes());
                args.add("keywords".getBytes());
                args.add("score".getBytes());
                args.add("DIALECT".getBytes());
                args.add("2".getBytes());

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
            log.error("FT.SEARCH向量搜索失败: {}", e.getMessage(), e);
            // 回退到Java侧暴力计算
            return fallbackSearch(queryVector, topK, filter);
        }
    }

    /**
     * 构建RediSearch过滤表达式
     */
    private String buildFilterExpr(String filter) {
        if (filter == null || filter.isEmpty()) {
            return "*";
        }
        // 将 @novelId:{xxx} 转为 RediSearch 语法
        if (filter.startsWith("@novelId:{")) {
            String novelId = filter.substring(10, filter.length() - 1);
            return "@novelId:{" + escapeTag(novelId) + "}";
        }
        return filter;
    }

    /**
     * 转义TAG值中的特殊字符
     */
    private String escapeTag(String value) {
        // RediSearch TAG 需要转义的字符
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
                    .replace(":", "\\:");
    }

    /**
     * 将float[]转为little-endian byte[] (IEEE 754 FLOAT32)
     */
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

    /**
     * 解析FT.SEARCH返回结果
     * 格式: [count, key1, [field, value, ...], key2, [field, value, ...], ...]
     */
    @SuppressWarnings("unchecked")
    private void parseSearchResults(List<?> rawResults, List<VectorSearchResult> results) {
        if (rawResults.size() < 2) return;

        // 第一个元素是总数
        // 从索引1开始，每两个元素为一组: (key, [fields...])
        for (int i = 1; i < rawResults.size(); i += 2) {
            if (i + 1 >= rawResults.size()) break;

            String redisKey = new String((byte[]) rawResults.get(i));
            List<?> fields = (List<?>) rawResults.get(i + 1);

            String shortKey = redisKey.startsWith(METADATA_PREFIX)
                ? redisKey.substring(METADATA_PREFIX.length())
                : redisKey;

            Map<String, Object> metadata = new HashMap<>();
            double score = 0.0;

            // 解析字段列表: [fieldName, value, fieldName, value, ...]
            for (int j = 0; j < fields.size(); j += 2) {
                if (j + 1 >= fields.size()) break;
                String fieldName = new String((byte[]) fields.get(j));
                Object rawValue = fields.get(j + 1);

                if ("score".equals(fieldName)) {
                    // score 是 byte[] 形式的字符串
                    score = Double.parseDouble(new String((byte[]) rawValue));
                } else if ("chapterNum".equals(fieldName)) {
                    metadata.put("chapterNum", Integer.parseInt(new String((byte[]) rawValue)));
                } else if ("novelId".equals(fieldName)) {
                    metadata.put("novelId", new String((byte[]) rawValue));
                } else if ("content".equals(fieldName)) {
                    metadata.put("content", new String((byte[]) rawValue));
                } else if ("keywords".equals(fieldName)) {
                    metadata.put("keywords", new String((byte[]) rawValue));
                }
            }

            // cosine distance → similarity: similarity = 1 - distance
            double similarity = 1.0 - score;
            results.add(new VectorSearchResult(shortKey, similarity, metadata));
        }
    }

    /**
     * 回退方案：Java侧暴力计算（当FT.SEARCH不可用时）
     */
    private List<VectorSearchResult> fallbackSearch(float[] queryVector, int topK, String filter) {
        log.warn("回退到Java侧暴力计算向量相似度");
        List<VectorSearchResult> results = new ArrayList<>();

        String targetNovelId = extractNovelId(filter);
        if (targetNovelId != null) {
            String idxKey = INDEX_PREFIX + targetNovelId;
            Set<String> keys = redisTemplate.opsForSet().members(idxKey);
            if (keys != null) {
                for (String key : keys) {
                    processVectorKey(key, queryVector, filter, results);
                }
            }
        }

        results.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        return results.stream().limit(topK).collect(Collectors.toList());
    }
```

- [ ] **Step 2: Keep old helper methods for fallback**

The existing `processVectorKey()`, `passesFilter()`, `extractNovelId()`, `calculateCosineSimilarity()` methods should be kept — they serve as the fallback path.

- [ ] **Step 3: Verify compilation**

Run: `cd backend && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/novel/system/ai/rag/RedisVectorServiceImpl.java
git commit -m "feat(rag): replace brute-force search with FT.SEARCH KNN, add fallback"
```

---

### Task 6: Guard ChapterSaveService against zero-vector saves

**Files:**
- Modify: `backend/src/main/java/com/novel/system/service/chapter/ChapterSaveService.java`

- [ ] **Step 1: Modify saveToMongoAsync() to skip Redis on embedding failure**

Replace the vectorization block (lines 137-153) in `saveToMongoAsync()`:

```java
// OLD:
//         try {
//             String textToEmbed = chapterContent.length() > 2000
//                     ? chapterContent.substring(0, 2000) : chapterContent;
//             float[] vector = embeddingService.embed(textToEmbed);
//             if (vector.length > 0) {
//                 Map<String, Object> metadata = new HashMap<>();
//                 metadata.put("novelId", novelId);
//                 metadata.put("chapterNum", chapterNum);
//                 metadata.put("chapterTitle", chapterTitle);
//                 metadata.put("wordCount", wordCount);
//                 String vectorKey = novelId + ":ch" + chapterNum;
//                 redisVectorService.saveVector(vectorKey, vector, metadata);
//                 log.info("章节向量化完成: 小说ID={}, 章节号={}, 向量维度={}", novelId, chapterNum, vector.length);
//             }
//         } catch (Exception e) {
//             log.warn("章节向量化失败（不影响保存）: {}", e.getMessage());
//         }

// NEW:
        try {
            String textToEmbed = chapterContent.length() > 2000
                    ? chapterContent.substring(0, 2000) : chapterContent;
            float[] vector = embeddingService.embed(textToEmbed);
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("novelId", novelId);
            metadata.put("chapterNum", chapterNum);
            metadata.put("chapterTitle", chapterTitle);
            metadata.put("wordCount", wordCount);
            String vectorKey = novelId + ":ch" + chapterNum;
            redisVectorService.saveVector(vectorKey, vector, metadata);
            log.info("章节向量化完成: 小说ID={}, 章节号={}, 向量维度={}", novelId, chapterNum, vector.length);
        } catch (Exception e) {
            log.error("章节向量化失败，跳过Redis向量保存（不影响章节内容保存）: {}", e.getMessage());
        }
```

- [ ] **Step 2: Verify compilation**

Run: `cd backend && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/novel/system/service/chapter/ChapterSaveService.java
git commit -m "fix(rag): skip Redis vector save when embedding fails in ChapterSaveService"
```

---

### Task 7: Guard RagServiceImpl.saveSummary() against zero-vector saves

**Files:**
- Modify: `backend/src/main/java/com/novel/system/ai/rag/RagServiceImpl.java`

- [ ] **Step 1: Modify saveSummary() to skip Redis on embedding failure**

Replace the saveSummary method (lines 290-329):

```java
// Replace the entire saveSummary method:
    @Override
    public ChapterSummary saveSummary(ChapterSummary summary) {
        if (summary == null) {
            log.warn("摘要对象为null，跳过保存");
            return null;
        }

        // 生成向量嵌入，失败时跳过Redis保存（不存储零向量）
        if (summary.getContent() != null) {
            try {
                float[] embedding = llmService.getEmbedding(summary.getContent());

                Map<String, Object> metadata = new HashMap<>();
                metadata.put("novelId", summary.getNovelId());
                metadata.put("chapterNum", summary.getChapterNum());
                metadata.put("content", summary.getContent());
                metadata.put("keywords", String.join(",",
                    summary.getKeywords() != null ? summary.getKeywords() : List.of()));
                metadata.put("priority", summary.getPriority() != null ? summary.getPriority() : 3);

                String key = summary.getNovelId() + ":ch" + summary.getChapterNum();
                redisVectorService.saveVector(key, embedding, metadata);

                // 同时保存embedding到MongoDB
                List<Float> embeddingList = new ArrayList<>();
                for (float f : embedding) {
                    embeddingList.add(f);
                }
                summary.setEmbedding(embeddingList);

                log.info("向量保存成功: {}", key);

            } catch (Exception e) {
                log.error("向量化失败，跳过Redis向量保存（摘要仍会保存到MongoDB）: {}", e.getMessage());
                summary.setEmbedding(null);
            }
        }

        summary.setCreateTime(LocalDateTime.now());
        return chapterSummaryRepository.save(summary);
    }
```

- [ ] **Step 2: Add missing import**

Check that `List` from `java.util` is imported (it already is based on the existing imports).

- [ ] **Step 3: Verify compilation**

Run: `cd backend && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/novel/system/ai/rag/RagServiceImpl.java
git commit -m "fix(rag): skip Redis vector save when embedding fails in saveSummary()"
```

---

### Task 8: End-to-end verification

**Files:** None (manual testing)

- [ ] **Step 1: Start the application and verify Redis index creation**

Start the Spring Boot application. Check logs for:
```
Redis向量索引创建成功: novel_summary_idx
```
or
```
Redis向量索引已存在: novel_summary_idx
```

If you see `Redis向量索引创建失败（可能Redis Stack未安装）`, verify Redis Stack is running:
```bash
redis-cli MODULE LIST
# Should show: rejson, search, timeseries
```

- [ ] **Step 2: Verify index has VECTOR field**

```bash
redis-cli FT.INFO novel_summary_idx
```

Check that the schema includes an `embedding` field of type `VECTOR`.

- [ ] **Step 3: Generate a chapter and verify vector is saved**

Trigger a chapter generation via the API. Check application logs for:
```
章节向量化完成: 小说ID=xxx, 章节号=1, 向量维度=1536
向量保存成功: xxx:ch1, 向量维度=1536
```

If you see `向量化失败，跳过Redis向量保存`, the embedding model is not configured. Add a `usageType=embedding` config in the model management UI.

- [ ] **Step 4: Verify Redis contains the vector**

```bash
redis-cli JSON.GET "novel:meta:xxx:ch1" "$.embedding[0:5]"
# Should return the first 5 float values (non-zero)
```

- [ ] **Step 5: Trigger RAG retrieval and verify non-empty results**

Either trigger another chapter generation (which calls `assembleContext` → `hybridSearch`), or call the RAG tool via the API. Check logs for:
```
FT.SEARCH完成: filter=@novelId:{xxx}, topK=10, 返回数=N
混合检索完成，返回N条结果
```

Where N > 0. If N = 0, check:
1. Is the embedding model configured? (`usageType=embedding` or `usageType=all`)
2. Do the stored vectors have non-zero values? (Step 4)
3. Is Redis Stack running with RediSearch module?

- [ ] **Step 6: Commit the spec doc**

```bash
git add docs/superpowers/plans/2026-05-17-rag-refactor.md
git commit -m "docs: add RAG refactoring implementation plan"
```
