# Agent 编排重构设计

**日期:** 2026-05-25
**状态:** 待审批
**方案:** 自顶向下（架构统一先行），集中冲刺 4-6 周

---

## 1. 背景与问题

当前小说章节生成系统存在两套并行的 Agent 编排架构：

- **架构 A**：`StreamingChapterController` → `ChapterGenerationChain`（LangChain4j AiServices 链式调用，快速流式写作）
- **架构 B**：`MultiAgentNovelService` → `GraphRunner`（13 节点图执行，完整多 Agent 流程）

此外，TypeScript 端（`moyun-ts/packages/core/src/ai/`）还有第三套并行实现。

**核心问题：**
1. 两套架构共享底层组件但编排逻辑完全不同，维护成本翻倍
2. `GraphRunner` 是硬编码线性链表（`NODE_ORDER`），非真正图，扩展困难
3. `NovelAgentState` 是 God Object（50+ 字段），隐式依赖无法追踪
4. 自修正循环缺乏硬性终止保护（REPLAN 可能无限循环）
5. 错误处理不统一（部分 try-catch 继续，部分直接抛异常）
6. 可观测性缺失（无耗时/token/决策原因追踪）
7. RAG Push/Pull 模式共存，可能导致重复上下文注入

---

## 2. 设计方案

### 2.1 统一图架构

**目标：** 合并 StreamingChapterController 和 MultiAgentNovelService 为统一的图执行入口。

**方案：** 引入 LangGraph4j 框架替代自研 GraphRunner，通过模式参数区分"快速流式"和"完整多 Agent"两种执行模式。

**框架选择：**
- GroupId: `org.bsc.langgraph4j`
- ArtifactId: `langgraph4j-core`
- GitHub: https://github.com/bsorrentino/langgraph4j

**架构图：**

```
                    ┌─────────────────────────┐
                    │    UnifiedGraphService    │
                    │  ┌─────────────────────┐  │
                    │  │   StateGraph         │  │
                    │  │   (LangGraph4j)      │  │
                    │  └─────────────────────┘  │
                    └──────────┬────────────────┘
                               │
                    ┌──────────┴──────────┐
                    │                     │
              ┌─────┴─────┐        ┌──────┴──────┐
              │ FAST 模式  │        │ FULL 模式   │
              │ (流式写作) │        │ (多Agent)   │
              └───────────┘        └─────────────┘
```

**两种模式的节点差异：**

| 节点 | FAST 模式 | FULL 模式 |
|------|----------|----------|
| `arc_check` | 执行 | 执行 |
| `outline` | 执行 | 执行 |
| `clue_planning` | 跳过 | 执行 |
| `character_planning` | 跳过 | 执行 |
| `world_validate` | 跳过 | 执行 |
| `idea_select` | 跳过 | 执行 |
| `writing` | 执行 | 执行 |
| `self_review` | 执行 | 执行 |
| `validation` | 跳过 | 执行 |
| `quality_scoring` | 跳过 | 执行 |
| `audit_router` | 跳过 | 执行 |
| `author_review_*` | 跳过 | 执行 |
| `summary` | 执行 | 执行 |
| `post_process` | 执行 | 执行 |

**变更范围：**
- 新增 `UnifiedGraphService`，构建 LangGraph4j `StateGraph`
- `StreamingChapterController` 改为调用 `UnifiedGraphService.execute(state, Mode.FAST)`
- `MultiAgentNovelService` 改为调用 `UnifiedGraphService.execute(state, Mode.FULL)`
- 删除自研 `GraphRunner`

**LangGraph4j 图定义：**

```java
var graph = new StateGraph<>(NovelAgentState::new)
    // Phase 1: 规划
    .addNode("arc_check", arcCheckNode::apply)
    .addNode("outline", outlineNode::apply)
    .addNode("clue_planning", cluePlanningNode::apply)
    .addNode("character_planning", characterPlanningNode::apply)
    .addNode("world_validate", worldValidatorNode::apply)

    // Phase 2: 写作
    .addNode("idea_select", ideaSelectionNode::apply)
    .addNode("writing", writingNode::apply)
    .addNode("self_review", selfReviewNode::apply)

    // Phase 3: 审计（仅 FULL 模式）
    .addNode("validation", validationNode::apply)
    .addNode("quality_scoring", qualityScoringNode::apply)
    .addNode("audit_router", auditRouterNode::apply)

    // Phase 4: 收尾
    .addNode("summary", summaryNode::apply)
    .addNode("post_process", postProcessNode::apply)

    // AuthorAgent 检查点节点（仅 FULL 模式）
    .addNode("author_review_outline", authorReviewOutlineNode::apply)
    .addNode("author_review_writing", authorReviewWritingNode::apply)
    .addNode("author_review_audit", authorReviewAuditNode::apply)

    // 起始边
    .addEdge(StateGraph.START, "arc_check")
    .addEdge("arc_check", "outline")

    // outline 后条件路由
    .addConditionalEdges("outline",
        state -> state.isFastMode() ? "writing" : "author_review_outline",
        Map.of("writing", "writing", "author_review_outline", "author_review_outline"))

    // AuthorAgent outline 检查点
    .addConditionalEdges("author_review_outline",
        state -> switch (state.getAuthorDirective().getAction()) {
            case APPROVE, REVISE -> "clue_planning";
            case REJECT -> "outline";
            case REDIRECT -> state.getAuthorDirective().getTargetNode();
        },
        Map.of("clue_planning", "clue_planning", "outline", "outline"))

    // 规划链（clue_planning 和 character_planning 并行执行）
    // LangGraph4j 通过 fan-out/fan-in 模式支持：从同一节点出发的多条边自动并行
    // world_validate 需要等待两条边都完成后才执行
    .addEdge("author_review_outline", "clue_planning")
    .addEdge("author_review_outline", "character_planning")
    .addEdge("clue_planning", "world_validate")
    .addEdge("character_planning", "world_validate")
    .addEdge("world_validate", "idea_select")
    .addEdge("idea_select", "writing")

    // writing 后条件路由
    .addConditionalEdges("writing",
        state -> state.isFastMode() ? "self_review" : "author_review_writing",
        Map.of("self_review", "self_review", "author_review_writing", "author_review_writing"))

    // AuthorAgent writing 检查点
    .addConditionalEdges("author_review_writing",
        state -> switch (state.getAuthorDirective().getAction()) {
            case APPROVE, REVISE -> "self_review";
            case REJECT -> "writing";
            case REDIRECT -> state.getAuthorDirective().getTargetNode();
        },
        Map.of("self_review", "self_review", "writing", "writing"))

    // self_review 条件路由
    .addConditionalEdges("self_review",
        state -> {
            if (state.getSeverity() == HIGH && state.getWriteRetryCount() < 2) return "writing";
            if (state.isFastMode()) return "summary";
            return "validation";
        },
        Map.of("writing", "writing", "summary", "summary", "validation", "validation"))

    // 审计链
    .addEdge("validation", "quality_scoring")
    .addEdge("quality_scoring", "audit_router")

    // audit_router 条件路由
    .addConditionalEdges("audit_router",
        state -> "author_review_audit",
        Map.of("author_review_audit", "author_review_audit"))

    // AuthorAgent audit 检查点
    .addConditionalEdges("author_review_audit",
        state -> switch (state.getAuditDecision()) {
            case PASS -> "summary";
            case REPLAN -> "outline";
            case REWRITE -> "writing";
            default -> "summary";
        },
        Map.of("summary", "summary", "outline", "outline", "writing", "writing"))

    // 收尾
    .addEdge("summary", "post_process")
    .addEdge("post_process", StateGraph.END);
```

**AuthorAgent 集成方式：**

将 AuthorAgent 包装为独立节点（`author_review_outline`、`author_review_writing`、`author_review_audit`），插入到关键路径中。每个节点内部调用 LLM 判断 APPROVE/REVISE/REJECT/REDIRECT，通过条件边路由到下一个节点。

---

### 2.2 NovelAgentState 拆分

**目标：** 将 God Object 拆分为阶段子状态，适配 LangGraph4j 的 `AgentState`。

**拆分结构：**

```
NovelAgentState (extends AgentState)
├── RequestInput          — 只读，请求参数
├── PlanningState         — 规划阶段输出
│   ├── chapterPlan       — ChapterPlan
│   ├── clueActions       — List<ClueAction>
│   ├── characterActions  — List<CharacterAction>
│   └── preWriteValidation — PreWriteValidation
├── WritingState          — 写作阶段输出
│   ├── selectedIdea      — String
│   ├── implementationIdeas — List<String>
│   └── draftContent      — DraftContent
├── AuditState            — 审计阶段输出
│   ├── auditResult       — AuditResult
│   └── auditDecision     — PASS / REWRITE / REPLAN / MANUAL_REVIEW
├── FinalState            — 最终输出
│   ├── finalContent      — String
│   ├── wordCount         — int
│   ├── qualityScore      — int
│   ├── chapterId         — String
│   └── summary           — String
└── FlowControl           — 流程控制
    ├── iterationCount    — int
    ├── planRetryCount    — int
    ├── writeRetryCount   — int
    ├── rewriteCount      — int
    ├── currentPhase      — GenerationPhase
    ├── executionTrace    — List<NodeExecutionRecord>
    ├── errors            — List<StructuredError>
    └── authorDirective   — AuthorDirective
```

**适配 LangGraph4j：**

```java
public class NovelAgentState extends AgentState {
    public NovelAgentState(Map<String, Object> initData) {
        super(initData);
    }

    // 强类型访问器
    public RequestInput getRequestInput() {
        return (RequestInput) value("requestInput").orElse(null);
    }

    public ChapterPlan getChapterPlan() {
        return (ChapterPlan) value("chapterPlan").orElse(null);
    }

    // ... 其他字段的访问器
}
```

**节点函数返回值：** 返回 `Map<String, Object>` 作为部分状态更新，LangGraph4j 自动合并到主状态。

**关键设计决策：**
- 子状态是逻辑分组，底层仍存储在同一个 `Map<String, Object>` 中
- 节点通过强类型访问器读取字段，通过返回 `Map` 写入字段
- 向后兼容：保留所有现有 getter，标记 `@Deprecated` 的直接访问方法逐步迁移

---

### 2.3 安全机制

#### 2.3.1 循环保护

```java
public class RetryGuard {
    private final int maxRetries;
    private final String retryCountKey;

    public boolean shouldProceed(NovelAgentState state) {
        int count = (int) state.value(retryCountKey).orElse(0);
        if (count >= maxRetries) {
            log.warn("Node exceeded max retries ({}/{}), forcing fallback", count, maxRetries);
            return false;
        }
        return true;
    }

    public NovelAgentState increment(NovelAgentState state) {
        int count = (int) state.value(retryCountKey).orElse(0);
        return state.withUpdated(retryCountKey, count + 1);
    }
}
```

**各路径的重试上限：**

| 循环路径 | 触发条件 | maxRetries | 超限后行为 |
|---------|---------|------------|-----------|
| `audit_router -> outline` (REPLAN) | `auditDecision == REPLAN` | 2 | 强制 PASS，降级输出 |
| `audit_router -> writing` (REWRITE) | `auditDecision == REWRITE` | 2 | 强制 PASS，降级输出 |
| `self_review -> writing` (HIGH severity) | `severity == HIGH` | 2 | 跳过重写，继续到 validation |
| `author_review -> outline` (REJECT) | `directive == REJECT` | 1 | 跳过 AuthorAgent，直接执行 |
| `author_review -> writing` (REJECT) | `directive == REJECT` | 1 | 跳过 AuthorAgent，直接执行 |

#### 2.3.2 统一错误处理

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
    AuthorDirective directive,       // AuthorAgent 决策
    Map<String, Object> metadata     // 节点特定数据
) {}
```

**自动收集包装器：**

```java
public NodeFunction withTracing(String nodeName, NodeFunction delegate) {
    return state -> {
        var start = Instant.now();
        try {
            var result = delegate.apply(state);
            var end = Instant.now();
            return mergeTrace(result, new NodeExecutionRecord(
                nodeName, state.getMode(), start, end,
                Duration.between(start, end).toMillis(),
                false, null, null, null, Map.of()
            ));
        } catch (Exception e) {
            var end = Instant.now();
            return handleError(state, nodeName, e, start, end);
        }
    };
}
```

#### 2.4.2 LLM 调用追踪

```java
public record LlmCallRecord(
    String usageType,        // outline / writing / summary / audit
    String modelName,
    int inputTokens,
    int outputTokens,
    int totalTokens,
    long durationMs,
    boolean cached,          // prompt cache hit
    Instant timestamp
) {}
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

### 2.5 RAG 清理

**目标：** 统一 Push/Pull 模式，消除重复上下文注入。

**方案：按模式分离**

| 模式 | RAG 行为 | 说明 |
|------|---------|------|
| FAST | Push 模式 | `ContextBuilderService` 自动注入 RAG 结果，Agent 无 Tool 访问 |
| FULL | Pull 模式 | Agent 通过 `RagTool` 主动检索，`ContextBuilderService` 不注入 RAG 层 |

**实现变更：**
1. `ContextBuilderService.buildWritingContext()` 增加 `mode` 参数
   - FAST：调用 `ragService.retrieveContext()` 注入第 6 层
   - FULL：跳过 RAG 层，只注入 Layer 1-5
2. `NovelToolProvider.getAllTools()` 增加 `mode` 参数
   - FAST：不返回 `RagTool`
   - FULL：返回 `RagTool`
3. `TokenBudgetService` 调整预算分配
   - FAST：RAG 占 12% token 预算
   - FULL：RAG 预算为 0（由 Agent 自行决定何时调用）

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

在 `AuthorReviewNode` 中检查配置，如果 `enabled=false` 则直接返回 APPROVE。

---

## 3. 实施阶段

| 阶段 | 周期 | 内容 | 产出 |
|------|------|------|------|
| **阶段 1** | 第 1-2 周 | 引入 LangGraph4j，构建 UnifiedGraphService，合并两套入口 | 可运行的统一图执行引擎 |
| **阶段 2** | 第 2-3 周 | 拆分 NovelAgentState 为子状态，适配 AgentState | 强类型子状态 + 向后兼容 |
| **阶段 3** | 第 3-4 周 | 安全机制：RetryGuard + ErrorStrategy + StructuredError | 所有循环路径有硬性终止 |
| **阶段 4** | 第 4-5 周 | 可观测性：NodeExecutionRecord + LlmCallRecord + Prometheus | 结构化追踪 + 指标导出 |
| **阶段 5** | 第 5-6 周 | RAG 清理 + AuthorAgent 配置化 + 文档 + 回归测试 | 无重复上下文 + 配置化开关 |

---

## 4. 风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| LangGraph4j API 不稳定 | 阶段 1 受阻 | 锁定版本，封装适配层，必要时回退自研 |
| 状态拆分导致回归 | 阶段 2 受阻 | 保留向后兼容 getter，逐步迁移 |
| 并行节点状态合并 | 阶段 1 受阻 | 自定义 LangGraph4j reducer 处理 fork-join |
| TypeScript 端同步 | 后续 | 阶段 5 后单独规划 TS 端迁移 |

---

## 5. 不在范围内

- TypeScript 端（`moyun-ts`）的同步迁移——单独规划
- 前端 UI 变更——不涉及
- 数据库 schema 变更——LlmConfig 等实体不变
- 新增 Agent 能力——本次只重构编排层，不新增功能节点
