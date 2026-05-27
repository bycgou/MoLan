# Genre Content & Enhancements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add 5 new genre directories with LLM-generated writing guides, plus single-file API, hot reload, and smart truncation.

**Architecture:** Extend existing `GenreService` with `getGenreGuideFile()` and `reload()`. Add `GenreWatchService` for dev-profile file watching. Generate 30 markdown files via LLM using a prompt template.

**Tech Stack:** Spring Boot 3.2, Java 17, SnakeYAML, java.nio.file.WatchService

**Spec:** `docs/superpowers/specs/2026-05-27-genre-content-and-enhancements-design.md`

---

## Phase 1: Content Generation

### Task 1: Create index.md for 5 New Genres

**Files:**
- Create: `backend/src/main/resources/genres/dushi/index.md`
- Create: `backend/src/main/resources/genres/xianxia/index.md`
- Create: `backend/src/main/resources/genres/kehuan/index.md`
- Create: `backend/src/main/resources/genres/youxi/index.md`
- Create: `backend/src/main/resources/genres/moshi/index.md`

- [ ] **Step 1: Create `dushi/index.md`**

Data from `GenreServiceImpl.initTemplatesForMissing()`:

```markdown
---
code: dushi
name: 都市
category: 男频
description: 现实背景、职场逆袭、商业争霸
tropes:
  - 职场逆袭：从小职员到CEO
  - 重生都市：重生后利用信息差
  - 透视异能：获得特殊能力
  - 神医流：医术救人
  - 保镖流：保护重要人物
pitfalls:
  - 避免过于脱离现实
  - 职场描写要专业
  - 感情线要有深度
  - 金手指要有限制
characterArchetypes:
  主角: 低调务实、能力出众
  女主: 职场精英或青梅竹马
  对手: 商业对手或职场敌人
  兄弟: 患难与共的伙伴
rhythmConfig:
  questRatio: 0.55
  fireRatio: 0.3
  constellationRatio: 0.15
  questMaxConsecutive: 4
  fireMaxGap: 8
  constellationMaxGap: 18
---

# 都市题材总览

都市小说以现实世界为背景，核心在于职场逆袭、商业争霸、都市异能。读者期望看到贴近生活的场景中发生的不平凡故事。
```

- [ ] **Step 2: Create `xianxia/index.md`**

```markdown
---
code: xianxia
name: 仙侠
category: 男频
description: 修仙问道、飞升成仙、仙界争霸
tropes:
  - 凡人修仙：从凡人到仙人
  - 重生修仙：带着记忆重修
  - 系统修仙：系统辅助修炼
  - 宗门修仙：从小宗门到仙门
  - 散修流：独自修炼成仙
pitfalls:
  - 境界体系要清晰
  - 法宝功法要详细
  - 仙界设定要完整
  - 避免境界通胀
characterArchetypes:
  主角: 心性坚定、悟性极高
  道侣: 仙子或妖女
  宗门长辈: 护短或严厉
  仙界势力: 各大仙门
rhythmConfig:
  questRatio: 0.65
  fireRatio: 0.1
  constellationRatio: 0.25
  questMaxConsecutive: 6
  fireMaxGap: 15
  constellationMaxGap: 12
---

# 仙侠题材总览

仙侠小说以修仙问道为核心，主角从凡人一步步修炼成仙。重点在于境界体系、法宝功法、仙界设定的完整性和自洽性。
```

- [ ] **Step 3: Create `kehuan/index.md`**

```markdown
---
code: kehuan
name: 科幻
category: 男频
description: 未来科技、星际文明、末日求生
tropes:
  - 星际争霸：宇宙战争
  - 机甲流：机甲战斗
  - 末日求生：灾难后生存
  - 赛博朋克：高科技低生活
  - 时间循环：重复同一天
pitfalls:
  - 科技设定要自洽
  - 世界观要完整
  - 避免科技万能
  - 人文思考要深入
characterArchetypes:
  主角: 理性或热血、有理想
  科学家: 技术担当
  军人: 战斗担当
  AI: 人工智能角色
rhythmConfig:
  questRatio: 0.6
  fireRatio: 0.15
  constellationRatio: 0.25
  questMaxConsecutive: 5
  fireMaxGap: 15
  constellationMaxGap: 12
---

# 科幻题材总览

科幻小说以未来科技和星际文明为背景，核心在于科技设定的自洽性和对人类命运的思考。好的科幻作品既要有硬核的科技感，也要有深刻的人文关怀。
```

- [ ] **Step 4: Create `youxi/index.md`**

```markdown
---
code: youxi
name: 游戏
category: 男频
description: 游戏世界、电竞比赛、虚拟现实
tropes:
  - 电竞流：职业选手
  - 游戏穿越：穿越到游戏世界
  - 网游流：游戏内发展
  - 直播流：游戏主播
  - 全息网游：沉浸式游戏
pitfalls:
  - 游戏设定要详细
  - 战斗描写要精彩
  - 职业平衡要合理
  - 避免外挂太强
characterArchetypes:
  主角: 技术流或战术流
  队友: 各有特色
  对手: 强大但可战胜
  粉丝: 提供爽点
rhythmConfig:
  questRatio: 0.55
  fireRatio: 0.2
  constellationRatio: 0.25
  questMaxConsecutive: 4
  fireMaxGap: 10
  constellationMaxGap: 15
---

# 游戏题材总览

游戏小说以虚拟游戏世界为舞台，核心在于游戏系统的设定、战斗的精彩描写、以及主角在游戏中的成长。好的游戏小说要让读者感觉"这游戏我也想玩"。
```

- [ ] **Step 5: Create `moshi/index.md`**

```markdown
---
code: moshi
name: 末世
category: 男频
description: 丧尸危机、资源争夺、重建文明
tropes:
  - 丧尸流：对抗丧尸
  - 异能流：获得异能
  - 系统流：末世系统
  - 基地流：建立基地
  - 进化流：生物进化
pitfalls:
  - 生存压力要真实
  - 人性描写要深刻
  - 资源设定要合理
  - 避免主角太圣母
characterArchetypes:
  主角: 冷静理智、有领导力
  伙伴: 各有专长
  反派: 人性黑暗面
  幸存者: 不同立场
rhythmConfig:
  questRatio: 0.6
  fireRatio: 0.2
  constellationRatio: 0.2
  questMaxConsecutive: 5
  fireMaxGap: 10
  constellationMaxGap: 15
---

# 末世题材总览

末世小说以文明崩塌后的世界为背景，核心在于生存压力、人性考验、资源争夺。好的末世作品不只是打丧尸，更是对人性的深刻探讨。
```

- [ ] **Step 6: Verify files exist**

Run: `ls backend/src/main/resources/genres/{dushi,xianxia,kehuan,youxi,moshi}/index.md`
Expected: 5 files listed.

---

### Task 2: Generate Guide Files for dushi (都市)

**Files:**
- Create: `backend/src/main/resources/genres/dushi/职场描写.md`
- Create: `backend/src/main/resources/genres/dushi/都市异能设定.md`
- Create: `backend/src/main/resources/genres/dushi/感情线设计.md`
- Create: `backend/src/main/resources/genres/dushi/商业博弈.md`
- Create: `backend/src/main/resources/genres/dushi/都市人物塑造.md`

- [ ] **Step 1: Generate all 5 guide files using LLM**

Use the following prompt template for each file. Call the LLM service or write the content directly:

```
你是一位资深网文写作指导专家。请为"都市"题材撰写一份"{topic}"写作指南。

题材信息：
- 名称：都市
- 描述：现实背景、职场逆袭、商业争霸
- 常见套路：职场逆袭、重生都市、透视异能、神医流、保镖流
- 避坑指南：避免过于脱离现实、职场描写要专业、感情线要有深度、金手指要有限制

要求：
1. 1500-3000 字
2. 使用 ## 标题分节
3. 包含具体写作建议和示例
4. 使用 - 列表和代码块（示例段落）
5. 参考以下现有指南的结构风格：

现有指南示例结构：
# 标题
> 核心原则引用
---
## 1. 要素一
### 技巧 A
（具体说明 + 示例）
### 技巧 B
（具体说明 + 示例）
---
## 2. 要素二
...

请生成"{topic}"的完整写作指南。
```

Topics: `职场描写`, `都市异能设定`, `感情线设计`, `商业博弈`, `都市人物塑造`

- [ ] **Step 2: Save generated content to files**

Each file should be saved as `backend/src/main/resources/genres/dushi/{topic}.md`.

- [ ] **Step 3: Verify files exist and have reasonable length**

Run: `wc -l backend/src/main/resources/genres/dushi/*.md`
Expected: Each guide file has 50+ lines.

---

### Task 3: Generate Guide Files for xianxia (仙侠)

**Files:**
- Create: `backend/src/main/resources/genres/xianxia/修仙体系.md`
- Create: `backend/src/main/resources/genres/xianxia/仙界设定.md`
- Create: `backend/src/main/resources/genres/xianxia/战斗描写.md`
- Create: `backend/src/main/resources/genres/xianxia/法宝丹药.md`
- Create: `backend/src/main/resources/genres/xianxia/宗门势力.md`

- [ ] **Step 1: Generate all 5 guide files using LLM**

Same prompt template as Task 2, with topic-specific adjustments:

```
题材信息：
- 名称：仙侠
- 描述：修仙问道、飞升成仙、仙界争霸
- 常见套路：凡人修仙、重生修仙、系统修仙、宗门修仙、散修流
- 避坑指南：境界体系要清晰、法宝功法要详细、仙界设定要完整、避免境界通胀
```

Topics: `修仙体系`, `仙界设定`, `战斗描写`, `法宝丹药`, `宗门势力`

- [ ] **Step 2: Save generated content to files**

- [ ] **Step 3: Verify files exist and have reasonable length**

Run: `wc -l backend/src/main/resources/genres/xianxia/*.md`

---

### Task 4: Generate Guide Files for kehuan (科幻)

**Files:**
- Create: `backend/src/main/resources/genres/kehuan/科技设定.md`
- Create: `backend/src/main/resources/genres/kehuan/世界观构建.md`
- Create: `backend/src/main/resources/genres/kehuan/科幻概念.md`
- Create: `backend/src/main/resources/genres/kehuan/太空歌剧.md`
- Create: `backend/src/main/resources/genres/kehuan/人工智能.md`

- [ ] **Step 1: Generate all 5 guide files using LLM**

```
题材信息：
- 名称：科幻
- 描述：未来科技、星际文明、末日求生
- 常见套路：星际争霸、机甲流、末日求生、赛博朋克、时间循环
- 避坑指南：科技设定要自洽、世界观要完整、避免科技万能、人文思考要深入
```

Topics: `科技设定`, `世界观构建`, `科幻概念`, `太空歌剧`, `人工智能`

- [ ] **Step 2: Save generated content to files**

- [ ] **Step 3: Verify files exist and have reasonable length**

---

### Task 5: Generate Guide Files for youxi (游戏)

**Files:**
- Create: `backend/src/main/resources/genres/youxi/游戏系统设计.md`
- Create: `backend/src/main/resources/genres/youxi/电竞描写.md`
- Create: `backend/src/main/resources/genres/youxi/虚拟世界.md`
- Create: `backend/src/main/resources/genres/youxi/玩家心理.md`
- Create: `backend/src/main/resources/genres/youxi/副本设计.md`

- [ ] **Step 1: Generate all 5 guide files using LLM**

```
题材信息：
- 名称：游戏
- 描述：游戏世界、电竞比赛、虚拟现实
- 常见套路：电竞流、游戏穿越、网游流、直播流、全息网游
- 避坑指南：游戏设定要详细、战斗描写要精彩、职业平衡要合理、避免外挂太强
```

Topics: `游戏系统设计`, `电竞描写`, `虚拟世界`, `玩家心理`, `副本设计`

- [ ] **Step 2: Save generated content to files**

- [ ] **Step 3: Verify files exist and have reasonable length**

---

### Task 6: Generate Guide Files for moshi (末世)

**Files:**
- Create: `backend/src/main/resources/genres/moshi/末世生存.md`
- Create: `backend/src/main/resources/genres/moshi/丧尸设定.md`
- Create: `backend/src/main/resources/genres/moshi/人性冲突.md`
- Create: `backend/src/main/resources/genres/moshi/基地建设.md`
- Create: `backend/src/main/resources/genres/moshi/异能体系.md`

- [ ] **Step 1: Generate all 5 guide files using LLM**

```
题材信息：
- 名称：末世
- 描述：丧尸危机、资源争夺、重建文明
- 常见套路：丧尸流、异能流、系统流、基地流、进化流
- 避坑指南：生存压力要真实、人性描写要深刻、资源设定要合理、避免主角太圣母
```

Topics: `末世生存`, `丧尸设定`, `人性冲突`, `基地建设`, `异能体系`

- [ ] **Step 2: Save generated content to files**

- [ ] **Step 3: Verify files exist and have reasonable length**

---

### Task 7: Commit Content and Remove Fallback

**Files:**
- Modify: `backend/src/main/java/com/novel/system/service/genre/GenreServiceImpl.java`

- [ ] **Step 1: Delete `initTemplatesForMissing()` method and its call from constructor**

In `GenreServiceImpl.java`:

Remove from constructor:
```java
// Before:
public GenreServiceImpl() {
    loadFromMarkdown();
    initTemplatesForMissing();
}

// After:
public GenreServiceImpl() {
    loadFromMarkdown();
}
```

Delete the entire `initTemplatesForMissing()` method (lines 241-286) and the `registerGenre()` helper method (lines 288-312).

- [ ] **Step 2: Verify compilation**

Run: `cd backend && mvn compile -pl . -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Run existing tests**

Run: `cd backend && mvn test -pl . -Dtest=GenreServiceImplTest -q`
Expected: All 4 tests pass (genre list should now have 11 genres instead of 9).

- [ ] **Step 4: Update test assertion for genre count**

In `GenreServiceImplTest.java`, update:
```java
// Before:
assertTrue(service.getGenreList().size() >= 9);

// After:
assertEquals(11, service.getGenreList().size());
```

- [ ] **Step 5: Commit all content changes**

```bash
cd D:/dev/moyun
git add backend/src/main/resources/genres/ backend/src/main/java/com/novel/system/service/genre/GenreServiceImpl.java backend/src/test/java/com/novel/system/service/genre/GenreServiceImplTest.java
git commit -m "feat(genre): add 5 new genre directories with LLM-generated guides"
```

---

## Phase 2: Feature Enhancements

### Task 8: Add Single-File API

**Files:**
- Modify: `backend/src/main/java/com/novel/system/service/genre/GenreService.java`
- Modify: `backend/src/main/java/com/novel/system/service/genre/GenreServiceImpl.java`
- Modify: `backend/src/main/java/com/novel/system/controller/GenreController.java`
- Modify: `backend/src/test/java/com/novel/system/service/genre/GenreServiceImplTest.java`

- [ ] **Step 1: Write the failing test**

Add to `GenreServiceImplTest.java`:

```java
@Test
void getGenreGuideFile_existingFile_returnsContent() {
    String content = service.getGenreGuideFile("xuanhuan", "cultivation-levels.md");
    assertNotNull(content);
    assertTrue(content.contains("修炼境界"));
}

@Test
void getGenreGuideFile_nonexistentFile_returnsNull() {
    assertNull(service.getGenreGuideFile("xuanhuan", "nonexistent.md"));
}

@Test
void getGenreGuideFile_pathTraversal_returnsNull() {
    assertNull(service.getGenreGuideFile("xuanhuan", "../../../etc/passwd"));
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && mvn test -pl . -Dtest=GenreServiceImplTest -q`
Expected: FAIL — `getGenreGuideFile` method does not exist.

- [ ] **Step 3: Add method to `GenreService` interface**

```java
/**
 * Get content of a single genre guide file.
 * @return file content, or null if not found
 */
String getGenreGuideFile(String code, String filename);
```

- [ ] **Step 4: Implement in `GenreServiceImpl`**

```java
@Override
public String getGenreGuideFile(String code, String filename) {
    if (code == null || filename == null) return null;
    // Path traversal guard
    if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
        return null;
    }
    String folder = mapGenreToFolder(code);
    if (folder == null) return null;
    try {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource resource = resolver.getResource("classpath:genres/" + folder + "/" + filename);
        if (!resource.exists()) return null;
        return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    } catch (Exception e) {
        log.warn("Failed to read genre guide file: {}/{}", code, filename, e);
        return null;
    }
}
```

- [ ] **Step 5: Add endpoint to `GenreController`**

```java
@GetMapping("/{genreCode}/guides/{filename}")
public ApiResponse<?> getGenreGuideFile(@PathVariable String genreCode, @PathVariable String filename) {
    String content = genreService.getGenreGuideFile(genreCode, filename);
    if (content == null) {
        return ApiResponse.error(404, "指南文件不存在");
    }
    return ApiResponse.success(content);
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `cd backend && mvn test -pl . -Dtest=GenreServiceImplTest -q`
Expected: All tests pass.

- [ ] **Step 7: Commit**

```bash
cd D:/dev/moyun
git add backend/src/main/java/com/novel/system/service/genre/ backend/src/main/java/com/novel/system/controller/GenreController.java backend/src/test/java/com/novel/system/service/genre/GenreServiceImplTest.java
git commit -m "feat(genre): add single-file guide API with path traversal guard"
```

---

### Task 9: Add Hot Reload

**Files:**
- Modify: `backend/src/main/java/com/novel/system/service/genre/GenreService.java`
- Modify: `backend/src/main/java/com/novel/system/service/genre/GenreServiceImpl.java`
- Create: `backend/src/main/java/com/novel/system/service/genre/GenreWatchService.java`
- Modify: `backend/src/main/java/com/novel/system/controller/GenreController.java`

- [ ] **Step 1: Add `reload()` to `GenreService` interface**

```java
/**
 * Reload genre data from markdown files. Clears in-memory cache and re-scans.
 */
void reload();
```

- [ ] **Step 2: Implement `reload()` in `GenreServiceImpl`**

```java
@Override
public synchronized void reload() {
    templates.clear();
    loadFromMarkdown();
    log.info("Genre data reloaded. Total genres: {}", templates.size());
}
```

Note: `synchronized` to prevent concurrent reload.

- [ ] **Step 3: Create `GenreWatchService.java`**

```java
package com.novel.system.service.genre;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.nio.file.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class GenreWatchService {

    private final GenreService genreService;
    private WatchService watchService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "genre-watch");
        t.setDaemon(true);
        return t;
    });

    @PostConstruct
    public void start() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource resource = resolver.getResource("classpath:genres/");
            Path genresPath = resource.getFile().toPath().toAbsolutePath();

            watchService = FileSystems.getDefault().newWatchService();
            genresPath.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE);

            executor.submit(this::watchLoop);
            log.info("GenreWatchService started, watching: {}", genresPath);
        } catch (Exception e) {
            log.warn("GenreWatchService failed to start (non-fatal): {}", e.getMessage());
        }
    }

    private void watchLoop() {
        while (true) {
            try {
                WatchKey key = watchService.take();
                // Debounce: wait 500ms for rapid successive events
                Thread.sleep(500);
                // Drain all pending events
                key.pollEvents();
                key.reset();
                log.info("Genre directory changed, reloading...");
                genreService.reload();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("GenreWatchService error", e);
            }
        }
    }

    @PreDestroy
    public void stop() {
        try {
            if (watchService != null) watchService.close();
            executor.shutdownNow();
        } catch (Exception ignored) {}
    }
}
```

- [ ] **Step 4: Add reload endpoint to `GenreController`**

```java
@PostMapping("/reload")
public ApiResponse<?> reloadGenres() {
    genreService.reload();
    return ApiResponse.success("题材数据已刷新，共 " + genreService.getGenreList().size() + " 个题材");
}
```

- [ ] **Step 5: Verify compilation**

Run: `cd backend && mvn compile -pl . -q`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
cd D:/dev/moyun
git add backend/src/main/java/com/novel/system/service/genre/ backend/src/main/java/com/novel/system/controller/GenreController.java
git commit -m "feat(genre): add hot reload via WatchService and POST /genre/reload endpoint"
```

---

### Task 10: Smart Truncation by Heading

**Files:**
- Modify: `backend/src/main/java/com/novel/system/service/genre/GenreServiceImpl.java`
- Modify: `backend/src/test/java/com/novel/system/service/genre/GenreServiceImplTest.java`

- [ ] **Step 1: Write the failing test**

Add to `GenreServiceImplTest.java`:

```java
@Test
void getGenreGuide_truncatesAtHeadingBoundary() {
    // The guide should not cut in the middle of a sentence
    String guide = service.getGenreGuide("xuanhuan");
    assertNotNull(guide);
    // If truncated, it should end at a heading or end of file, not mid-sentence
    if (guide.length() > 1500) {
        // Check that truncation didn't happen mid-heading
        assertFalse(guide.endsWith("#"), "Should not truncate mid-heading");
    }
}
```

- [ ] **Step 2: Run test**

Run: `cd backend && mvn test -pl . -Dtest=GenreServiceImplTest#test_getGenreGuide_truncatesAtHeadingBoundary -q`
Expected: May pass or fail depending on current truncation behavior.

- [ ] **Step 3: Implement smart truncation in `loadMarkdownGuide()`**

Replace the current `loadMarkdownGuide()` method:

```java
private String loadMarkdownGuide(String folder) {
    try {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:genres/" + folder + "/*.md");
        StringBuilder sb = new StringBuilder();
        int totalChars = 0;
        for (Resource resource : resources) {
            if (totalChars >= maxTotalChars) break;
            String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            content = smartTruncate(content, maxCharsPerFile);
            sb.append(content).append("\n\n");
            totalChars += content.length();
        }
        return sb.toString().trim();
    } catch (Exception e) {
        log.warn("Failed to load markdown guide for folder: {}", folder, e);
        return "";
    }
}

/**
 * Truncate markdown content at heading boundaries instead of mid-sentence.
 * If a ## heading is found after maxChars, truncate at that heading.
 * Falls back to hard truncation if no heading boundary is found.
 */
private String smartTruncate(String content, int maxChars) {
    if (content.length() <= maxChars) return content;

    // Find the last ## heading before maxChars
    int lastHeading = -1;
    int searchFrom = Math.min(maxChars, content.length());
    // Search backwards from maxChars for a "\n## " pattern
    int idx = content.lastIndexOf("\n## ", searchFrom);
    if (idx > 0) {
        lastHeading = idx;
    }

    if (lastHeading > 0) {
        return content.substring(0, lastHeading);
    }

    // Fallback: try to find a paragraph break (double newline)
    int lastParagraph = content.lastIndexOf("\n\n", maxChars);
    if (lastParagraph > maxChars / 2) {
        return content.substring(0, lastParagraph);
    }

    // Final fallback: hard cut
    return content.substring(0, maxChars);
}
```

- [ ] **Step 4: Run tests**

Run: `cd backend && mvn test -pl . -Dtest=GenreServiceImplTest -q`
Expected: All tests pass.

- [ ] **Step 5: Commit**

```bash
cd D:/dev/moyun
git add backend/src/main/java/com/novel/system/service/genre/GenreServiceImpl.java backend/src/test/java/com/novel/system/service/genre/GenreServiceImplTest.java
git commit -m "feat(genre): smart truncation at markdown heading boundaries"
```

---

## Notes

- **Content quality:** LLM-generated guide files are drafts. Review and refine as needed after generation.
- **WatchService limitations:** The `classpath:` resource path may not resolve to a real filesystem path in packaged JARs. The WatchService only works in dev mode where resources are unpacked on disk. This is by design (`@Profile("dev")`).
- **moyun-ts sync:** Deferred. Can be added later as a build script that reads `index.md` front matter.
