# RAG Data Path Fix Design

## Problem

After the initial RAG refactoring (FT.SEARCH KNN + vector validation), three data path issues remain:

1. **Fallback search broken**: `saveVector()` uses `JSON.SET` but `processVectorKey()` reads with plain `GET`, which cannot read RedisJSON documents.
2. **Dual save path**: `ChapterSaveService` (path A) and `RagServiceImpl.saveSummary()` (path B) both write to the same Redis key. Path A has incomplete metadata (no content/keywords/priority). Path B overwrites path A but may fail.
3. **Generic query text**: "第X章相关剧情" produces poor semantic matching against stored summary embeddings.

## Scope

Fix data path only. Do NOT modify ContextBuilderService — RAG remains as Agent @Tool on-demand.

## Changes

### 1. Fix JSON.GET compatibility in fallback path

**File:** `RedisVectorServiceImpl.java`

Change `processVectorKey()` (line ~216) from:
```java
String jsonDoc = redisTemplate.opsForValue().get(key);
```
To use `JSON.GET`:
```java
String jsonDoc = redisTemplate.execute((RedisCallback<String>) connection -> {
    byte[] result = (byte[]) connection.execute(
        "JSON.GET", key.getBytes(StandardCharsets.UTF_8), "$".getBytes(StandardCharsets.UTF_8));
    if (result == null) return null;
    // JSON.GET key $ returns [{...}], extract the first element
    String arrayJson = new String(result, StandardCharsets.UTF_8);
    if (arrayJson.startsWith("[") && arrayJson.endsWith("]")) {
        return arrayJson.substring(1, arrayJson.length() - 1);
    }
    return arrayJson;
});
```

Also fix `deleteVector()` (line ~388) which uses `opsForValue().get()` to read before deleting:
```java
String jsonDoc = redisTemplate.execute((RedisCallback<String>) connection -> {
    byte[] result = (byte[]) connection.execute(
        "JSON.GET", fullKey.getBytes(StandardCharsets.UTF_8), "$".getBytes(StandardCharsets.UTF_8));
    if (result == null) return null;
    String arrayJson = new String(result, StandardCharsets.UTF_8);
    if (arrayJson.startsWith("[") && arrayJson.endsWith("]")) {
        return arrayJson.substring(1, arrayJson.length() - 1);
    }
    return arrayJson;
});
```

Also fix `scanAndSearch()` — the SCAN cursor returns full keys, but `processVectorKey()` needs the full key. Already passing full key from scan, so no change needed there.

### 2. Remove dual save path A

**File:** `ChapterSaveService.java`

In `saveToMongoAsync()`, remove the entire vectorization block (lines ~137-153). The method should only do:
- `ragService.saveSummary(summary)` — handles both MongoDB save AND Redis vector save
- `updateNovelGraph()`
- `storyStateService.updateAfterChapter()`

Remove the `embeddingService` and `redisVectorService` fields from `ChapterSaveService` since they're no longer used.

### 3. Optimize query text for RAG retrieval

**File:** `RagServiceImpl.java`

In `retrieveContext()` and `assembleContext()`, replace the generic query text with content from recent summaries:

```java
// Build query from recent summaries for better semantic matching
List<ChapterSummary> recentSummaries = getRecentSummaries(novelId, currentChapterNum, 3);
StringBuilder queryBuilder = new StringBuilder();
for (ChapterSummary s : recentSummaries) {
    if (s.getContent() != null) {
        queryBuilder.append(s.getContent(), 0, Math.min(s.getContent().length(), 200));
        queryBuilder.append(" ");
    }
}
String queryText = queryBuilder.length() > 0
    ? queryBuilder.toString().trim()
    : "第" + currentChapterNum + "章相关剧情";
```

## Files Modified

| File | Change |
|------|--------|
| `RedisVectorServiceImpl.java` | Fix `processVectorKey()` and `deleteVector()` to use JSON.GET |
| `ChapterSaveService.java` | Remove path A vectorization block, remove unused fields |
| `RagServiceImpl.java` | Optimize query text in `retrieveContext()` and `assembleContext()` |
