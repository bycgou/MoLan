# GraphRunner 架构重构设计

日期：2026-05-26
状态：已修订（基于 review 反馈）
范围：后端 GraphRunner 及周边组件

## 背景

当前 GraphRunner 是 355 行的上帝类，混合了调度、执行、合并、重置、重试、AuthorAgent 拦截等职责。代码审查发现 6 个架构问题：

1. 扇出合并逻辑脆弱 — 硬编码字符串匹配，executionTrace 丢失
2. AuthorAgent 每次检查点无条件调用 LLM — 无预筛，浪费 token
3. "Multi-Agent" 名不副实 — 实际是固定管线 + 条件路由
4. 状态重置逻辑硬编码 — switch 语句，违反开闭原则
5. 无中间状态持久化 — 崩溃即丢失全部 LLM 调用结果
6. 校验全通过才算通过 — 七维 `&&` 连接，缺乏灰度机制

本设计解决问题 1、2、4、5、6。问题 3 是命名问题，不在本次重构范围内。

## 设计方案：方案 B — GraphRunner 拆分 + 校验层重构

### 整体架构

```
GraphRunner (调度层, ~150行)          NodeExecutor (执行层, ~100行)
  ├─ 主循环 + 条件路由                  ├─ beforeNode (AuthorAgent 拦截，唯一入口)
  ├─ fan-out 编排                       ├─ node.apply(state)
  └─ 调用 NodeExecutor                  ├─ Redis checkpoint（仅检查点节点）
                                        └─ afterNode

NovelGraphNode 接口 (扩展)
  ├─ apply(state)                       — 不变
  ├─ nodeName()                         — 不变
  ├─ shouldSkip(state)                  — 不变
  ├─ errorStrategy()                    — 不变
  ├─ maxRetries()                       — 不变
  └─ resetState(state, source) [新增]   — 重置逻辑声明（source 参数预留，当前忽略）

GraphDefinition (扩展)
  └─ fanOut(source, targets, mode, MergeStrategy) [新增] — 扇出合并策略
```

### 实施顺序

按依赖关系分批：

1. **第一批**：第 3 节（resetState）+ 第 2 节（MergeStrategy）— 无外部依赖，纯重构
2. **第二批**：第 4 节（ValidationLayer）— 依赖 AuditResult 字段扩展
3. **第三批**：第 5 节（预筛）— 依赖第 4 节的 FATAL/WARNING 分级
4. **第四批**：第 1 节（NodeExecutor + 持久化）— 依赖前三批稳定

### 回滚方案

- GradedAuditCalculator 通过 `novel.validation.use-graded-audit: true` 开关控制，默认 false（走老逻辑）
- 预筛通过 `author-agent.pre-filter-enabled: true` 开关控制，默认 true
- 持久化通过 `novel.checkpoint.enabled: true` 开关控制，默认 true

---

## 第 1 节：NodeExecutor + 持久化

### NodeExecutor

从 GraphRunner 中提取 `executeNodeWithErrorStrategy` + AuthorAgent 拦截 + 持久化，成为独立类。

**关键约束**：NodeExecutor 是 AuthorAgent 拦截的**唯一入口**。GraphRunner 主循环中原有的 AuthorAgent 拦截代码（第 54-101 行）全部删除，由 NodeExecutor 统一处理。

职责：
- AuthorAgent beforeNode 拦截（含预筛）
- 带重试策略的节点执行
- AuthorAgent afterNode
- Redis 检查点保存（仅检查点节点）

```java
@Slf4j
public class NodeExecutor {

    private final AuthorAgent authorAgent;
    private final StateCheckpointService checkpointService;
    private final GraphDefinition definition;  // 用于判断 isCheckpoint

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

        // 仅在检查点节点持久化（13 个节点中只有 3 个检查点）
        if (definition.isCheckpoint(node.nodeName())) {
            checkpointService.saveCheckpoint(node.nodeName(), result);
        }

        return result;
    }

    private NovelAgentState executeWithRetry(NovelGraphNode node, NovelAgentState state) {
        // 从 GraphRunner.executeNodeWithErrorStrategy 提取，逻辑不变
    }
}
```

### GraphRunner 改造

删除主循环中所有 AuthorAgent 相关代码（原第 54-101 行的 REDIRECT/REJECT 处理、afterNode 调用等），这些职责全部移入 NodeExecutor。GraphRunner 主循环只保留：

```java
while (currentNode != null && !currentNode.equals(END)) {
    // ... 迭代检查、日志

    NovelGraphNode node = nodes.get(currentNode);
    state = nodeExecutor.execute(node, state, mode);  // 唯一执行入口

    // fan-out 编排
    if (definition.isFanOut(currentNode)) { ... }

    // 路由到下一节点
    currentNode = getNextNode(currentNode, state, mode);
}
```

### StateCheckpointService — Redis 持久化

```java
@Service
@ConditionalOnProperty(prefix = "novel.checkpoint", name = "enabled", havingValue = "true")
public class StateCheckpointService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public void saveCheckpoint(String nodeName, NovelAgentState state) {
        String key = buildKey(state.getNovelId(), state.getChapterNum(), nodeName);
        StateSnapshot snapshot = serialize(state);  // 排除 SseEmitter
        String json = objectMapper.writeValueAsString(snapshot);
        redisTemplate.opsForValue().set(key, json, Duration.ofHours(1));
    }

    public Optional<StateSnapshot> loadLatestCheckpoint(String novelId, int chapterNum) {
        // 扫描 key 前缀 novel:checkpoint:{novelId}:{chapterNum}:*
        // 返回最新的检查点（按 nodeName 在管线中的顺序判断）
    }

    public void clearCheckpoints(String novelId, int chapterNum) {
        // 章节生成完成后清理所有检查点
    }
}
```

**StateSnapshot**：

```java
@Data
public class StateSnapshot {
    private String nodeName;          // 检查点节点名
    private String stateJson;         // 序列化后的 NovelAgentState
    private Instant savedAt;
    private String novelId;
    private int chapterNum;
}
```

**GraphRunner.execute 入口支持恢复**：

```java
public NovelAgentState execute(NovelAgentState initialState, Mode mode) {
    // 检查是否有未完成的检查点
    if (mode == Mode.FULL && checkpointService != null) {
        Optional<StateSnapshot> checkpoint = checkpointService
                .loadLatestCheckpoint(initialState.getNovelId(), initialState.getChapterNum());
        if (checkpoint.isPresent()) {
            log.info("[GraphRunner] 从检查点恢复: {}", checkpoint.get().getNodeName());
            NovelAgentState restored = deserialize(checkpoint.get());
            String resumeNode = getNextNode(checkpoint.get().getNodeName(), restored, mode);
            return executeFrom(resumeNode, restored, mode);  // 跳过已完成节点
        }
    }
    // 正常执行
    return executeFrom("arc_check", initialState.toBuilder().mode(mode).build(), mode);
}
```

关键决策：
- `SseEmitter` 不序列化 — 崩溃后无法恢复，恢复时需重新建立 SSE
- TTL 1 小时 — 一章生成通常 5-15 分钟，1 小时覆盖异常
- Key 格式：`novel:checkpoint:{novelId}:{chapterNum}:{nodeName}`
- 只在检查点节点（outline、writing、audit_router）持久化 — 其余 10 个节点存了也没人会从那里恢复
- 只在 FULL 模式下持久化 — FAST 模式代价低，不需要断点恢复
- 章节生成完成后调用 `clearCheckpoints` 清理 Redis

---

## 第 2 节：MergeStrategy + fan-out 重构

### MergeStrategy 接口

```java
/**
 * 扇出合并策略。
 * 合并语义：多个分支写同一字段时，后遍历的覆盖先遍历的。
 * 大多数场景下各分支写不同字段，不会冲突。
 */
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
            if (result.getChapterPlan() == null) {
                // 部分分支失败（异常后 chapterPlan 为 null），记录警告但不阻断
                log.warn("[MergeStrategy] 扇出分支结果 chapterPlan 为 null，可能执行失败");
                continue;
            }
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
 *
 * @param state  当前状态
 * @param source 触发重置的来源节点名（预留参数，当前实现可忽略）
 */
default NovelAgentState resetState(NovelAgentState state, String source) {
    return state;
}
```

source 参数是接口级别的低成本保险。当前实现忽略它，但保留以备将来需要差异化重置（如从 audit_router 回退到 outline 时保留部分 plan）。

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

### 评分标准化

各维度的分数来源不一致，需要统一到 0-100 量级：

| 维度 | 来源 | 评分方式 |
|------|------|---------|
| ooc | ValidationNode | 使用 `oocScore`（0-100 连续分） |
| logic | ValidationNode | passed=true → 100, passed=false → 0 |
| setting | ValidationNode | passed=true → 100, passed=false → 0 |
| clue | ValidationNode | passed=true → 100, passed=false → 0 |
| rhythm | ValidationNode | passed=true → 100, passed=false → 0 |
| quality | QualityScoringNode | 使用 `qualityScore`（0-100 连续分） |
| clueLanding | QualityScoringNode | passed=true → 100, passed=false → 0 |

**现状限制**：ValidationNode 的 logic/clue/setting/rhythm 只输出 boolean，不是连续分数。当前采用 100/0 二值映射，粒度较粗。后续可逐步让 ValidationServiceImpl 的各维度 prompt 也输出分数。

**效果**：OOC 和 quality 有连续分（0-100），其余 5 个维度是 100/0 二值。加权分 = OOC 连续分 × 0.20 + quality 连续分 × 0.10 + 其他维度的 100/0 × 权重。

### ValidationConfig — 可配置权重和阈值

```java
@Data
@Component
@ConfigurationProperties(prefix = "novel.validation")
public class ValidationConfig {

    // 功能开关：true=使用加权评分，false=走老逻辑（全通过策略）
    private boolean useGradedAudit = false;

    // 综合通过阈值（0-100）
    private double passThreshold = 60.0;

    // 各维度权重（总和应为 1.0）
    private Map<String, DimensionConfig> dimensions = Map.of(
        "ooc",         new DimensionConfig(0.20, Severity.FATAL),
        "logic",       new DimensionConfig(0.20, Severity.FATAL),
        "setting",     new DimensionConfig(0.15, Severity.FATAL),
        "clue",        new DimensionConfig(0.15, Severity.WARNING),
        "rhythm",      new DimensionConfig(0.10, Severity.WARNING),
        "quality",     new DimensionConfig(0.10, Severity.WARNING),
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

**阈值说明**：
- 阈值 60 + 当前权重 = FATAL 全通过时最低分 55，WARNING 全挂时不通过
- 允许最多 2 个 WARNING 维度未通过（55 + 0.10×100 + 0.10×100 = 75 ≥ 60）
- 具体数值在实现时用典型场景验证后调优

### GradedAuditCalculator

**调用方**：AuditRouterNode（评分者和裁决者分离）。QualityScoringNode 只负责收集原始分数，不做综合判断。

```java
@Component
@RequiredArgsConstructor
public class GradedAuditCalculator {

    private final ValidationConfig config;

    public GradedResult calculate(AuditResult rawAudit) {
        List<AuditResult.DimensionResult> dimensions = new ArrayList<>();
        List<String> fatalFailures = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // OOC：使用连续分数
        dimensions.add(evalDimension("ooc", rawAudit.isOocPassed(),
                rawAudit.getOocScore(), rawAudit.getOocDetail()));
        // 其他维度：boolean → 100/0
        dimensions.add(evalDimension("logic", rawAudit.isLogicPassed(),
                rawAudit.isLogicPassed() ? 100 : 0, rawAudit.getLogicDetail()));
        dimensions.add(evalDimension("setting", rawAudit.isSettingPassed(),
                rawAudit.isSettingPassed() ? 100 : 0, rawAudit.getSettingDetail()));
        dimensions.add(evalDimension("clue", rawAudit.isCluePassed(),
                rawAudit.isCluePassed() ? 100 : 0, rawAudit.getClueDetail()));
        dimensions.add(evalDimension("rhythm", rawAudit.isRhythmPassed(),
                rawAudit.isRhythmPassed() ? 100 : 0, rawAudit.getRhythmDetail()));
        // quality：使用连续分数
        dimensions.add(evalDimension("quality", rawAudit.isQualityPassed(),
                rawAudit.getQualityScore(), rawAudit.getQualityAssessment()));
        dimensions.add(evalDimension("clueLanding", rawAudit.isClueLandingPassed(),
                rawAudit.isClueLandingPassed() ? 100 : 0,
                formatClueLandingIssues(rawAudit.getClueLandingIssues())));

        // 加权总分
        double weightedScore = 0;
        for (AuditResult.DimensionResult d : dimensions) {
            weightedScore += d.getScore() * d.getWeight();
            if (!d.isPassed()) {
                if ("FATAL".equals(d.getSeverity())) {
                    fatalFailures.add(d.getDimension());
                } else if ("WARNING".equals(d.getSeverity())) {
                    warnings.add(d.getDimension());
                }
            }
        }

        // 判定：FATAL 任一未通过 → 不通过；否则看加权总分
        boolean allPassed = fatalFailures.isEmpty()
                && weightedScore >= config.getPassThreshold();

        return new GradedResult(weightedScore, dimensions, fatalFailures, warnings, allPassed);
    }

    private AuditResult.DimensionResult evalDimension(String name, boolean passed,
                                                        double score, String detail) {
        ValidationConfig.DimensionConfig dc = config.getDimensions().get(name);
        double weight = dc != null ? dc.getWeight() : 0;
        String severity = dc != null ? dc.getSeverity().name() : "SKIP";
        return AuditResult.DimensionResult.builder()
                .dimension(name).passed(passed).score(score)
                .weight(weight).severity(severity).detail(detail)
                .build();
    }

    @Data
    @AllArgsConstructor
    public static class GradedResult {
        private double weightedScore;
        private List<AuditResult.DimensionResult> dimensions;
        private List<String> fatalFailures;
        private List<String> warnings;
        private boolean allPassed;
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

@Getter
@Builder
@AllArgsConstructor
public static class DimensionResult {
    private String dimension;     // "ooc", "logic", ...
    private boolean passed;
    private double score;         // 该维度得分 (0-100)
    private double weight;        // 权重
    private String severity;      // "FATAL", "WARNING", "SKIP"
    private String detail;        // 描述
}
```

### QualityScoringNode 改造

删除手动计算 `allPassed` 的 7 行代码。QualityScoringNode 只负责收集原始分数（qualityScore、clueLandingPassed），不再做综合判断。

保留原有 `allPassed` 字段的写入以向后兼容：当 `useGradedAudit=false` 时走老逻辑，`=true` 时 `allPassed` 由 AuditRouterNode 的 GradedAuditCalculator 决定。

### AuditRouterNode 改造

注入 GradedAuditCalculator，在路由决策前计算加权结果：

```java
@Override
public NovelAgentState apply(NovelAgentState state) throws Exception {
    NovelAgentState.AuditResult audit = state.getAuditResult();
    if (audit == null) return pass(state);

    // 功能开关：走老逻辑还是新逻辑
    if (validationConfig.isUseGradedAudit()) {
        return gradedRoute(state, audit);
    } else {
        return legacyRoute(state, audit);  // 保留原有逻辑
    }
}

private NovelAgentState gradedRoute(NovelAgentState state, AuditResult audit) {
    GradedAuditCalculator.GradedResult graded = calculator.calculate(audit);

    // 回写加权结果到 AuditResult
    NovelAgentState.AuditResult updatedAudit = audit.toBuilder()
            .weightedScore(graded.getWeightedScore())
            .dimensionResults(graded.getDimensions())
            .fatalFailures(graded.getFatalFailures())
            .warnings(graded.getWarnings())
            .allPassed(graded.isAllPassed())
            .build();
    state = state.toBuilder().auditResult(updatedAudit).build();

    // 路由决策
    if (!graded.getFatalFailures().isEmpty()) {
        if (state.getPlanRetryCount() < MAX_PLAN_RETRIES) {
            return replan(state);
        }
        return manualReview(state);
    }
    if (!graded.isAllPassed() && state.getRewriteCount() < MAX_REWRITES) {
        return rewrite(state);
    }
    return pass(state);
}
```

### 配置示例（application.yml）

```yaml
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
```

---

## 第 5 节：AuthorAgent 预筛优化

### AuthorCheckpointFilter（AuthorAgentImpl 私有方法）

作为 AuthorAgentImpl 的私有方法，3 个检查点各一个方法，不需要独立类。

**outline 预筛**（保守条件）：
- 章纲结构完整（title, summary, keyPoints, hook 非空）
- 无 userIntent
- 无 narrativeDirection（AuthorAgent 之前注入的叙事方向）
- 以上全部满足 → 跳过

**writing 预筛**（保守条件）：
- 自审 severity=LOW
- 字数 1000-8000
- 无修订意见
- 无 userIntent
- 无 narrativeDirection
- 以上全部满足 → 跳过

**audit_router 预筛**：
- allPassed=true 且 qualityScore ≥ 90 → 跳过（高置信度，LLM 几乎不可能 REVISE）
- 有 FATAL 失败 → 必须调 LLM

字数阈值（1000-8000）提取到 `AuthorAgentConfig` 中可配。

### 预期效果

| 检查点 | 预期命中率 | 风险 |
|--------|-----------|------|
| outline | ~20% | 低（保守条件） |
| writing | ~40% | 低（保守条件） |
| audit_router | ~60% | 极低（高置信度） |

综合：每章 AuthorAgent LLM 调用从 3 次降至 1-2 次，节省 ~30-40% 开销。

### 功能开关

```yaml
author-agent:
  pre-filter-enabled: true  # 默认开启
```

---

## 改动文件清单

| 文件 | 变更类型 | 说明 |
|------|---------|------|
| `NovelGraphNode.java` | 修改 | 新增 `resetState()` default 方法 |
| `MergeStrategy.java` | 新增 | 函数式接口 + defaultStrategy/chapterPlanMerge |
| `GraphRunner.java` | 重写 | 删除 AuthorAgent 拦截/mergeFanOutResult/resetStateForReroute，委托 NodeExecutor |
| `NodeExecutor.java` | 新增 | 节点执行 + AuthorAgent 拦截（唯一入口）+ Redis 持久化 |
| `StateCheckpointService.java` | 新增 | Redis 检查点保存/恢复/清理 |
| `GraphDefinition.java` | 修改 | fanOut 新增 MergeStrategy 参数 |
| `MultiAgentNovelService.java` | 修改 | fanOut 调用传入 chapterPlanMerge |
| `OutlineNode.java` | 修改 | 实现 resetState |
| `WritingNode.java` | 修改 | 实现 resetState |
| `ValidationConfig.java` | 新增 | 校验权重 + 阈值 + 功能开关 |
| `GradedAuditCalculator.java` | 新增 | 加权评分计算 |
| `NovelAgentState.AuditResult` | 修改 | 新增 weightedScore、dimensionResults、DimensionResult 等 |
| `QualityScoringNode.java` | 修改 | 删除 allPassed 计算，只收集原始分数 |
| `AuditRouterNode.java` | 修改 | 注入 GradedAuditCalculator，新增 gradedRoute/legacyRoute 双路径 |
| `AuthorAgentImpl.java` | 修改 | beforeNode 插入预筛（私有方法） |
| `AuthorAgentConfig.java` | 修改 | 新增 preFilterEnabled、字数阈值等配置 |
| `application.yml` | 修改 | 新增 novel.validation + author-agent 配置 |

## 不在范围内

- 问题 3（"Multi-Agent" 命名）— 重命名影响面大，收益低
- 新增校验维度
- 新增节点类型
- TS 版同步 — TS 版是简化版，本次重构不跟随，后续视需要单独处理
- ValidationServiceImpl 各维度输出连续分数 — 当前用 100/0 二值映射，后续逐步改进
