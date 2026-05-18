# RAG Data Path Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix three data path issues in the RAG module: broken fallback JSON.GET, dual save path redundancy, and generic query text.

**Architecture:** Three independent fixes: (1) switch `processVectorKey()` and `deleteVector()` from plain GET to JSON.GET for RedisJSON compatibility; (2) remove the redundant vectorization block in ChapterSaveService that writes incomplete metadata; (3) improve query text in RagServiceImpl by using recent summary content instead of generic "第X章相关剧情".

**Tech Stack:** Java 17, Spring Boot 3.x, Redis Stack (RedisJSON + RediSearch), MongoDB

---

## File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `backend/src/main/java/com/novel/system/ai/rag/RedisVectorServiceImpl.java` | Modify | Fix `processVectorKey()` and `deleteVector()` to use JSON.GET |
| `backend/src/main/java/com/novel/system/service/chapter/ChapterSaveService.java` | Modify | Remove path A vectorization block, remove unused fields |
| `backend/src/main/java/com/novel/system/ai/rag/RagServiceImpl.java` | Modify | Optimize query text in `retrieveContext()` and `assembleContext()` |

---

### Task 1: Fix JSON.GET compatibility in fallback path

**Files:**
- Modify: `backend/src/main/java/com/novel/system/ai/rag/RedisVectorServiceImpl.java`

- [ ] **Step 1: Add jsonGet helper method**

Add this private method to `RedisVectorServiceImpl`, after the `calculateCosineSimilarity()` method:

```java
    /**
     * 从 RedisJSON 读取文档
     * JSON.GET key $ 返回 [{...}] 格式，提取第一个元素
     */
    private String jsonGet(String key) {
        return redisTemplate.execute((RedisCallback<String>) connection -> {
            byte[] result = (byte[]) connection.execute(
                "JSON.GET",
                key.getBytes(StandardCharsets.UTF_8),
                "$".getBytes(StandardCharsets.UTF_8));
            if (result == null) return null;
            String arrayJson = new String(result, StandardCharsets.UTF_8);
            // JSON.GET key $ returns JSON array, unwrap it
            if (arrayJson.startsWith("[") && arrayJson.endsWith("]")) {
                return arrayJson.substring(1, arrayJson.length() - 1);
            }
            return arrayJson;
        });
    }
```

- [ ] **Step 2: Fix processVectorKey() to use jsonGet**

In `processVectorKey()` (around line 216), replace:

```java
            String jsonDoc = redisTemplate.opsForValue().get(key);
```

With:

```java
            String jsonDoc = jsonGet(key);
```

- [ ] **Step 3: Fix deleteVector() to use jsonGet**

In `deleteVector()` (around line 388), replace:

```java
                String jsonDoc = redisTemplate.opsForValue().get(fullKey);
```

With:

```java
                String jsonDoc = jsonGet(fullKey);
```

- [ ] **Step 4: Verify compilation**

Run: `cd backend && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/novel/system/ai/rag/RedisVectorServiceImpl.java
git commit -m "fix(rag): use JSON.GET in fallback path for RedisJSON compatibility"
```

---

### Task 2: Remove dual save path A from ChapterSaveService

**Files:**
- Modify: `backend/src/main/java/com/novel/system/service/chapter/ChapterSaveService.java`

- [ ] **Step 1: Remove the vectorization block**

In `saveToMongoAsync()`, delete lines 136-151 (the entire try/catch block that calls `embeddingService.embed()` and `redisVectorService.saveVector()`):

```java
// DELETE this entire block:
        try {
            // 向量化章节内容（异步增强RAG检索）
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

After removal, `saveToMongoAsync()` should start directly with:

```java
        // 保存摘要（MongoDB + Redis向量）
        try {
            ragService.saveSummary(summary);
        } catch (Exception e) {
            log.warn("保存摘要失败（不影响保存）: {}", e.getMessage());
        }
```

- [ ] **Step 2: Remove unused fields**

Remove `embeddingService` and `redisVectorService` fields from the class (lines ~42-43):

```java
// DELETE these two lines:
    private final EmbeddingService embeddingService;
    private final RedisVectorService redisVectorService;
```

Also remove the now-unused imports:

```java
// DELETE:
import com.novel.system.ai.rag.EmbeddingService;
import com.novel.system.ai.rag.RedisVectorService;
```

- [ ] **Step 3: Verify compilation**

Run: `cd backend && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/novel/system/service/chapter/ChapterSaveService.java
git commit -m "refactor(rag): remove redundant path A vectorization, keep only saveSummary path"
```

---

### Task 3: Optimize query text for RAG retrieval

**Files:**
- Modify: `backend/src/main/java/com/novel/system/ai/rag/RagServiceImpl.java`

- [ ] **Step 1: Add buildQueryFromRecentSummaries helper**

Add this private method to `RagServiceImpl`:

```java
    /**
     * 从最近章节摘要构建查询文本，提高向量检索的语义匹配精度
     */
    private String buildQueryFromRecentSummaries(String novelId, int currentChapterNum) {
        List<ChapterSummary> recent = getRecentSummaries(novelId, currentChapterNum, 3);
        StringBuilder sb = new StringBuilder();
        for (ChapterSummary s : recent) {
            if (s.getContent() != null && !s.getContent().isEmpty()) {
                String snippet = s.getContent().length() > 200
                    ? s.getContent().substring(0, 200)
                    : s.getContent();
                sb.append(snippet).append(" ");
            }
        }
        return sb.length() > 0
            ? sb.toString().trim()
            : "第" + currentChapterNum + "章相关剧情";
    }
```

- [ ] **Step 2: Update retrieveContext() query text**

In `retrieveContext()` (around line 56), replace:

```java
            String queryText = "第" + currentChapterNum + "章相关剧情";
```

With:

```java
            String queryText = buildQueryFromRecentSummaries(novelId, currentChapterNum);
```

- [ ] **Step 3: Update assembleContext() query text**

In `assembleContext()` (around line 421), replace:

```java
            String queryText = "第" + chapterNum + "章相关重要剧情";
```

With:

```java
            String queryText = buildQueryFromRecentSummaries(novelId, chapterNum);
```

- [ ] **Step 4: Verify compilation**

Run: `cd backend && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/novel/system/ai/rag/RagServiceImpl.java
git commit -m "feat(rag): use recent summary content as query text for better semantic matching"
```

---

### Task 4: End-to-end verification

**Files:** None (manual testing)

- [ ] **Step 1: Start application and verify no compilation errors**

```bash
cd backend && mvn spring-boot:run
```

Check for startup errors related to `ChapterSaveService` (missing field injections).

- [ ] **Step 2: Generate a chapter and verify save path**

Trigger chapter generation. Check logs for:
```
向量保存成功: xxx:ch1, 向量维度=1536, novelId=xxx
```
And verify NO log from `ChapterSaveService` about "章节向量化完成" (that path is removed).

- [ ] **Step 3: Verify RAG retrieval returns results**

Check logs for:
```
FT.SEARCH完成: filter=@novelId:{xxx}, topK=10, 返回数=N
```
Where N > 0.

- [ ] **Step 4: Verify fallback path works**

If Redis Stack is not installed, check that fallback returns results:
```
FT.SEARCH向量搜索失败，回退到Java侧计算
```
And verify results are non-empty.
