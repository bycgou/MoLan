# GraphRunner 架构重构设计

日期：2026-05-26
状态：待审阅
范围：后端 GraphRunner 及周边组件

## 背景

当前 GraphRunner 是 355 行的上帝类，混合了调度、执行、合并、重置、重试、AuthorAgent 拦截等职责。代码审查发现 6 个架构问题：

1. 扇出合并逻辑脆弱 — 硬编码字符串匹配，executionTrace 丢失
2. AuthorAgent 每次检查点无条件调用 LLM — 无预筛，浪费 token
3. "Multi-Agent" 名不副实 — 实际是固定管线 + 条件路由
4. 状态重置逻辑硬编码 — switch 语句，违反开闭原则
5. 无中间状态持久化 — 崩溃即丢失全部 LLM 调用结果
6. 校验全通过才算通过 — 七维 `&&` 连接，缺乏灰度机制

本设计解决问题 1、2、4、5、6。问题 3 是命名问题，不在本次重构范围内（重命名影响面大，收益低）。

## 设计方案：方案 B — GraphRunner 拆分 + 校验层重构

### 整体架构

```
GraphRunner (调度层, ~150行)          NodeExecutor (执行层, ~100行)
  ├─ 主循环 + 条件路由                  ├─ beforeNode (AuthorAgent 预筛)
  ├─ fan-out 编排                       ├─ node.apply(state)
  └─ 调用 NodeExecutor                  ├─ Redis checkpoint
                                        └─ afterNode

NovelGraphNode 接口 (扩展)
  ├─ apply(state)                       — 不变
  ├─ nodeName()                         — 不变
  ├─ shouldSkip(state)                  — 不变
  ├─ errorStrategy()                    — 不变
  ├─ maxRetries()                       — 不变
  └─ resetState(state, source) [新增]   — 重置逻辑声明

GraphDefinition (扩展)
  └─ fanOut(source, targets, mode, MergeStrategy) [新增] — 扇出合并策略
```

---

## 第 1 节：NodeExecutor + 持久化

### NodeExecutor

从 GraphRunner 中提取 `executeNodeWithErrorStrategy` + AuthorAgent 拦截 + 持久化，成为独立类。

职责：
- AuthorAgent beforeNode 拦截（含预筛）
- 带重试策略的节点执行
- AuthorAgent afterNode
- Redis 检查点保存

```java
@Slf4j
public class NodeExecutor {

    private final AuthorAgent authorAgent;
    private final StateCheckpointService checkpointService;

    public NovelAgentState execute(NovelGraphNode node, NovelAgentState state, Mode mode) {
        if (node.shouldSkip(state)) {
            return appendSkipTrace(state, node.nodeName(), mode);
        }

        AuthorDirective directive = null;
        if (mode == Mode.FULL && authorAgent != null) {
            directive = authorAgent.beforeNode(node.nodeName(), state);
            if (shouldRedirectOrReject(directive)) {
                return handleDirective(node, state, directive);
            }
        }
        state = state.toBuilder().authorDirective(directive).build();

        NovelAgentState result = executeWithRetry(node, state);

        if (authorAgent != null && directive != null) {
            NovelAgentState after = authorAgent.afterNode(node.nodeName(), result, directive);
            if (after != null) result = after;
        }

        checkpointService.saveCheckpoint(node.nodeName(), result);
        return result;
    }

    private NovelAgentState executeWithRetry(NovelGraphNode node, NovelAgentState state) {
        // 从 GraphRunner.executeNodeWithErrorStrategy 提取，逻辑不变
    }
}
```

### StateCheckpointService — Redis 持久化

```java
@Service
public class StateCheckpointService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public void saveCheckpoint(String nodeName, NovelAgentState state) {
        String key = buildKey(state.getNovelId(), state.getChapterNum(), nodeName);
        String json = objectMapper.writeValueAsString(serialize(state));
        redisTemplate.opsForValue().set(key, json, Duration.ofHours(1));
    }

    public Optional<CheckpointData> loadCheckpoint(String novelId, int chapterNum) {
        // 扫描 key 前缀，找到最新的检查点
        // CheckpointData = { nodeName, stateJson, savedAt }
        // 用于断点恢复（本设计预留，后续实现）
    }
}
```

关键决策：
- `SseEmitter` 不序列化 — 崩溃后无法恢复，恢复时需重新建立 SSE
- TTL 1 小时 — 一章生成通常 5-15 分钟，1 小时覆盖异常
- Key 格式：`novel:checkpoint:{novelId}:{chapterNum}:{nodeName}`
- 只在 FULL 模式下持久化 — FAST 模式代价低，不需要断点恢复

---

## 第 2 节：MergeStrategy + fan-out 重构

### MergeStrategy 接口

```java
@FunctionalInterface
public interface MergeStrategy {
    NovelAgentState merge(NovelAgentState base, List<NovelAgentState> results);

    static MergeStrategy defaultStrategy() { ... }
    static MergeStrategy chapterPlanMerge() { ... }
}
```

### 默认合并策略

解决 executionTrace 丢失 bug — 累积所有分支的 trace、errors、llmCalls：

```java
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
```

### chapterPlanMerge — 当前 fan-out 专用

在 defaultStrategy 基础上，合并各分支的 chapterPlan 字段（clueActions + characterActions）：

```java
static MergeStrategy chapterPlanMerge() {
    return (base, results) -> {
        NovelAgentState merged = defaultStrategy().merge(base, results);

        NovelAgentState.ChapterPlan.ChapterPlanBuilder planBuilder =
                merged.getChapterPlan() != null
                        ? merged.getChapterPlan().toBuilder()
                        : NovelAgentState.ChapterPlan.builder();

        for (NovelAgentState result : results) {
            if (result.getChapterPlan() == null) continue;
            if (result.getChapterPlan().getClueActions() != null)
                planBuilder.clueActions(result.getChapterPlan().getClueActions());
            if (result.getChapterPlan().getCharacterActions() != null)
                planBuilder.characterActions(result.getChapterPlan().getCharacterActions());
        }

        return merged.toBuilder().chapterPlan(planBuilder.build()).build();
    };
}
```

### GraphDefinition 扩展

```java
public GraphDefinitionBuilder fanOut(String source, List<String> targets,
                                      Mode mode, MergeStrategy strategy) { ... }

// 不传 strategy 时使用默认合并策略
public GraphDefinitionBuilder fanOut(String source, List<String> targets, Mode mode) {
    return fanOut(source, targets, mode, MergeStrategy.defaultStrategy());
}
```

### GraphRunner.executeFanOut 改造

删除 `mergeFanOutResult` 方法，`executeFanOut` 改用 `strategy.merge(state, results)`。

---

## 第 3 节：StateReset + NovelGraphNode 接口扩展

### NovelGraphNode 接口新增

```java
/**
 * 当流程重新路由到该节点时，声明如何重置状态。
 * 默认不做任何重置。
 */
default NovelAgentState resetState(NovelAgentState state, String source) {
    return state;
}
```

### 各节点实现

**OutlineNode** — 从 GraphRunner `"outline"` case 迁移：

```java
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

**WritingNode** — 从 GraphRunner `"writing"` case 迁移：

```java
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

**其他节点** — 继承默认实现（不做重置）。

### GraphRunner 改造

删除 `resetStateForReroute` 私有方法，两处调用改为：

```java
NovelGraphNode targetGraphNode = nodes.get(target);
if (targetGraphNode != null) {
    state = targetGraphNode.resetState(state, currentNode);
}
```

---

## 第 4 节：ValidationLayer + 加权评分

### ValidationConfig — 可配置权重和阈值

```java
@Data
@Component
@ConfigurationProperties(prefix = "novel.validation")
public class ValidationConfig {

    private double passThreshold = 70.0;

    private Map<String, DimensionConfig> dimensions = Map.of(
        "ooc",         new DimensionConfig(0.20, Severity.FATAL),
        "logic",       new DimensionConfig(0.20, Severity.FATAL),
        "setting",     new DimensionConfig(0.15, Severity.FATAL),
        "clue",        new DimensionConfig(0.15, Severity.WARNING),
        "rhythm",      new DimensionConfig(0.10, Severity.WARNING),
        "quality",     new DimensionConfig(0.10, Severity.WARNING),
        "clueLanding", new DimensionConfig(0.10, Severity.WARNING)
    );

    public enum Severity { FATAL, WARNING, SKIP }
}
```

设计意图：
- OOC、逻辑、设定 = FATAL — 硬伤，必须修
- 伏笔、节奏、质量、伏笔落地 = WARNING — 扣分但不单独阻断
- 通过阈值 70 — 允许 WARNING 维度有瑕疵

### GradedAuditCalculator

替代当前的 `allPassed = ... && ...` 逻辑：

- 逐维计算得分 × 权重
- FATAL 维度任一未通过 → 不通过
- 否则看加权总分是否 ≥ 阈值

```java
@Component
@RequiredArgsConstructor
public class GradedAuditCalculator {

    private final ValidationConfig config;

    public GradedResult calculate(AuditResult rawAudit) {
        // 逐维评估 → 加权总分 → FATAL/WARNING 分类
    }
}
```

### AuditResult 扩展

新增字段（向后兼容）：

```java
private double weightedScore;                          // 加权综合分 (0-100)
private List<DimensionResult> dimensionResults;        // 各维度明细
private List<String> fatalFailures;                    // FATAL 级未通过的维度名
private List<String> warnings;                         // WARNING 级未通过的维度名
```

### QualityScoringNode 改造

删除手动计算 `allPassed` 的 7 行代码，改用 `GradedAuditCalculator.calculate()`。

### AuditRouterNode 改造

路由逻辑改为基于 `fatalFailures` + `weightedScore`：
- FATAL 未通过 → REPLAN（或 MANUAL_REVIEW）
- WARNING 拉低总分 → REWRITE
- 全部通过 → PASS

### 配置示例（application.yml）

```yaml
novel:
  validation:
    pass-threshold: 70.0
    dimensions:
      ooc:         { weight: 0.20, severity: FATAL }
      logic:       { weight: 0.20, severity: FATAL }
      setting:     { weight: 0.15, severity: FATAL }
      clue:        { weight: 0.15, severity: WARNING }
      rhythm:      { weight: 0.10, severity: WARNING }
      quality:     { weight: 0.10, severity: WARNING }
      clue-landing: { weight: 0.10, severity: WARNING }
```

---

## 第 5 节：AuthorAgent 预筛优化

### AuthorCheckpointFilter

每个检查点有独立的规则预筛，命中则直接 APPROVE，不调 LLM：

**outline 预筛**：
- 章纲结构完整（title, summary, keyPoints, hook 非空）且无 userIntent → 跳过
- 有 userIntent → 必须调 LLM 审查是否符合意图

**writing 预筛**：
- 自审 severity=LOW、字数 1000-8000、无修订意见、无 userIntent → 跳过
- severity=HIGH 或有修订意见 → 必须调 LLM

**audit_router 预筛**：
- allPassed=true 且 qualityScore ≥ 90 → 跳过
- 有 FATAL 失败 → 必须调 LLM 判断是否应 REDIRECT

### 预期效果

| 检查点 | 预期命中率 |
|--------|-----------|
| outline | ~30% |
| writing | ~50% |
| audit_router | ~60% |

综合：每章 AuthorAgent LLM 调用从 3 次降至 1-2 次，节省 ~40% 开销。

### 集成方式

在 `AuthorAgentImpl.beforeNode` 中，LLM 调用前插入预筛判断。预筛是纯 Java 逻辑，零延迟、零成本。

---

## 改动文件清单

| 文件 | 变更类型 | 说明 |
|------|---------|------|
| `NovelGraphNode.java` | 修改 | 新增 `resetState()` default 方法 |
| `MergeStrategy.java` | 新增 | 函数式接口 + defaultStrategy/chapterPlanMerge |
| `GraphRunner.java` | 重写 | 删除 mergeFanOutResult/resetStateForReroute，提取执行逻辑到 NodeExecutor |
| `NodeExecutor.java` | 新增 | 节点执行 + AuthorAgent 拦截 + Redis 持久化 |
| `StateCheckpointService.java` | 新增 | Redis 检查点保存/恢复 |
| `GraphDefinition.java` | 修改 | fanOut 新增 MergeStrategy 参数 |
| `MultiAgentNovelService.java` | 修改 | fanOut 调用传入 chapterPlanMerge |
| `OutlineNode.java` | 修改 | 实现 resetState |
| `WritingNode.java` | 修改 | 实现 resetState |
| `ValidationConfig.java` | 新增 | 校验权重 + 阈值配置 |
| `GradedAuditCalculator.java` | 新增 | 加权评分计算 |
| `NovelAgentState.AuditResult` | 修改 | 新增 weightedScore、dimensionResults 等字段 |
| `QualityScoringNode.java` | 修改 | 改用 GradedAuditCalculator |
| `AuditRouterNode.java` | 修改 | 路由逻辑改为基于 fatalFailures + weightedScore |
| `AuthorCheckpointFilter.java` | 新增 | 3 个检查点的规则预筛 |
| `AuthorAgentImpl.java` | 修改 | beforeNode 插入预筛判断 |
| `application.yml` | 修改 | 新增 novel.validation 配置 |

## 不在范围内

- 问题 3（"Multi-Agent" 命名）— 重命名影响面大，收益低，单独处理
- 新增校验维度
- 新增节点类型
- 持久化恢复流程（断点续跑）— 设计预留，后续实现
