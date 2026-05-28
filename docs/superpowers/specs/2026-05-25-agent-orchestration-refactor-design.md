# Agent 编排重构设计

**日期:** 2026-05-25
**状态:** 已实现
**方案:** 改进自研 GraphRunner（声明式图定义 + 条件路由 + 并行支持），集中冲刺 4-6 周
**代码验证:** 已对照后端代码逐一验证，修正了初始分析中的夸大/过时描述

---

## 1. 背景与问题

当前小说章节生成系统存在两套并行的 Agent 编排架构：

- **架构 A**：`StreamingChapterController` → `ChapterGenerationChain`（LangChain4j AiServices 链式调用，快速流式写作）
- **架构 B**：`MultiAgentNovelService` → `GraphRunner`（13 节点图执行，完整多 Agent 流程）

此外，TypeScript 端（`moyun-ts/packages/core/src/ai/`）还有第三套并行实现。

**核心问题（已代码验证）：**

| # | 问题 | 验证结论 | 关键证据 |
|---|------|---------|---------|
| 1 | 两套并行架构 | ✅ 确认 | StreamingChapterController 和 MultiAgentNovelService 共享底层组件但编排完全独立 |
| 2 | GraphRunner 硬编码线性链表 | ✅ 确认 | NODE_ORDER 硬编码 13 个节点名，currentIndex++ 线性推进，resolveNextNode 只处理 2 个节点的回退，不支持并行（runPhase1 绕过 GraphRunner 手动编排） |
| 3 | NovelAgentState 字段膨胀 | ⚠️ 部分确认 | 直接字段 ~25 个（非 50+），但含内嵌类字段总数接近 50；定性合理 |
| 4 | 自修正循环终止保护不完善 | ⚠️ 部分确认 | 有 MAX_ITERATIONS=20 兜底和 AuditRouterNode 的 MAX_REWRITES=2/MAX_PLAN_RETRIES=3，但 AuthorAgent REDIRECT 路径无精细保护，仅靠全局计数器 |
| 5 | 错误处理不统一 | ✅ 确认 | 三种模式混用：GraphRunner try-catch 继续、StreamingChapterController completeWithError 终止、节点内部静默降级 |
| 6 | 可观测性缺失 | ✅ 确认 | executionTrace 只是 List<String>，无耗时/token/决策原因；LLM 调用追踪完全缺失；无 Prometheus 指标 |
| 7 | RAG Push/Pull 共存 | ⚠️ 过时/夸大 | Push 模式已基本废弃（ContextBuilderService 未注入 RAG 层），仅剩 TokenBudgetService 中 ragContext 死代码和 RagTool 无差别注入 |

---

## 2. 设计方案

### 2.1 改进自研 GraphRunner

**目标：** 将 GraphRunner 从硬编码线性链表升级为声明式图引擎，支持条件边、并行执行、模式过滤，同时保持 SSE 流式兼容。

**选择自研而非 LangGraph4j 的理由：**

| 维度 | 改进自研 | LangGraph4j |
|------|---------|-------------|
| SSE 流式兼容 | ✅ 零成本（当前已有 SseEmitter 集成） | ⚠️ 需验证，可能需要额外适配层 |
| 并行支持 | ✅ CompletableFuture 已有先例 | ⚠️ fan-out/fan-in 不成熟 |
| 侵入性 | 低（增量改进现有代码） | 中（需适配 AgentState、重写节点函数签名） |
| API 稳定性 | ✅ 自控 | ⚠️ 小众框架，API 可能变化 |
| 代码量 | ~500 行增量 | ~500 行 + 外部依赖 + 适配层 |

**改进后的 GraphRunner API：**

```java
public class GraphRunner {
    private final GraphDefinition definition;
    private final Map<String, NovelGraphNode> nodes;
    private final AuthorAgent authorAgent;

    // 替代当前的 NODE_ORDER + resolveNextNode()
    public NovelAgentState execute(NovelAgentState state, Mode mode) {
        String currentNode = definition.getEntryNode(mode);
        while (currentNode != null && !currentNode.equals(END)) {
            // 1. 循环保护检查
            if (!retryGuard(currentNode, state)) {
                currentNode = definition.getNextNode(currentNode, state, mode);
                continue;
            }

            // 2. AuthorAgent 拦截（仅 FULL 模式 + 检查点节点）
            if (mode == FULL && definition.isCheckpoint(currentNode)) {
                AuthorDirective directive = authorAgent.beforeNode(currentNode, state);
                switch (directive.getAction()) {
                    case REDIRECT -> { currentNode = directive.getTargetNode(); continue; }
                    case REJECT -> { state = authorAgent.afterNode(currentNode, state, directive); currentNode = definition.getNextNode(currentNode, state, mode); continue; }
                    case REVISE -> { state = authorAgent.afterNode(currentNode, state, directive); }
                    default -> {}
                }
            }

            // 3. 执行节点（带错误策略）
            state = executeWithErrorStrategy(currentNode, state);

            // 4. AuthorAgent 后处理
            if (mode == FULL && definition.isCheckpoint(currentNode)) {
                state = authorAgent.afterNode(currentNode, state, state.getAuthorDirective());
            }

            // 5. 路由决策
            currentNode = definition.getNextNode(currentNode, state, mode);
        }
        return state;
    }
}
```

**声明式图定义（替代 NODE_ORDER）：**

```java
public class GraphDefinition {
    // 边定义
    private final List<Edge> edges;
    // 条件边定义
    private final List<ConditionalEdge> conditionalEdges;
    // 并行分组
    private final Map<String, List<String>> fanOutGroups;

    public record Edge(String from, String to, Set<Mode> modes) {}
    public record ConditionalEdge(String from, String to, Predicate<NovelAgentState> condition, Set<Mode> modes) {}
}
```

**图定义构建：**

```java
GraphDefinition.builder()
    // 线性链
    .edge("arc_check", "outline")
    .edge("outline", "clue_planning", Mode.FULL)
    .edge("outline", "writing", Mode.FAST)            // FAST 模式捷径

    // 并行分组：clue_planning 和 character_planning 同时执行，汇合到 world_validate
    .fanOut("outline", List.of("clue_planning", "character_planning"), Mode.FULL)
    .fanIn(List.of("clue_planning", "character_planning"), "world_validate", Mode.FULL)

    .edge("world_validate", "idea_select", Mode.FULL)
    .edge("idea_select", "writing", Mode.FULL)
    .edge("writing", "self_review")

    // 条件边（替代 resolveNextNode if-else）
    .conditionalEdge("self_review", "writing",
        s -> s.getSeverity() == HIGH && s.getWriteRetryCount() < 2)
    .conditionalEdge("self_review", "summary", s -> s.isFastMode())
    .conditionalEdge("self_review", "validation", s -> !s.isFastMode())

    .edge("validation", "quality_scoring", Mode.FULL)
    .edge("quality_scoring", "audit_router", Mode.FULL)

    // audit_router 条件路由
    .conditionalEdge("audit_router", "summary", s -> s.getAuditDecision() == PASS, Mode.FULL)
    .conditionalEdge("audit_router", "outline", s -> s.getAuditDecision() == REPLAN, Mode.FULL)
    .conditionalEdge("audit_router", "writing", s -> s.getAuditDecision() == REWRITE, Mode.FULL)

    .edge("summary", "post_process")
    .edge("post_process", END)

    // AuthorAgent 检查点
    .checkpoint("outline")
    .checkpoint("writing")
    .checkpoint("audit_router")

    // 重试保护策略
    .retryPolicy("outline", new RetryPolicy(2, "outline", s -> s.clearChapterPlan()))
    .retryPolicy("writing", new RetryPolicy(3, "writing", s -> s.clearDraft()))
    .retryPolicy("audit_router", new RetryPolicy(2, "outline", s -> s.clearAudit()))

    .build();
```

**并行执行实现（改进现有 CompletableFuture 模式）：**

```java
// 在 GraphRunner 中处理 fan-out/fan-in
if (definition.isFanOut(currentNode)) {
    List<String> parallelNodes = definition.getFanOutTargets(currentNode, mode);
    List<CompletableFuture<NovelAgentState>> futures = parallelNodes.stream()
        .map(node -> CompletableFuture.supplyAsync(() -> executeWithErrorStrategy(node, state), executor))
        .toList();

    // 等待所有并行节点完成，合并状态
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    state = mergeParallelStates(state, futures.stream().map(CompletableFuture::join).toList());

    // 跳转到 fan-in 目标
    currentNode = definition.getFanInTarget(parallelNodes, mode);
    continue;
}
```

**变更范围：**
- 重写 `GraphRunner`：用 `GraphDefinition` 替代 `NODE_ORDER` + `resolveNextNode()`
- 重写 `MultiAgentNovelService`：Phase 1 手动编排纳入 GraphDefinition 的 fan-out/fan-in
- `StreamingChapterController` 改为调用 `GraphRunner.execute(state, Mode.FAST)`
- 保留 `NovelGraphNode` 接口不变（`apply(NovelAgentState)` 签名不变）

---

### 2.2 NovelAgentState 改进

**目标：** 逻辑分组 + 接口约束，减少隐式依赖。

**注意：** 不做激进拆分（底层仍用 Builder 模式），只做逻辑分组和访问约束。

**改进方案：**

```java
@Builder(toBuilder = true)
public class NovelAgentState {
    // === 请求输入（只读）===
    @Getter private final String novelId;
    @Getter private final int chapterNum;
    @Getter private final String userIntent;
    @Getter private final String styleCardId;
    @Getter private final String llmConfigId;
    @Getter private final Mode mode;          // 新增：FAST / FULL

    // === 规划阶段 ===
    @Getter private final ChapterPlan chapterPlan;
    @Getter private final PreWriteValidation preWriteValidation;

    // === 写作阶段 ===
    @Getter private final String selectedIdea;
    @Getter private final List<String> implementationIdeas;
    @Getter private final DraftContent draftContent;

    // === 审计阶段 ===
    @Getter private final AuditResult auditResult;
    @Getter private final AuditDecision auditDecision;

    // === 最终输出 ===
    @Getter private final FinalResult finalResult;

    // === 流程控制 ===
    @Getter private final int iterationCount;
    @Getter private final int planRetryCount;
    @Getter private final int writeRetryCount;
    @Getter private final int rewriteCount;
    @Getter private final GenerationPhase currentPhase;
    @Getter private final List<NodeExecutionRecord> executionTrace;  // 升级
    @Getter private final List<StructuredError> errors;              // 升级
    @Getter private final AuthorDirective authorDirective;

    // === 可观测性（新增）===
    @Getter private final List<LlmCallRecord> llmCalls;
}
```

**关键改进：**
- 新增 `mode` 字段（FAST/FULL），节点通过 `state.getMode()` 判断是否执行
- `executionTrace` 从 `List<String>` 升级为 `List<NodeExecutionRecord>`
- `errors` 从 `List<String>` 升级为 `List<StructuredError>`
- 新增 `llmCalls` 字段追踪 LLM 调用
- 保留 `toBuilder()` 模式不变，节点函数签名不变

**接口约束（后续渐进式迁移）：** 通过代码审查和文档约定各节点的读写字段范围，不做运行时强制。

---

### 2.3 安全机制（最高优先级）

#### 2.3.1 循环保护增强

**当前状态：** 有 `MAX_ITERATIONS=20` 全局兜底和 `MAX_REWRITES=2/MAX_PLAN_RETRIES=3` 节点级限制，但 AuthorAgent REDIRECT 路径无精细保护。

**改进：** 在 GraphRunner 的路由决策中增加每路径重试计数：

```java
// 在 NovelAgentState 中新增
private final Map<String, Integer> nodeRetryCounts;  // 节点级重试计数

// 在 GraphRunner 路由逻辑中
public String resolveNextNode(String currentNode, NovelAgentState state, Mode mode) {
    String next = definition.getNextNode(currentNode, state, mode);

    // 检查目标节点的重试次数
    if (next != null && state.getNodeRetryCount(next) >= definition.getMaxRetries(next)) {
        log.warn("Node {} exceeded max retries ({}), forcing fallback",
            next, definition.getMaxRetries(next));
        return definition.getFallbackNode(next);  // 跳过，使用降级路径
    }

    return next;
}
```

**各路径的重试上限（已有 + 新增）：**

| 循环路径 | 触发条件 | maxRetries | 超限后行为 | 当前状态 |
|---------|---------|------------|-----------|---------|
| `audit_router -> outline` (REPLAN) | `auditDecision == REPLAN` | 3 | 强制 PASS，降级输出 | ✅ 已有 MAX_PLAN_RETRIES=3 |
| `audit_router -> writing` (REWRITE) | `auditDecision == REWRITE` | 2 | 强制 PASS，降级输出 | ✅ 已有 MAX_REWRITES=2 |
| `self_review -> writing` (HIGH severity) | `severity == HIGH` | 2 | 跳过重写，继续执行 | ✅ 已有 |
| `author_review -> outline` (REJECT) | `directive == REJECT` | 1 | 跳过 AuthorAgent，直接执行 | ⚠️ 新增 |
| `author_review -> writing` (REJECT) | `directive == REJECT` | 1 | 跳过 AuthorAgent，直接执行 | ⚠️ 新增 |
| `author_review -> *` (REDIRECT) | `directive == REDIRECT` | 1 | 忽略 REDIRECT，继续原路径 | ⚠️ 新增 |
| 全局迭代上限 | `iterationCount` | 20 | 强制终止，返回错误 | ✅ 已有 |

#### 2.3.2 统一错误处理

**当前状态：** 三种模式混用——GraphRunner try-catch 继续、StreamingChapterController completeWithError 终止、节点内部静默降级。

**改进：** 定义统一的错误策略枚举，在 GraphRunner 中统一处理：

```java
public enum ErrorStrategy {
    SKIP,   // 跳过该节点，使用默认值，继续执行
    RETRY,  // 重试当前节点
    FAIL    // 终止整个流程，返回错误
}
```

**各节点的默认策略：**

| 节点 | 默认策略 | 说明 |
|------|---------|------|
| `arc_check` | SKIP | 弧线约束非必需 |
| `outline` | RETRY (2次) | 大纲是核心，值得重试 |
| `clue_planning` | SKIP | 伏笔规划失败不影响写作 |
| `character_planning` | SKIP | 同上 |
| `world_validate` | SKIP | 世界观验证失败可降级 |
| `idea_select` | SKIP | 想法选择失败用默认 |
| `writing` | RETRY (2次) | 核心节点 |
| `self_review` | SKIP | 自审失败跳过 |
| `validation` | SKIP | 审计失败跳过 |
| `quality_scoring` | SKIP | 评分失败用默认分 |
| `audit_router` | FAIL | 路由决策必须成功 |
| `summary` | SKIP | 摘要失败跳过 |
| `post_process` | SKIP | 后处理失败异步重试 |

#### 2.3.3 结构化错误

```java
public record StructuredError(
    String nodeName,
    ErrorStrategy strategy,
    String message,
    Throwable cause,
    Instant timestamp,
    boolean recovered  // 是否被恢复
) {}
```

替换当前的 `List<String> errors`。

---

### 2.4 可观测性

#### 2.4.1 执行追踪记录

```java
public record NodeExecutionRecord(
    String nodeName,
    Mode mode,
    Instant startTime,
    Instant endTime,
    long durationMs,
    boolean skipped,
    ErrorStrategy errorStrategy,
    StructuredError error,           // null if success
    AuthorDirective directive,       // AuthorAgent 决策（仅 FULL 模式检查点）
    Map<String, Object> metadata     // 节点特定数据（如 auditDecision, qualityScore）
) {}
```

**在 GraphRunner 中自动收集：**

```java
private NovelAgentState executeWithErrorStrategy(String nodeName, NovelAgentState state) {
    var start = Instant.now();
    try {
        NovelGraphNode node = nodes.get(nodeName);
        NovelAgentState result = node.apply(state);
        var end = Instant.now();

        // 追加执行记录到 executionTrace
        return StateHelper.appendTrace(result, new NodeExecutionRecord(
            nodeName, state.getMode(), start, end,
            Duration.between(start, end).toMillis(),
            false, null, null, null, Map.of()
        ));
    } catch (Exception e) {
        var end = Instant.now();
        ErrorStrategy strategy = definition.getErrorStrategy(nodeName);
        return handleNodeError(state, nodeName, e, strategy, start, end);
    }
}
```

#### 2.4.2 LLM 调用追踪

```java
public record LlmCallRecord(
    String nodeName,         // 调用来源节点
    String usageType,        // outline / writing / summary / audit
    String modelName,
    int inputTokens,
    int outputTokens,
    int totalTokens,
    long durationMs,
    Instant timestamp
) {}
```

**实现方式：** 在 `ChatModelProvider` 中包装 `ChatModel`，拦截 `chat()` 调用提取 token 使用量：

```java
public class TracingChatModel implements ChatModel {
    private final ChatModel delegate;
    private final Consumer<LlmCallRecord> recorder;
    private final String nodeName;

    @Override
    public ChatResponse chat(ChatRequest request) {
        var start = Instant.now();
        ChatResponse response = delegate.chat(request);
        var end = Instant.now();

        TokenUsage usage = response.tokenUsage();
        recorder.accept(new LlmCallRecord(
            nodeName, null, delegate.modelName(),
            usage.inputTokenCount(), usage.outputTokenCount(), usage.totalTokenCount(),
            Duration.between(start, end).toMillis(), start
        ));

        return response;
    }
}
```

存储到 `NovelAgentState.llmCalls` 字段。

#### 2.4.3 Prometheus 指标

```java
@Component
public class AgentMetrics {
    // 节点级指标
    private final Timer nodeExecutionTime;
    private final Counter nodeRetries;
    private final Counter nodeErrors;

    // LLM 级指标
    private final Timer llmCallDuration;
    private final Counter llmTokensTotal;
    private final Counter llmCallsTotal;

    // 流程级指标
    private final Timer chapterGenerationTotal;
    private final Counter auditDecisions;  // label: PASS/REWRITE/REPLAN
    private final Gauge activeGenerations;
}
```

**指标命名规范：**
- `moyun_agent_node_duration_seconds{node="writing", mode="FULL"}`
- `moyun_agent_llm_tokens_total{usage="outline", model="deepseek-chat"}`
- `moyun_agent_chapter_generation_seconds{mode="FAST"}`

#### 2.4.4 路由决策日志

```java
public record RoutingDecision(
    String fromNode,
    String toNode,
    String reason,
    Map<String, Object> stateSnapshot
) {}
```

存入 `executionTrace`，同时以 `DEBUG` 级别输出到日志。

---

### 2.5 RAG 清理（低优先级）

**当前状态：** Push 模式已基本废弃（ContextBuilderService 未注入 RAG 层），仅剩 TokenBudgetService 中 ragContext 死代码和 RagTool 无差别注入。

**改进：** 清理死代码 + 按模式过滤 RagTool：

1. 删除 `TokenBudgetService` 中 ragContext 相关死代码
2. `NovelToolProvider.getAllTools()` 增加 `mode` 参数
   - FAST：不返回 `RagTool`（写作者不需要主动检索）
   - FULL：返回 `RagTool`

---

### 2.6 AuthorAgent 配置化

**目标：** 支持运行时开关 AuthorAgent，开发/测试时可跳过以节省成本。

**方案：**

```yaml
moyun:
  agent:
    author:
      enabled: true          # 全局开关
      checkpoints:           # 检查点列表
        - outline
        - writing
        - audit_router
      max-interventions: 3   # 单次章节生成最大干预次数
```

在 GraphRunner 的 AuthorAgent 拦截逻辑中检查配置，如果 `enabled=false` 则跳过 `beforeNode/afterNode` 调用。

---

## 3. 实施阶段

| 阶段 | 周期 | 优先级 | 内容 | 产出 |
|------|------|--------|------|------|
| **阶段 1** | 第 1 周 | P0 | 安全机制：AuthorAgent REDIRECT/REJECT 路径重试保护 + 结构化错误 | 所有循环路径有硬性终止 |
| **阶段 2** | 第 2-3 周 | P1 | 可观测性：NodeExecutionRecord + TracingChatModel + Prometheus 指标 | 结构化追踪 + 指标导出 |
| **阶段 3** | 第 3-4 周 | P1 | 改进 GraphRunner：声明式 GraphDefinition + 条件边 + fan-out/fan-in + 模式过滤 | 可扩展的图引擎 |
| **阶段 4** | 第 4-5 周 | P2 | 统一入口：StreamingChapterController 调用 GraphRunner(FAST) + AuthorAgent 配置化 | 两套架构合并 |
| **阶段 5** | 第 5-6 周 | P3 | RAG 清理 + NovelAgentState 字段整理 + 文档 + 回归测试 | 代码清洁度提升 |

**阶段依赖关系：**
```
阶段 1 (安全) ──────────────────────────────────────> 可独立部署
阶段 2 (可观测) ────────────────────────────────────> 可独立部署
阶段 3 (图引擎) ──依赖阶段 1 的 RetryGuard──> 阶段 4 (统一入口)
阶段 4 (统一入口) ──依赖阶段 3 的 GraphDefinition──> 可部署
阶段 5 (清理) ──依赖阶段 3、4──> 最终交付
```

---

## 4. 风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| GraphDefinition 条件边表达力不足 | 阶段 3 受阻 | 条件边用 `Predicate<NovelAgentState>` 声明，足够表达当前所有路由逻辑 |
| 并行节点状态合并（fan-out/fan-in） | 阶段 3 受阻 | 参考现有 CompletableFuture 合并逻辑，提取为通用 merge 函数 |
| TracingChatModel 拦截 LangChain4j token usage | 阶段 2 受阻 | LangChain4j 的 ChatResponse 已包含 TokenUsage，直接提取即可 |
| TypeScript 端同步 | 后续 | 阶段 5 后单独规划 TS 端迁移 |
| StreamingChapterController 改造回归 | 阶段 4 受阻 | FAST 模式节点集是当前流程的子集，行为应完全一致 |

---

## 5. 不在范围内

- TypeScript 端（`moyun-ts`）的同步迁移——单独规划
- 前端 UI 变更——不涉及
- 数据库 schema 变更——LlmConfig 等实体不变
- 新增 Agent 能力——本次只重构编排层，不新增功能节点
- NovelAgentState 激进拆分——保留 Builder 模式，只做逻辑分组和字段整理
