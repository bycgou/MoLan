# GraphRunner 架构重构实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 拆分 GraphRunner 上帝类，修复 5 个架构问题（扇出合并、状态重置、校验灰度、AuthorAgent 预筛、状态持久化）

**Architecture:** GraphRunner 拆为调度层（GraphRunner）+ 执行层（NodeExecutor），新增 MergeStrategy 接口替代硬编码合并，resetState 下沉到节点接口，校验改为加权评分 + FATAL/WARNING 分级，AuthorAgent 增加规则预筛，关键检查点持久化到 Redis。

**Tech Stack:** Java 17, Spring Boot 3, Lombok, Redis, JUnit 5 + Mockito

---

## 文件结构

### 新增文件
| 文件 | 职责 |
|------|------|
| `backend/src/main/java/com/novel/system/ai/graph/MergeStrategy.java` | 扇出合并策略接口 |
| `backend/src/main/java/com/novel/system/ai/graph/NodeExecutor.java` | 节点执行 + AuthorAgent 拦截 + 持久化 |
| `backend/src/main/java/com/novel/system/ai/graph/StateCheckpointService.java` | Redis 检查点保存/恢复/清理 |
| `backend/src/main/java/com/novel/system/ai/graph/StateSnapshot.java` | 检查点数据结构 |
| `backend/src/main/java/com/novel/system/ai/graph/ValidationConfig.java` | 校验权重 + 阈值配置 |
| `backend/src/main/java/com/novel/system/ai/graph/GradedAuditCalculator.java` | 加权评分计算 |
| `backend/src/test/java/com/novel/system/ai/graph/MergeStrategyTest.java` | MergeStrategy 单元测试 |
| `backend/src/test/java/com/novel/system/ai/graph/NodeExecutorTest.java` | NodeExecutor 单元测试 |
| `backend/src/test/java/com/novel/system/ai/graph/GradedAuditCalculatorTest.java` | 加权评分单元测试 |
| `backend/src/test/java/com/novel/system/ai/graph/StateCheckpointServiceTest.java` | 检查点服务单元测试 |

### 修改文件
| 文件 | 变更 |
|------|------|
| `backend/src/main/java/com/novel/system/ai/graph/NovelGraphNode.java` | 新增 `resetState()` default 方法 |
| `backend/src/main/java/com/novel/system/ai/graph/GraphDefinition.java` | FanOutGroup 增加 MergeStrategy 字段，fanOut builder 增加重载 |
| `backend/src/main/java/com/novel/system/ai/graph/GraphRunner.java` | 删除 AuthorAgent 拦截/mergeFanOutResult/resetStateForReroute，委托 NodeExecutor |
| `backend/src/main/java/com/novel/system/ai/graph/NovelAgentState.java` | AuditResult 新增 weightedScore/dimensionResults/fatalFailures/warnings + DimensionResult 内嵌类 |
| `backend/src/main/java/com/novel/system/ai/graph/OutlineNode.java` | 实现 resetState |
| `backend/src/main/java/com/novel/system/ai/graph/WritingNode.java` | 实现 resetState |
| `backend/src/main/java/com/novel/system/ai/graph/QualityScoringNode.java` | 删除 allPassed 计算，保留原始分数收集 |
| `backend/src/main/java/com/novel/system/ai/graph/AuditRouterNode.java` | 注入 GradedAuditCalculator，新增 gradedRoute/legacyRoute 双路径 |
| `backend/src/main/java/com/novel/system/ai/graph/AuthorAgentImpl.java` | beforeNode 插入预筛私有方法 |
| `backend/src/main/java/com/novel/system/ai/graph/AuthorAgentConfig.java` | 新增 preFilterEnabled、字数阈值配置 |
| `backend/src/main/java/com/novel/system/service/chapter/MultiAgentNovelService.java` | fanOut 调用传入 chapterPlanMerge |
| `backend/src/test/java/com/novel/system/ai/graph/GraphRunnerV2Test.java` | 适配 NodeExecutor 构造方式 |

---

## 第一批：resetState + MergeStrategy（无外部依赖）

### Task 1: NovelGraphNode 接口扩展 — resetState

**Files:**
- Modify: `backend/src/main/java/com/novel/system/ai/graph/NovelGraphNode.java`
- Modify: `backend/src/main/java/com/novel/system/ai/graph/OutlineNode.java`
- Modify: `backend/src/main/java/com/novel/system/ai/graph/WritingNode.java`

- [ ] **Step 1: 在 NovelGraphNode 接口新增 resetState default 方法**

```java
// NovelGraphNode.java — 在 maxRetries() 方法之后添加

/**
 * 当流程重新路由到该节点时，声明如何重置状态。
 * 默认不做任何重置。
 *
 * @param state  当前状态
 * @param source 触发重置的来源节点名（预留参数，当前实现可忽略）
 */
default NovelAgentState resetState(NovelAgentState state, String source) {
    return state;
}
```

- [ ] **Step 2: OutlineNode 实现 resetState**

```java
// OutlineNode.java — 在 nodeName() 方法之后添加

@Override
public NovelAgentState resetState(NovelAgentState state, String source) {
    return state.toBuilder()
            .chapterPlan(null)
            .preWriteValidation(null)
            .selectedIdea(null)
            .implementationIdeas(null)
            .build();
}
```

- [ ] **Step 3: WritingNode 实现 resetState**

```java
// WritingNode.java — 在 nodeName() 方法之后添加

@Override
public NovelAgentState resetState(NovelAgentState state, String source) {
    NovelAgentState.DraftContent draft = state.getDraftContent();
    if (draft != null) {
        return state.toBuilder()
                .draftContent(draft.toBuilder()
                        .severity(NovelAgentState.Severity.HIGH)
                        .build())
                .build();
    }
    return state;
}
```

- [ ] **Step 4: 编译验证**

Run: `cd D:/dev/moyun/backend && mvn compile -pl . -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/novel/system/ai/graph/NovelGraphNode.java \
        backend/src/main/java/com/novel/system/ai/graph/OutlineNode.java \
        backend/src/main/java/com/novel/system/ai/graph/WritingNode.java
git commit -m "feat(graph): add resetState default method to NovelGraphNode interface"
```

---

### Task 2: GraphRunner — 用 resetState 替换 resetStateForReroute

**Files:**
- Modify: `backend/src/main/java/com/novel/system/ai/graph/GraphRunner.java`

- [ ] **Step 1: 删除 resetStateForReroute 方法**

删除 GraphRunner.java 第 331-354 行的 `resetStateForReroute` 私有方法。

- [ ] **Step 2: 替换第一处调用（AuthorAgent REDIRECT，原第 69 行）**

将：
```java
state = resetStateForReroute(state, target);
```
替换为：
```java
NovelGraphNode targetGraphNode = nodes.get(target);
if (targetGraphNode != null) {
    state = targetGraphNode.resetState(state, currentNode);
}
```

- [ ] **Step 3: 替换第二处调用（正常路由，原第 134 行）**

将：
```java
state = resetStateForReroute(state, nextNode);
```
替换为：
```java
NovelGraphNode nextGraphNode = nodes.get(nextNode);
if (nextGraphNode != null) {
    state = nextGraphNode.resetState(state, currentNode);
}
```

- [ ] **Step 4: 编译验证**

Run: `cd D:/dev/moyun/backend && mvn compile -pl . -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/novel/system/ai/graph/GraphRunner.java
git commit -m "refactor(graph): replace resetStateForReroute with node.resetState()"
```

---

### Task 3: MergeStrategy 接口 + 默认策略

**Files:**
- Create: `backend/src/main/java/com/novel/system/ai/graph/MergeStrategy.java`
- Create: `backend/src/test/java/com/novel/system/ai/graph/MergeStrategyTest.java`

- [ ] **Step 1: 写 MergeStrategy 单元测试**

```java
package com.novel.system.ai.graph;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MergeStrategyTest {

    @Test
    void defaultStrategy_accumulatesTracesFromAllBranches() {
        NovelAgentState base = NovelAgentState.builder()
                .executionTrace(new ArrayList<>(List.of(
                        NodeExecutionRecord.success("base_node", Mode.FULL,
                                java.time.Instant.now(), java.time.Instant.now(),
                                null, java.util.Map.of()))))
                .errors(new ArrayList<>())
                .llmCalls(new ArrayList<>())
                .build();

        NovelAgentState branch1 = base.toBuilder()
                .executionTrace(new ArrayList<>(List.of(
                        NodeExecutionRecord.success("branch1", Mode.FULL,
                                java.time.Instant.now(), java.time.Instant.now(),
                                null, java.util.Map.of()))))
                .build();

        NovelAgentState branch2 = base.toBuilder()
                .executionTrace(new ArrayList<>(List.of(
                        NodeExecutionRecord.success("branch2", Mode.FULL,
                                java.time.Instant.now(), java.time.Instant.now(),
                                null, java.util.Map.of()))))
                .build();

        MergeStrategy strategy = MergeStrategy.defaultStrategy();
        NovelAgentState merged = strategy.merge(base, List.of(branch1, branch2));

        assertEquals(3, merged.getExecutionTrace().size()); // base + branch1 + branch2
        assertEquals("base_node", merged.getExecutionTrace().get(0).nodeName());
        assertEquals("branch1", merged.getExecutionTrace().get(1).nodeName());
        assertEquals("branch2", merged.getExecutionTrace().get(2).nodeName());
    }

    @Test
    void chapterPlanMerge_combinesClueAndCharacterActions() {
        NovelAgentState base = NovelAgentState.builder()
                .chapterPlan(NovelAgentState.ChapterPlan.builder()
                        .summary("test").build())
                .executionTrace(new ArrayList<>())
                .errors(new ArrayList<>())
                .llmCalls(new ArrayList<>())
                .build();

        NovelAgentState clueBranch = base.toBuilder()
                .chapterPlan(NovelAgentState.ChapterPlan.builder()
                        .clueActions(List.of(
                                NovelAgentState.ClueAction.builder()
                                        .clueId("c1").action("PLANT").build()))
                        .build())
                .build();

        NovelAgentState charBranch = base.toBuilder()
                .chapterPlan(NovelAgentState.ChapterPlan.builder()
                        .characterActions(List.of(
                                NovelAgentState.CharacterAction.builder()
                                        .characterName("Alice").build()))
                        .build())
                .build();

        MergeStrategy strategy = MergeStrategy.chapterPlanMerge();
        NovelAgentState merged = strategy.merge(base, List.of(clueBranch, charBranch));

        assertNotNull(merged.getChapterPlan().getClueActions());
        assertEquals(1, merged.getChapterPlan().getClueActions().size());
        assertEquals("c1", merged.getChapterPlan().getClueActions().get(0).getClueId());

        assertNotNull(merged.getChapterPlan().getCharacterActions());
        assertEquals(1, merged.getChapterPlan().getCharacterActions().size());
        assertEquals("Alice", merged.getChapterPlan().getCharacterActions().get(0).getCharacterName());
    }

    @Test
    void chapterPlanMerge_handlesNullChapterPlan() {
        NovelAgentState base = NovelAgentState.builder()
                .chapterPlan(NovelAgentState.ChapterPlan.builder().summary("test").build())
                .executionTrace(new ArrayList<>())
                .errors(new ArrayList<>())
                .llmCalls(new ArrayList<>())
                .build();

        // Failed branch returns state with null chapterPlan
        NovelAgentState failedBranch = base.toBuilder()
                .chapterPlan(null)
                .build();

        MergeStrategy strategy = MergeStrategy.chapterPlanMerge();
        NovelAgentState merged = strategy.merge(base, List.of(failedBranch));

        // Should not throw, base chapterPlan preserved
        assertNotNull(merged.getChapterPlan());
        assertEquals("test", merged.getChapterPlan().getSummary());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd D:/dev/moyun/backend && mvn test -pl . -Dtest=MergeStrategyTest -q`
Expected: FAIL — MergeStrategy class not found

- [ ] **Step 3: 实现 MergeStrategy 接口**

```java
package com.novel.system.ai.graph;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 扇出合并策略。
 * 合并语义：多个分支写同一字段时，后遍历的覆盖先遍历的。
 * 大多数场景下各分支写不同字段，不会冲突。
 */
@FunctionalInterface
public interface MergeStrategy {

    NovelAgentState merge(NovelAgentState base, List<NovelAgentState> results);

    /**
     * 默认策略：累积所有分支的 executionTrace、errors、llmCalls。
     */
    static MergeStrategy defaultStrategy() {
        return (base, results) -> {
            NovelAgentState.NovelAgentStateBuilder builder = base.toBuilder();

            List<NodeExecutionRecord> allTraces = new ArrayList<>(base.getExecutionTrace());
            List<StructuredError> allErrors = new ArrayList<>(base.getErrors());
            List<LlmCallRecord> allLlmCalls = new ArrayList<>(base.getLlmCalls());

            for (NovelAgentState result : results) {
                allTraces.addAll(result.getExecutionTrace());
                allErrors.addAll(result.getErrors());
                allLlmCalls.addAll(result.getLlmCalls());
            }

            return builder
                    .executionTrace(allTraces)
                    .errors(allErrors)
                    .llmCalls(allLlmCalls)
                    .build();
        };
    }

    /**
     * 章纲合并策略：在默认策略基础上，合并各分支的 chapterPlan 字段。
     * clue_planning 分支写 clueActions，character_planning 分支写 characterActions。
     */
    static MergeStrategy chapterPlanMerge() {
        return (base, results) -> {
            NovelAgentState merged = defaultStrategy().merge(base, results);

            NovelAgentState.ChapterPlan.ChapterPlanBuilder planBuilder =
                    merged.getChapterPlan() != null
                            ? merged.getChapterPlan().toBuilder()
                            : NovelAgentState.ChapterPlan.builder();

            for (NovelAgentState result : results) {
                if (result.getChapterPlan() == null) {
                    continue;
                }
                if (result.getChapterPlan().getClueActions() != null) {
                    planBuilder.clueActions(result.getChapterPlan().getClueActions());
                }
                if (result.getChapterPlan().getCharacterActions() != null) {
                    planBuilder.characterActions(result.getChapterPlan().getCharacterActions());
                }
            }

            return merged.toBuilder().chapterPlan(planBuilder.build()).build();
        };
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd D:/dev/moyun/backend && mvn test -pl . -Dtest=MergeStrategyTest -q`
Expected: 3 tests PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/novel/system/ai/graph/MergeStrategy.java \
        backend/src/test/java/com/novel/system/ai/graph/MergeStrategyTest.java
git commit -m "feat(graph): add MergeStrategy interface with defaultStrategy and chapterPlanMerge"
```

---

### Task 4: GraphDefinition — fanOut 支持 MergeStrategy

**Files:**
- Modify: `backend/src/main/java/com/novel/system/ai/graph/GraphDefinition.java`

- [ ] **Step 1: FanOutGroup record 增加 mergeStrategy 字段**

将：
```java
public record FanOutGroup(String source, List<String> targets, Mode mode) {}
```
改为：
```java
public record FanOutGroup(String source, List<String> targets, Mode mode, MergeStrategy mergeStrategy) {}
```

- [ ] **Step 2: 新增 getFanOutMergeStrategy 方法**

```java
public MergeStrategy getFanOutMergeStrategy(String nodeName, Mode mode) {
    FanOutGroup group = fanOutGroups.get(nodeName);
    if (group != null && group.mergeStrategy() != null) {
        return group.mergeStrategy();
    }
    return MergeStrategy.defaultStrategy();
}
```

- [ ] **Step 3: Builder — 修改现有 fanOut 方法，新增带 MergeStrategy 的重载**

将现有 fanOut 方法改为：
```java
public Builder fanOut(String source, List<String> targets, Mode mode) {
    fanOutGroups.put(source, new FanOutGroup(source, targets, mode, MergeStrategy.defaultStrategy()));
    return this;
}
```

新增重载：
```java
public Builder fanOut(String source, List<String> targets, Mode mode, MergeStrategy strategy) {
    fanOutGroups.put(source, new FanOutGroup(source, targets, mode, strategy));
    return this;
}
```

- [ ] **Step 4: 编译验证**

Run: `cd D:/dev/moyun/backend && mvn compile -pl . -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/novel/system/ai/graph/GraphDefinition.java
git commit -m "feat(graph): GraphDefinition.fanOut supports MergeStrategy parameter"
```

---

### Task 5: GraphRunner — executeFanOut 使用 MergeStrategy

**Files:**
- Modify: `backend/src/main/java/com/novel/system/ai/graph/GraphRunner.java`
- Modify: `backend/src/main/java/com/novel/system/service/chapter/MultiAgentNovelService.java`

- [ ] **Step 1: 删除 GraphRunner.mergeFanOutResult 方法**

删除 GraphRunner.java 第 194-222 行的 `mergeFanOutResult` 私有方法。

- [ ] **Step 2: 改造 executeFanOut 使用 MergeStrategy**

将 executeFanOut 方法（第 145-189 行）替换为：

```java
private NovelAgentState executeFanOut(String sourceNode, NovelAgentState state, Mode mode) {
    List<String> targets = definition.getFanOutTargets(sourceNode, mode);
    MergeStrategy strategy = definition.getFanOutMergeStrategy(sourceNode, mode);
    if (targets.isEmpty()) {
        return state;
    }

    log.info("[GraphRunner] Fan-out from {}: {}", sourceNode, targets);
    final NovelAgentState currentState = state;

    List<CompletableFuture<NovelAgentState>> futures = targets.stream()
            .map(target -> CompletableFuture.supplyAsync(() -> {
                NovelGraphNode node = nodes.get(target);
                if (node == null) {
                    log.warn("[GraphRunner] Fan-out node not registered: {}", target);
                    return StateHelper.appendError(currentState, new StructuredError(
                            target, ErrorStrategy.SKIP, "Node not registered",
                            null, java.time.Instant.now(), true));
                }
                try {
                    return node.apply(currentState);
                } catch (Exception e) {
                    log.error("[GraphRunner] Fan-out node {} failed: {}", target, e.getMessage());
                    return StateHelper.appendError(currentState, new StructuredError(
                            target, ErrorStrategy.SKIP, e.getMessage(),
                            e, java.time.Instant.now(), true));
                }
            }, parallelExecutor))
            .toList();

    List<NovelAgentState> results = new ArrayList<>();
    for (int i = 0; i < futures.size(); i++) {
        try {
            results.add(futures.get(i).get(60, TimeUnit.SECONDS));
        } catch (Exception e) {
            log.error("[GraphRunner] Fan-out target {} failed: {}", targets.get(i), e.getMessage());
            results.add(StateHelper.appendError(currentState, new StructuredError(
                    targets.get(i), ErrorStrategy.SKIP, e.getMessage(),
                    e, java.time.Instant.now(), true)));
        }
    }

    return strategy.merge(state, results);
}
```

- [ ] **Step 3: MultiAgentNovelService — fanOut 传入 chapterPlanMerge**

将：
```java
.fanOut("outline", List.of("clue_planning", "character_planning"), Mode.FULL)
```
改为：
```java
.fanOut("outline", List.of("clue_planning", "character_planning"), Mode.FULL, MergeStrategy.chapterPlanMerge())
```

- [ ] **Step 4: 编译验证**

Run: `cd D:/dev/moyun/backend && mvn compile -pl . -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: 运行现有测试**

Run: `cd D:/dev/moyun/backend && mvn test -pl . -Dtest=GraphRunnerV2Test -q`
Expected: 5 tests PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/novel/system/ai/graph/GraphRunner.java \
        backend/src/main/java/com/novel/system/service/chapter/MultiAgentNovelService.java
git commit -m "refactor(graph): executeFanOut uses MergeStrategy instead of hardcoded mergeFanOutResult"
```

---

## 第二批：ValidationLayer（依赖 AuditResult 字段扩展）

### Task 6: AuditResult 扩展 — 新增加权评分字段

**Files:**
- Modify: `backend/src/main/java/com/novel/system/ai/graph/NovelAgentState.java`

- [ ] **Step 1: AuditResult 新增字段和 DimensionResult 内嵌类**

在 `NovelAgentState.java` 的 `AuditResult` 类中，在 `suggestions` 字段之后添加：

```java
// === 加权评分（新增） ===
private double weightedScore;
private List<DimensionResult> dimensionResults;
private List<String> fatalFailures;
private List<String> warnings;

@Getter
@Builder
@AllArgsConstructor
public static class DimensionResult {
    private String dimension;
    private boolean passed;
    private double score;
    private double weight;
    private String severity;
    private String detail;
}
```

- [ ] **Step 2: 编译验证**

Run: `cd D:/dev/moyun/backend && mvn compile -pl . -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/novel/system/ai/graph/NovelAgentState.java
git commit -m "feat(graph): add weightedScore/dimensionResults/fatalFailures/warnings to AuditResult"
```

---

### Task 7: ValidationConfig + GradedAuditCalculator

**Files:**
- Create: `backend/src/main/java/com/novel/system/ai/graph/ValidationConfig.java`
- Create: `backend/src/main/java/com/novel/system/ai/graph/GradedAuditCalculator.java`
- Create: `backend/src/test/java/com/novel/system/ai/graph/GradedAuditCalculatorTest.java`

- [ ] **Step 1: 写 GradedAuditCalculator 单元测试**

```java
package com.novel.system.ai.graph;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GradedAuditCalculatorTest {

    private ValidationConfig config;
    private GradedAuditCalculator calculator;

    @BeforeEach
    void setUp() {
        config = new ValidationConfig();
        config.setUseGradedAudit(true);
        config.setPassThreshold(60.0);
        config.setDimensions(Map.of(
                "ooc", new ValidationConfig.DimensionConfig(0.20, ValidationConfig.Severity.FATAL),
                "logic", new ValidationConfig.DimensionConfig(0.20, ValidationConfig.Severity.FATAL),
                "setting", new ValidationConfig.DimensionConfig(0.15, ValidationConfig.Severity.FATAL),
                "clue", new ValidationConfig.DimensionConfig(0.15, ValidationConfig.Severity.WARNING),
                "rhythm", new ValidationConfig.DimensionConfig(0.10, ValidationConfig.Severity.WARNING),
                "quality", new ValidationConfig.DimensionConfig(0.10, ValidationConfig.Severity.WARNING),
                "clueLanding", new ValidationConfig.DimensionConfig(0.10, ValidationConfig.Severity.WARNING)
        ));
        calculator = new GradedAuditCalculator(config);
    }

    @Test
    void allPass_highScore() {
        NovelAgentState.AuditResult audit = NovelAgentState.AuditResult.builder()
                .oocPassed(true).oocScore(90)
                .logicPassed(true)
                .settingPassed(true)
                .cluePassed(true)
                .rhythmPassed(true)
                .qualityPassed(true).qualityScore(85.0)
                .clueLandingPassed(true)
                .build();

        GradedAuditCalculator.GradedResult result = calculator.calculate(audit);

        assertTrue(result.isAllPassed());
        assertTrue(result.getFatalFailures().isEmpty());
        assertTrue(result.getWarnings().isEmpty());
        // 0.20*90 + 0.20*100 + 0.15*100 + 0.15*100 + 0.10*100 + 0.10*85 + 0.10*100 = 95.5
        assertTrue(result.getWeightedScore() > 90);
    }

    @Test
    void fatalFailure_blocksPass() {
        NovelAgentState.AuditResult audit = NovelAgentState.AuditResult.builder()
                .oocPassed(false).oocScore(30)  // FATAL fail
                .logicPassed(true)
                .settingPassed(true)
                .cluePassed(true)
                .rhythmPassed(true)
                .qualityPassed(true).qualityScore(90.0)
                .clueLandingPassed(true)
                .build();

        GradedAuditCalculator.GradedResult result = calculator.calculate(audit);

        assertFalse(result.isAllPassed());
        assertTrue(result.getFatalFailures().contains("ooc"));
    }

    @Test
    void warningOnly_doesNotBlockIfScoreAboveThreshold() {
        NovelAgentState.AuditResult audit = NovelAgentState.AuditResult.builder()
                .oocPassed(true).oocScore(80)
                .logicPassed(true)
                .settingPassed(true)
                .cluePassed(false)  // WARNING fail
                .rhythmPassed(true)
                .qualityPassed(true).qualityScore(80.0)
                .clueLandingPassed(true)
                .build();

        GradedAuditCalculator.GradedResult result = calculator.calculate(audit);

        // FATAL all passed, weighted = 0.20*80 + 0.20*100 + 0.15*100 + 0.15*0 + 0.10*100 + 0.10*80 + 0.10*100 = 79
        assertTrue(result.isAllPassed());
        assertTrue(result.getFatalFailures().isEmpty());
        assertTrue(result.getWarnings().contains("clue"));
    }

    @Test
    void multipleWarnings_canDropBelowThreshold() {
        NovelAgentState.AuditResult audit = NovelAgentState.AuditResult.builder()
                .oocPassed(true).oocScore(80)
                .logicPassed(true)
                .settingPassed(true)
                .cluePassed(false)   // WARNING fail
                .rhythmPassed(false) // WARNING fail
                .qualityPassed(false).qualityScore(40.0)  // WARNING fail
                .clueLandingPassed(true)
                .build();

        GradedAuditCalculator.GradedResult result = calculator.calculate(audit);

        // weighted = 0.20*80 + 0.20*100 + 0.15*100 + 0.15*0 + 0.10*0 + 0.10*40 + 0.10*100 = 55
        assertFalse(result.isAllPassed()); // 55 < 60
        assertEquals(3, result.getWarnings().size());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd D:/dev/moyun/backend && mvn test -pl . -Dtest=GradedAuditCalculatorTest -q`
Expected: FAIL — classes not found

- [ ] **Step 3: 实现 ValidationConfig**

```java
package com.novel.system.ai.graph;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "novel.validation")
public class ValidationConfig {

    private boolean useGradedAudit = false;
    private double passThreshold = 60.0;

    private Map<String, DimensionConfig> dimensions = Map.of(
            "ooc", new DimensionConfig(0.20, Severity.FATAL),
            "logic", new DimensionConfig(0.20, Severity.FATAL),
            "setting", new DimensionConfig(0.15, Severity.FATAL),
            "clue", new DimensionConfig(0.15, Severity.WARNING),
            "rhythm", new DimensionConfig(0.10, Severity.WARNING),
            "quality", new DimensionConfig(0.10, Severity.WARNING),
            "clueLanding", new DimensionConfig(0.10, Severity.WARNING)
    );

    @Data
    @AllArgsConstructor
    public static class DimensionConfig {
        private double weight;
        private Severity severity;
    }

    public enum Severity { FATAL, WARNING, SKIP }
}
```

- [ ] **Step 4: 实现 GradedAuditCalculator**

```java
package com.novel.system.ai.graph;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class GradedAuditCalculator {

    private final ValidationConfig config;

    public GradedResult calculate(NovelAgentState.AuditResult rawAudit) {
        List<NovelAgentState.AuditResult.DimensionResult> dimensions = new ArrayList<>();
        List<String> fatalFailures = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        dimensions.add(evalDimension("ooc", rawAudit.isOocPassed(),
                rawAudit.getOocScore(), rawAudit.getOocDetail()));
        dimensions.add(evalDimension("logic", rawAudit.isLogicPassed(),
                rawAudit.isLogicPassed() ? 100 : 0, rawAudit.getLogicDetail()));
        dimensions.add(evalDimension("setting", rawAudit.isSettingPassed(),
                rawAudit.isSettingPassed() ? 100 : 0, rawAudit.getSettingDetail()));
        dimensions.add(evalDimension("clue", rawAudit.isCluePassed(),
                rawAudit.isCluePassed() ? 100 : 0, rawAudit.getClueDetail()));
        dimensions.add(evalDimension("rhythm", rawAudit.isRhythmPassed(),
                rawAudit.isRhythmPassed() ? 100 : 0, rawAudit.getRhythmDetail()));
        dimensions.add(evalDimension("quality", rawAudit.isQualityPassed(),
                rawAudit.getQualityScore(), rawAudit.getQualityAssessment()));
        dimensions.add(evalDimension("clueLanding", rawAudit.isClueLandingPassed(),
                rawAudit.isClueLandingPassed() ? 100 : 0,
                formatClueLandingIssues(rawAudit.getClueLandingIssues())));

        double weightedScore = 0;
        for (NovelAgentState.AuditResult.DimensionResult d : dimensions) {
            weightedScore += d.getScore() * d.getWeight();
            if (!d.isPassed()) {
                if ("FATAL".equals(d.getSeverity())) {
                    fatalFailures.add(d.getDimension());
                } else if ("WARNING".equals(d.getSeverity())) {
                    warnings.add(d.getDimension());
                }
            }
        }

        boolean allPassed = fatalFailures.isEmpty()
                && weightedScore >= config.getPassThreshold();

        return new GradedResult(weightedScore, dimensions, fatalFailures, warnings, allPassed);
    }

    private NovelAgentState.AuditResult.DimensionResult evalDimension(
            String name, boolean passed, double score, String detail) {
        ValidationConfig.DimensionConfig dc = config.getDimensions().get(name);
        double weight = dc != null ? dc.getWeight() : 0;
        String severity = dc != null ? dc.getSeverity().name() : "SKIP";
        return NovelAgentState.AuditResult.DimensionResult.builder()
                .dimension(name).passed(passed).score(score)
                .weight(weight).severity(severity).detail(detail)
                .build();
    }

    private String formatClueLandingIssues(List<String> issues) {
        if (issues == null || issues.isEmpty()) return "无";
        return String.join("；", issues);
    }

    @Data
    @AllArgsConstructor
    public static class GradedResult {
        private double weightedScore;
        private List<NovelAgentState.AuditResult.DimensionResult> dimensions;
        private List<String> fatalFailures;
        private List<String> warnings;
        private boolean allPassed;
    }
}
```

- [ ] **Step 5: 运行测试确认通过**

Run: `cd D:/dev/moyun/backend && mvn test -pl . -Dtest=GradedAuditCalculatorTest -q`
Expected: 4 tests PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/novel/system/ai/graph/ValidationConfig.java \
        backend/src/main/java/com/novel/system/ai/graph/GradedAuditCalculator.java \
        backend/src/test/java/com/novel/system/ai/graph/GradedAuditCalculatorTest.java
git commit -m "feat(graph): add ValidationConfig and GradedAuditCalculator for weighted scoring"
```

---

### Task 8: QualityScoringNode — 删除 allPassed 计算

**Files:**
- Modify: `backend/src/main/java/com/novel/system/ai/graph/QualityScoringNode.java`

- [ ] **Step 1: 删除手动 allPassed 计算，保留原始分数收集**

将 QualityScoringNode.java 第 67-83 行：

```java
boolean allPassed = audit.isOocPassed()
        && audit.isLogicPassed()
        && audit.isCluePassed()
        && audit.isSettingPassed()
        && audit.isRhythmPassed()
        && quality.qualityPassed
        && quality.clueLandingPassed;

NovelAgentState.AuditResult updatedAudit = audit.toBuilder()
        .qualityScore(quality.totalScore)
        .qualityPassed(quality.qualityPassed)
        .qualityAssessment(quality.assessment)
        .clueLandingPassed(quality.clueLandingPassed)
        .clueLandingIssues(quality.clueLandingIssues)
        .suggestions(mergeSuggestions(audit.getSuggestions(), quality.suggestions))
        .allPassed(allPassed)
        .build();
```

替换为：

```java
// allPassed 由 AuditRouterNode 的 GradedAuditCalculator 决定（当 useGradedAudit=true）
// 此处保留向后兼容的老逻辑
boolean allPassed = audit.isOocPassed()
        && audit.isLogicPassed()
        && audit.isCluePassed()
        && audit.isSettingPassed()
        && audit.isRhythmPassed()
        && quality.qualityPassed
        && quality.clueLandingPassed;

NovelAgentState.AuditResult updatedAudit = audit.toBuilder()
        .qualityScore(quality.totalScore)
        .qualityPassed(quality.qualityPassed)
        .qualityAssessment(quality.assessment)
        .clueLandingPassed(quality.clueLandingPassed)
        .clueLandingIssues(quality.clueLandingIssues)
        .suggestions(mergeSuggestions(audit.getSuggestions(), quality.suggestions))
        .allPassed(allPassed)  // 保留，AuditRouterNode 可能会覆盖
        .build();
```

注意：此处保留老逻辑是为了向后兼容（useGradedAudit=false 时）。AuditRouterNode 在 useGradedAudit=true 时会用 GradedAuditCalculator 的结果覆盖 allPassed。

- [ ] **Step 2: 编译验证**

Run: `cd D:/dev/moyun/backend && mvn compile -pl . -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/novel/system/ai/graph/QualityScoringNode.java
git commit -m "refactor(graph): QualityScoringNode keeps allPassed for backward compat, AuditRouterNode overrides"
```

---

### Task 9: AuditRouterNode — 双路径路由（gradedRoute + legacyRoute）

**Files:**
- Modify: `backend/src/main/java/com/novel/system/ai/graph/AuditRouterNode.java`

- [ ] **Step 1: 注入依赖，拆分路由逻辑**

将 AuditRouterNode 替换为：

```java
package com.novel.system.ai.graph;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditRouterNode implements NovelGraphNode {

    private static final int MAX_REWRITES = 2;
    private static final int MAX_PLAN_RETRIES = 3;
    private static final double MIN_QUALITY_SCORE = 60.0;

    private final ValidationConfig validationConfig;
    private final GradedAuditCalculator gradedAuditCalculator;

    @Override
    public String nodeName() {
        return "audit_router";
    }

    @Override
    public ErrorStrategy errorStrategy() { return ErrorStrategy.FAIL; }

    @Override
    public NovelAgentState apply(NovelAgentState state) throws Exception {
        NovelAgentState.AuditResult audit = state.getAuditResult();

        if (audit == null) {
            return pass(state);
        }

        if (validationConfig.isUseGradedAudit()) {
            return gradedRoute(state, audit);
        } else {
            return legacyRoute(state, audit);
        }
    }

    private NovelAgentState gradedRoute(NovelAgentState state, NovelAgentState.AuditResult audit) {
        GradedAuditCalculator.GradedResult graded = gradedAuditCalculator.calculate(audit);

        NovelAgentState.AuditResult updatedAudit = audit.toBuilder()
                .weightedScore(graded.getWeightedScore())
                .dimensionResults(graded.getDimensions())
                .fatalFailures(graded.getFatalFailures())
                .warnings(graded.getWarnings())
                .allPassed(graded.isAllPassed())
                .build();
        state = state.toBuilder().auditResult(updatedAudit).build();

        if (!graded.getFatalFailures().isEmpty()) {
            if (state.getPlanRetryCount() < MAX_PLAN_RETRIES) {
                log.info("[audit_router] FATAL 失败: {}，决策: REPLAN", graded.getFatalFailures());
                return replan(state);
            }
            log.warn("[audit_router] FATAL 失败且重规划次数达上限，决策: MANUAL_REVIEW");
            return manualReview(state);
        }

        if (!graded.isAllPassed() && state.getRewriteCount() < MAX_REWRITES) {
            log.info("[audit_router] 加权分 {} < {}，决策: REWRITE",
                    graded.getWeightedScore(), validationConfig.getPassThreshold());
            return rewrite(state);
        }

        log.info("[audit_router] 通过（加权分={}），决策: PASS", graded.getWeightedScore());
        return pass(state);
    }

    private NovelAgentState legacyRoute(NovelAgentState state, NovelAgentState.AuditResult audit) {
        if (state.getRewriteCount() >= MAX_REWRITES && !audit.isAllPassed()) {
            log.warn("[audit_router] 重写次数达上限({})，标记MANUAL_REVIEW", state.getRewriteCount());
            return manualReview(state);
        }

        if (state.getPreWriteValidation() != null
                && !state.getPreWriteValidation().isPassed()
                && state.getPlanRetryCount() < MAX_PLAN_RETRIES) {
            log.info("[audit_router] 写前校验未通过，决策: REPLAN");
            return replan(state);
        }

        if (!audit.isAllPassed()) {
            if (!audit.isOocPassed() || !audit.isSettingPassed()) {
                if (state.getPlanRetryCount() < MAX_PLAN_RETRIES) {
                    log.info("[audit_router] OOC/设定校验未通过，决策: REPLAN");
                    return replan(state);
                }
            }

            if (state.getRewriteCount() < MAX_REWRITES) {
                log.info("[audit_router] 审校未通过(质量{})，决策: REWRITE", audit.getQualityScore());
                return rewrite(state);
            }
        }

        if (audit.getQualityScore() < MIN_QUALITY_SCORE && state.getRewriteCount() < MAX_REWRITES) {
            log.info("[audit_router] 质量分过低({})，决策: REWRITE", audit.getQualityScore());
            return rewrite(state);
        }

        log.info("[audit_router] 全部通过，决策: PASS");
        return pass(state);
    }

    private NovelAgentState pass(NovelAgentState state) {
        return state.toBuilder()
                .auditDecision("PASS")
                .currentPhase(NovelAgentState.GenerationPhase.AUDIT_ROUTER.name())
                .build();
    }

    private NovelAgentState replan(NovelAgentState state) {
        return state.toBuilder()
                .auditDecision("REPLAN")
                .planRetryCount(state.getPlanRetryCount() + 1)
                .iterationCount(state.getIterationCount() + 1)
                .currentPhase(NovelAgentState.GenerationPhase.AUDIT_ROUTER.name())
                .build();
    }

    private NovelAgentState rewrite(NovelAgentState state) {
        return state.toBuilder()
                .auditDecision("REWRITE")
                .rewriteCount(state.getRewriteCount() + 1)
                .iterationCount(state.getIterationCount() + 1)
                .currentPhase(NovelAgentState.GenerationPhase.AUDIT_ROUTER.name())
                .build();
    }

    private NovelAgentState manualReview(NovelAgentState state) {
        return state.toBuilder()
                .auditDecision("MANUAL_REVIEW")
                .currentPhase(NovelAgentState.GenerationPhase.AUDIT_ROUTER.name())
                .build();
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `cd D:/dev/moyun/backend && mvn compile -pl . -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/novel/system/ai/graph/AuditRouterNode.java
git commit -m "refactor(graph): AuditRouterNode dual-path routing with GradedAuditCalculator"
```

---

## 第三批：AuthorAgent 预筛（依赖 FATAL/WARNING 分级）

### Task 10: AuthorAgentConfig — 新增预筛配置

**Files:**
- Modify: `backend/src/main/java/com/novel/system/ai/graph/AuthorAgentConfig.java`

- [ ] **Step 1: 新增配置字段**

```java
// AuthorAgentConfig.java — 在现有字段之后添加

private boolean preFilterEnabled = true;
private int wordCountMin = 1000;
private int wordCountMax = 8000;
private double qualityScoreAutoApprove = 90.0;
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/com/novel/system/ai/graph/AuthorAgentConfig.java
git commit -m "feat(graph): AuthorAgentConfig add preFilterEnabled and word count thresholds"
```

---

### Task 11: AuthorAgentImpl — 预筛逻辑

**Files:**
- Modify: `backend/src/main/java/com/novel/system/ai/graph/AuthorAgentImpl.java`

- [ ] **Step 1: 在 beforeNode 方法中插入预筛**

将 `beforeNode` 方法（第 28-61 行）替换为：

```java
@Override
public AuthorDirective beforeNode(String nodeName, NovelAgentState state) {
    if (!authorConfig.isEnabled() || !authorConfig.getCheckpoints().contains(nodeName)) {
        return AuthorDirective.builder()
                .action(AuthorDirective.Action.APPROVE)
                .build();
    }

    if (!LLM_CHECKPOINTS.contains(nodeName)) {
        return AuthorDirective.builder()
                .action(AuthorDirective.Action.APPROVE)
                .build();
    }

    // 预筛：命中则跳过 LLM 调用
    if (authorConfig.isPreFilterEnabled()) {
        AuthorDirective preFilterResult = switch (nodeName) {
            case "outline" -> preFilterOutline(state);
            case "writing" -> preFilterWriting(state);
            case "audit_router" -> preFilterAudit(state);
            default -> null;
        };
        if (preFilterResult != null) {
            log.info("[AuthorAgent] 预筛通过({}): {}", nodeName, preFilterResult.getRationale());
            return preFilterResult;
        }
    }

    log.info("[AuthorAgent] beforeNode 检查点: {}", nodeName);

    try {
        String prompt = buildBeforePrompt(nodeName, state);
        String response = chatModelProvider.getStructuredModel(state.getLlmConfigId()).chat(prompt);
        AuthorDirective directive = parseDirective(response);
        directive.setTargetNode(nodeName);

        if (directive.shouldIntervene()) {
            log.info("[AuthorAgent] 介入: action={}, rationale={}",
                    directive.getAction(), directive.getRationale());
        }

        return directive;
    } catch (Exception e) {
        log.error("[AuthorAgent] beforeNode 失败: {} - {}", nodeName, e.getMessage());
        return AuthorDirective.builder()
                .action(AuthorDirective.Action.APPROVE)
                .build();
    }
}
```

- [ ] **Step 2: 添加预筛私有方法**

在 `parseDirective` 方法之后添加：

```java
private AuthorDirective preFilterOutline(NovelAgentState state) {
    NovelAgentState.ChapterPlan plan = state.getChapterPlan();
    if (plan == null) return null;

    boolean structurallyComplete =
            isNotBlank(plan.getTitle()) &&
            isNotBlank(plan.getSummary()) &&
            plan.getKeyPoints() != null && !plan.getKeyPoints().isEmpty() &&
            isNotBlank(plan.getHook());

    if (!structurallyComplete) return null;
    if (isNotBlank(state.getUserIntent())) return null;
    if (state.getAuthorDirective() != null
            && isNotBlank(state.getAuthorDirective().getNarrativeDirection())) return null;

    return AuthorDirective.builder()
            .action(AuthorDirective.Action.APPROVE)
            .rationale("章纲结构完整，无创作意图/叙事方向需审查")
            .build();
}

private AuthorDirective preFilterWriting(NovelAgentState state) {
    NovelAgentState.DraftContent draft = state.getDraftContent();
    if (draft == null) return null;

    if (draft.getSeverity() != NovelAgentState.Severity.LOW) return null;
    if (draft.getRevisionNotes() != null && !draft.getRevisionNotes().isEmpty()) return null;
    if (draft.getWordCount() < authorConfig.getWordCountMin()
            || draft.getWordCount() > authorConfig.getWordCountMax()) return null;
    if (isNotBlank(state.getUserIntent())) return null;
    if (state.getAuthorDirective() != null
            && isNotBlank(state.getAuthorDirective().getNarrativeDirection())) return null;

    return AuthorDirective.builder()
            .action(AuthorDirective.Action.APPROVE)
            .rationale("正文自审LOW，字数正常，无修订意见")
            .build();
}

private AuthorDirective preFilterAudit(NovelAgentState state) {
    NovelAgentState.AuditResult audit = state.getAuditResult();
    if (audit == null) return null;

    if (audit.isAllPassed() && audit.getQualityScore() >= authorConfig.getQualityScoreAutoApprove()) {
        return AuthorDirective.builder()
                .action(AuthorDirective.Action.APPROVE)
                .rationale(String.format("全部通过且质量分 %.1f >= %.0f",
                        audit.getQualityScore(), authorConfig.getQualityScoreAutoApprove()))
                .build();
    }

    return null;
}

private boolean isNotBlank(String s) {
    return s != null && !s.isBlank();
}
```

- [ ] **Step 3: 编译验证**

Run: `cd D:/dev/moyun/backend && mvn compile -pl . -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/novel/system/ai/graph/AuthorAgentImpl.java
git commit -m "feat(graph): AuthorAgent pre-filter to skip LLM calls for clearly passing content"
```

---

## 第四批：NodeExecutor + 持久化（依赖前三批稳定）

### Task 12: StateSnapshot + StateCheckpointService

**Files:**
- Create: `backend/src/main/java/com/novel/system/ai/graph/StateSnapshot.java`
- Create: `backend/src/main/java/com/novel/system/ai/graph/StateCheckpointService.java`
- Create: `backend/src/test/java/com/novel/system/ai/graph/StateCheckpointServiceTest.java`

- [ ] **Step 1: 实现 StateSnapshot**

```java
package com.novel.system.ai.graph;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StateSnapshot {
    private String nodeName;
    private String stateJson;
    private Instant savedAt;
    private String novelId;
    private int chapterNum;
}
```

- [ ] **Step 2: 实现 StateCheckpointService**

```java
package com.novel.system.ai.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "novel.checkpoint", name = "enabled", havingValue = "true")
public class StateCheckpointService {

    private static final String KEY_PREFIX = "novel:checkpoint:";
    private static final Duration TTL = Duration.ofHours(1);
    private static final List<String> CHECKPOINT_ORDER = List.of(
            "outline", "writing", "audit_router");

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public StateCheckpointService(RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void saveCheckpoint(String nodeName, NovelAgentState state) {
        try {
            String key = buildKey(state.getNovelId(), state.getChapterNum(), nodeName);
            StateSnapshot snapshot = new StateSnapshot(
                    nodeName,
                    objectMapper.writeValueAsString(state),
                    Instant.now(),
                    state.getNovelId(),
                    state.getChapterNum());
            String json = objectMapper.writeValueAsString(snapshot);
            redisTemplate.opsForValue().set(key, json, TTL);
            log.debug("[Checkpoint] 保存检查点: {}", key);
        } catch (Exception e) {
            log.warn("[Checkpoint] 保存检查点失败: {} - {}", nodeName, e.getMessage());
        }
    }

    public Optional<StateSnapshot> loadLatestCheckpoint(String novelId, int chapterNum) {
        try {
            String pattern = buildKey(novelId, chapterNum, "*");
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys == null || keys.isEmpty()) {
                return Optional.empty();
            }

            // 按管线顺序找最新的检查点
            String latestKey = null;
            int latestIndex = -1;
            for (String key : keys) {
                String nodeName = extractNodeName(key);
                int index = CHECKPOINT_ORDER.indexOf(nodeName);
                if (index > latestIndex) {
                    latestIndex = index;
                    latestKey = key;
                }
            }

            if (latestKey == null) return Optional.empty();

            String json = redisTemplate.opsForValue().get(latestKey);
            if (json == null) return Optional.empty();

            return Optional.of(objectMapper.readValue(json, StateSnapshot.class));
        } catch (Exception e) {
            log.warn("[Checkpoint] 加载检查点失败: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public void clearCheckpoints(String novelId, int chapterNum) {
        try {
            String pattern = buildKey(novelId, chapterNum, "*");
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.debug("[Checkpoint] 清理 {} 个检查点", keys.size());
            }
        } catch (Exception e) {
            log.warn("[Checkpoint] 清理检查点失败: {}", e.getMessage());
        }
    }

    private String buildKey(String novelId, int chapterNum, String nodeName) {
        return KEY_PREFIX + novelId + ":" + chapterNum + ":" + nodeName;
    }

    private String extractNodeName(String key) {
        String[] parts = key.split(":");
        return parts.length >= 4 ? parts[3] : "";
    }
}
```

- [ ] **Step 3: 编译验证**

Run: `cd D:/dev/moyun/backend && mvn compile -pl . -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/novel/system/ai/graph/StateSnapshot.java \
        backend/src/main/java/com/novel/system/ai/graph/StateCheckpointService.java
git commit -m "feat(graph): add StateCheckpointService for Redis-based state persistence"
```

---

### Task 13: NodeExecutor — 提取执行逻辑

**Files:**
- Create: `backend/src/main/java/com/novel/system/ai/graph/NodeExecutor.java`
- Create: `backend/src/test/java/com/novel/system/ai/graph/NodeExecutorTest.java`

- [ ] **Step 1: 写 NodeExecutor 单元测试**

```java
package com.novel.system.ai.graph;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NodeExecutorTest {

    @Mock private AuthorAgent authorAgent;
    @Mock private StateCheckpointService checkpointService;
    @Mock private GraphDefinition definition;
    @Mock private NovelGraphNode testNode;

    private NodeExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new NodeExecutor(authorAgent, checkpointService, definition);
        when(testNode.nodeName()).thenReturn("test_node");
    }

    @Test
    void executesNodeAndReturnsResult() throws Exception {
        NovelAgentState state = NovelAgentState.builder().build();
        NovelAgentState expected = state.toBuilder().iterationCount(1).build();
        when(testNode.apply(any())).thenReturn(expected);
        when(definition.isCheckpoint("test_node")).thenReturn(false);

        NovelAgentState result = executor.execute(testNode, state, Mode.FULL);

        assertEquals(1, result.getIterationCount());
        verify(testNode).apply(any());
    }

    @Test
    void savesCheckpointForCheckpointNodes() throws Exception {
        NovelAgentState state = NovelAgentState.builder().build();
        when(testNode.apply(any())).thenReturn(state);
        when(testNode.nodeName()).thenReturn("outline");
        when(definition.isCheckpoint("outline")).thenReturn(true);
        when(authorAgent.beforeNode(any(), any())).thenReturn(
                AuthorDirective.builder().action(AuthorDirective.Action.APPROVE).build());

        executor.execute(testNode, state, Mode.FULL);

        verify(checkpointService).saveCheckpoint("outline", state);
    }

    @Test
    void skipsNodeWhenShouldSkipReturnsTrue() throws Exception {
        NovelAgentState state = NovelAgentState.builder().build();
        when(testNode.shouldSkip(any())).thenReturn(true);
        when(definition.isCheckpoint("test_node")).thenReturn(false);

        NovelAgentState result = executor.execute(testNode, state, Mode.FULL);

        verify(testNode, never()).apply(any());
        assertFalse(result.getExecutionTrace().isEmpty());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd D:/dev/moyun/backend && mvn test -pl . -Dtest=NodeExecutorTest -q`
Expected: FAIL — NodeExecutor class not found

- [ ] **Step 3: 实现 NodeExecutor**

```java
package com.novel.system.ai.graph;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Map;

@Slf4j
public class NodeExecutor {

    private final AuthorAgent authorAgent;
    private final StateCheckpointService checkpointService;
    private final GraphDefinition definition;

    public NodeExecutor(AuthorAgent authorAgent, StateCheckpointService checkpointService,
                        GraphDefinition definition) {
        this.authorAgent = authorAgent;
        this.checkpointService = checkpointService;
        this.definition = definition;
    }

    public NovelAgentState execute(NovelGraphNode node, NovelAgentState state, Mode mode) {
        String nodeName = node.nodeName();

        if (node.shouldSkip(state)) {
            Instant now = Instant.now();
            return StateHelper.appendTrace(state, NodeExecutionRecord.skipped(
                    nodeName, mode, now, now));
        }

        AuthorDirective directive = null;
        if (mode == Mode.FULL && authorAgent != null) {
            directive = authorAgent.beforeNode(nodeName, state);
            if (directive != null && directive.shouldIntervene()) {
                if (directive.getAction() == AuthorDirective.Action.REDIRECT) {
                    return handleRedirect(node, state, directive, nodeName);
                }
                if (directive.getAction() == AuthorDirective.Action.REJECT) {
                    return handleReject(node, state, directive, nodeName);
                }
            }
        }
        state = state.toBuilder().authorDirective(directive).build();

        NovelAgentState result = executeWithRetry(node, state, mode);

        if (authorAgent != null && directive != null) {
            NovelAgentState after = authorAgent.afterNode(nodeName, result, directive);
            if (after != null) result = after;
        }

        if (checkpointService != null && definition.isCheckpoint(nodeName)) {
            checkpointService.saveCheckpoint(nodeName, result);
        }

        return result;
    }

    private NovelAgentState executeWithRetry(NovelGraphNode node, NovelAgentState state, Mode mode) {
        ErrorStrategy strategy = node.errorStrategy();
        int maxRetries = strategy == ErrorStrategy.RETRY ? node.maxRetries() : 0;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                if (attempt > 0) {
                    log.info("[NodeExecutor] Retrying node {} (attempt {}/{})",
                            node.nodeName(), attempt, maxRetries);
                }
                NovelAgentState result = node.apply(state);
                result = StateHelper.incrementNodeRetry(result, node.nodeName());
                return result;
            } catch (Exception e) {
                log.warn("[NodeExecutor] Node {} failed on attempt {}: {}",
                        node.nodeName(), attempt + 1, e.getMessage());

                if (strategy == ErrorStrategy.FAIL) {
                    return StateHelper.appendError(state, new StructuredError(
                            node.nodeName(), strategy, e.getMessage(), e, Instant.now(), false));
                }

                if (strategy == ErrorStrategy.SKIP || attempt == maxRetries) {
                    return StateHelper.appendError(state, new StructuredError(
                            node.nodeName(), strategy, e.getMessage(), e, Instant.now(), true));
                }
            }
        }
        return state;
    }

    private NovelAgentState handleRedirect(NovelGraphNode node, NovelAgentState state,
                                            AuthorDirective directive, String nodeName) {
        String target = directive.getTargetNode();
        if (target != null) {
            log.info("[NodeExecutor] AuthorAgent redirect: {} -> {}", nodeName, target);
            Instant now = Instant.now();
            state = StateHelper.appendTrace(state, NodeExecutionRecord.success(
                    nodeName, state.getMode(), now, now, directive,
                    Map.of("action", "author_redirect", "target", target)));
        }
        return state;
    }

    private NovelAgentState handleReject(NovelGraphNode node, NovelAgentState state,
                                          AuthorDirective directive, String nodeName) {
        log.info("[NodeExecutor] AuthorAgent rejected: {}", nodeName);
        Instant now = Instant.now();
        state = StateHelper.appendTrace(state, NodeExecutionRecord.success(
                nodeName, state.getMode(), now, now, directive,
                Map.of("action", "author_rejected")));
        return state;
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd D:/dev/moyun/backend && mvn test -pl . -Dtest=NodeExecutorTest -q`
Expected: 3 tests PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/novel/system/ai/graph/NodeExecutor.java \
        backend/src/test/java/com/novel/system/ai/graph/NodeExecutorTest.java
git commit -m "feat(graph): add NodeExecutor — sole AuthorAgent intercept point with checkpoint support"
```

---

### Task 14: GraphRunner — 委托 NodeExecutor，删除旧逻辑

**Files:**
- Modify: `backend/src/main/java/com/novel/system/ai/graph/GraphRunner.java`
- Modify: `backend/src/test/java/com/novel/system/ai/graph/GraphRunnerV2Test.java`

- [ ] **Step 1: GraphRunner 新增 NodeExecutor 字段和构造方式**

将 GraphRunner 的构造函数和字段替换为：

```java
@Slf4j
public class GraphRunner {

    private final GraphDefinition definition;
    private final java.util.Map<String, NovelGraphNode> nodes = new ConcurrentHashMap<>();
    private final ExecutorService parallelExecutor = Executors.newCachedThreadPool();
    private NodeExecutor nodeExecutor;
    private StateCheckpointService checkpointService;

    private static final int MAX_ITERATIONS = 20;

    public GraphRunner(GraphDefinition definition) {
        this.definition = definition;
    }

    public void setNodeExecutor(NodeExecutor nodeExecutor) {
        this.nodeExecutor = nodeExecutor;
    }

    public void setCheckpointService(StateCheckpointService checkpointService) {
        this.checkpointService = checkpointService;
    }

    // 保留 setAuthorAgent 用于测试兼容，但实际 AuthorAgent 由 NodeExecutor 管理
    public void setAuthorAgent(AuthorAgent authorAgent) {
        // 创建一个简单的 NodeExecutor 用于向后兼容
        this.nodeExecutor = new NodeExecutor(authorAgent, null, definition);
    }

    public void register(NovelGraphNode node) {
        nodes.put(node.nodeName(), node);
    }
    // ... 其余不变
}
```

- [ ] **Step 2: 重写 execute 主循环**

将 `execute` 方法（第 35-140 行）替换为：

```java
public NovelAgentState execute(NovelAgentState initialState) {
    return execute(initialState, Mode.FULL);
}

public NovelAgentState execute(NovelAgentState initialState, Mode mode) {
    NovelAgentState state = initialState.toBuilder().mode(mode).build();

    // 断点恢复检查
    if (mode == Mode.FULL && checkpointService != null) {
        Optional<StateSnapshot> checkpoint = checkpointService
                .loadLatestCheckpoint(state.getNovelId(), state.getChapterNum());
        if (checkpoint.isPresent()) {
            log.info("[GraphRunner] 从检查点恢复: {}", checkpoint.get().getNodeName());
            try {
                NovelAgentState restored = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(checkpoint.get().getStateJson(), NovelAgentState.class);
                String resumeNode = getNextNode(checkpoint.get().getNodeName(), restored, mode);
                return executeFrom(resumeNode, restored, mode);
            } catch (Exception e) {
                log.warn("[GraphRunner] 检查点恢复失败，从头执行: {}", e.getMessage());
            }
        }
    }

    return executeFrom("arc_check", state, mode);
}

private NovelAgentState executeFrom(String startNode, NovelAgentState state, Mode mode) {
    String currentNode = startNode;
    int iterations = 0;

    while (currentNode != null && !currentNode.equals(GraphDefinition.END)) {
        if (++iterations > MAX_ITERATIONS) {
            log.error("[GraphRunner] Max iterations ({}) exceeded, terminating", MAX_ITERATIONS);
            state = StateHelper.appendError(state, new StructuredError(
                    currentNode, ErrorStrategy.FAIL, "Max iterations exceeded",
                    null, Instant.now(), false));
            break;
        }

        NovelGraphNode node = nodes.get(currentNode);
        if (node == null) {
            log.error("[GraphRunner] Node not found: {}", currentNode);
            state = StateHelper.appendError(state, new StructuredError(
                    currentNode, ErrorStrategy.FAIL, "Node not registered",
                    null, Instant.now(), false));
            break;
        }

        log.info("[GraphRunner] Executing node: {} (iter={})", currentNode, iterations);

        if (nodeExecutor != null) {
            state = nodeExecutor.execute(node, state, mode);
        } else {
            // Fallback: 直接执行（无 AuthorAgent、无检查点）
            try {
                state = node.apply(state);
            } catch (Exception e) {
                state = StateHelper.appendError(state, new StructuredError(
                        currentNode, ErrorStrategy.SKIP, e.getMessage(),
                        e, Instant.now(), true));
            }
        }

        Instant now = Instant.now();
        state = StateHelper.appendTrace(state, NodeExecutionRecord.success(
                currentNode, state.getMode(), now, now,
                state.getAuthorDirective(), Map.of()));

        if (definition.isFanOut(currentNode)) {
            state = executeFanOut(currentNode, state, mode);
            currentNode = definition.getFanInTarget(
                    definition.getFanOutTargets(currentNode, mode), mode);
            if (currentNode == null) {
                currentNode = GraphDefinition.END;
            }
            continue;
        }

        String nextNode = getNextNode(currentNode, state, mode);
        if (nextNode != null && !nextNode.equals(GraphDefinition.END)) {
            NovelGraphNode nextGraphNode = nodes.get(nextNode);
            if (nextGraphNode != null) {
                state = nextGraphNode.resetState(state, currentNode);
            }
        }
        currentNode = nextNode;
    }

    // 章节生成完成后清理检查点
    if (checkpointService != null && state.getNovelId() != null) {
        checkpointService.clearCheckpoints(state.getNovelId(), state.getChapterNum());
    }

    return state;
}
```

- [ ] **Step 3: 删除旧方法**

删除以下方法：
- `executeNodeWithErrorStrategy`（第 280-325 行）— 已移入 NodeExecutor
- `resetStateForReroute`（如果还在的话）— 已在 Task 2 中替换

保留：
- `executeFanOut`（已改造为使用 MergeStrategy）
- `getNextNode`、`resolveWithRetryProtection`（路由逻辑不变）

- [ ] **Step 4: 更新 GraphRunnerV2Test 适配新构造方式**

```java
// GraphRunnerV2Test.java — 在 setUp 方法中，runner 创建后改为：

GraphRunner runner = new GraphRunner(definition);
runner.register(arcCheckNode);
runner.register(outlineNode);
runner.register(writingNode);
runner.register(summaryNode);
runner.register(postProcessNode);
// 使用 setAuthorAgent（内部创建 NodeExecutor）
runner.setAuthorAgent(authorAgent);
```

- [ ] **Step 5: 运行测试确认通过**

Run: `cd D:/dev/moyun/backend && mvn test -pl . -Dtest=GraphRunnerV2Test -q`
Expected: 5 tests PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/novel/system/ai/graph/GraphRunner.java \
        backend/src/test/java/com/novel/system/ai/graph/GraphRunnerV2Test.java
git commit -m "refactor(graph): GraphRunner delegates to NodeExecutor, remove AuthorAgent intercept code"
```

---

### Task 15: MultiAgentNovelService — 注入 NodeExecutor + StateCheckpointService

**Files:**
- Modify: `backend/src/main/java/com/novel/system/service/chapter/MultiAgentNovelService.java`

- [ ] **Step 1: 注入新依赖，配置 NodeExecutor**

将 MultiAgentNovelService 构造函数替换为：

```java
public MultiAgentNovelService(
        ArcOrchestrationNode arcCheck,
        OutlineNode outline,
        CluePlanningNode cluePlan,
        CharacterPlanningNode charPlan,
        WorldValidatorNode worldValidate,
        IdeaSelectionNode ideaSelect,
        WritingNode writing,
        SelfReviewNode selfReview,
        ValidationNode validation,
        QualityScoringNode qualityScoring,
        AuditRouterNode auditRouter,
        SummaryNode summary,
        PostProcessNode postProcess,
        AuthorAgent authorAgent,
        @org.springframework.beans.factory.annotation.Autowired(required = false) StateCheckpointService checkpointService) {

    GraphDefinition definition = GraphDefinition.builder()
            .edge("arc_check", "outline")
            .edge("outline", "clue_planning", Mode.FULL)
            .edge("outline", "writing", Mode.FAST)
            .fanOut("outline", List.of("clue_planning", "character_planning"), Mode.FULL, MergeStrategy.chapterPlanMerge())
            .fanIn(List.of("clue_planning", "character_planning"), "world_validate", Mode.FULL)
            .edge("world_validate", "idea_select", Mode.FULL)
            .edge("idea_select", "writing", Mode.FULL)
            .edge("writing", "self_review")
            .conditionalEdge("self_review", "writing",
                    s -> s.getDraftContent() != null
                            && s.getDraftContent().getSeverity() == NovelAgentState.Severity.HIGH
                            && s.getWriteRetryCount() < 2)
            .conditionalEdge("self_review", "summary", s -> s.getMode() == Mode.FAST)
            .conditionalEdge("self_review", "validation", s -> s.getMode() == Mode.FULL)
            .edge("validation", "quality_scoring", Mode.FULL)
            .edge("quality_scoring", "audit_router", Mode.FULL)
            .conditionalEdge("audit_router", "summary",
                    s -> "PASS".equals(s.getAuditDecision()), Mode.FULL)
            .conditionalEdge("audit_router", "outline",
                    s -> "REPLAN".equals(s.getAuditDecision()), Mode.FULL)
            .conditionalEdge("audit_router", "writing",
                    s -> "REWRITE".equals(s.getAuditDecision()), Mode.FULL)
            .edge("summary", "post_process")
            .checkpoint("outline")
            .checkpoint("writing")
            .checkpoint("audit_router")
            .retryPolicy("outline", 2, "summary")
            .retryPolicy("writing", 3, "summary")
            .retryPolicy("audit_router", 2, "summary")
            .build();

    this.runner = new GraphRunner(definition);
    runner.register(arcCheck);
    runner.register(outline);
    runner.register(cluePlan);
    runner.register(charPlan);
    runner.register(worldValidate);
    runner.register(ideaSelect);
    runner.register(writing);
    runner.register(selfReview);
    runner.register(validation);
    runner.register(qualityScoring);
    runner.register(auditRouter);
    runner.register(summary);
    runner.register(postProcess);

    // NodeExecutor 统一管理 AuthorAgent 拦截 + 检查点
    NodeExecutor nodeExecutor = new NodeExecutor(authorAgent, checkpointService, definition);
    runner.setNodeExecutor(nodeExecutor);
    runner.setCheckpointService(checkpointService);
}
```

- [ ] **Step 2: 编译验证**

Run: `cd D:/dev/moyun/backend && mvn compile -pl . -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: 运行全部图相关测试**

Run: `cd D:/dev/moyun/backend && mvn test -pl . -Dtest="GraphRunnerV2Test,MergeStrategyTest,GradedAuditCalculatorTest,NodeExecutorTest" -q`
Expected: ALL PASS

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/novel/system/service/chapter/MultiAgentNovelService.java
git commit -m "refactor(graph): MultiAgentNovelService wires NodeExecutor and chapterPlanMerge"
```

---

### Task 16: application.yml 配置

**Files:**
- Modify: `backend/src/main/resources/application.yml`（或 application.properties）

- [ ] **Step 1: 新增配置项**

```yaml
# 加权校验
novel:
  validation:
    use-graded-audit: false  # 默认走老逻辑，验证后切换为 true
    pass-threshold: 60.0
    dimensions:
      ooc:         { weight: 0.20, severity: FATAL }
      logic:       { weight: 0.20, severity: FATAL }
      setting:     { weight: 0.15, severity: FATAL }
      clue:        { weight: 0.15, severity: WARNING }
      rhythm:      { weight: 0.10, severity: WARNING }
      quality:     { weight: 0.10, severity: WARNING }
      clue-landing: { weight: 0.10, severity: WARNING }

# AuthorAgent 预筛
moyun:
  agent:
    author:
      pre-filter-enabled: true
      word-count-min: 1000
      word-count-max: 8000
      quality-score-auto-approve: 90.0

# 状态检查点
novel:
  checkpoint:
    enabled: true
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/resources/
git commit -m "config: add validation, author-agent pre-filter, and checkpoint configuration"
```

---

## 验证清单

全部任务完成后，运行完整测试套件：

```bash
cd D:/dev/moyun/backend && mvn test -pl . -q
```

手动验证：
1. FULL 模式生成一章，检查 executionTrace 是否包含所有节点（包括 fan-out 分支）
2. 检查 Redis 中是否有检查点 key（`novel:checkpoint:*`）
3. 杀掉进程后重新请求同一章，检查是否从检查点恢复
4. 切换 `use-graded-audit: true`，生成一章，检查 AuditResult 中是否有 weightedScore 和 dimensionResults
5. 检查 AuthorAgent 日志中是否有"预筛通过"记录
