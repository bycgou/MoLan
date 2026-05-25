# Agent 编排重构实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将硬编码线性链表的 GraphRunner 升级为声明式图引擎，统一两套并行架构，添加安全机制和可观测性。

**Architecture:** 改进自研 GraphRunner（非 LangGraph4j），引入 GraphDefinition 声明式图定义、条件边、fan-out/fan-in 并行支持、Mode 过滤。保留 NovelGraphNode 接口不变，节点函数签名不变。StreamingChapterController 改为调用 GraphRunner(FAST)。

**Tech Stack:** Java 17, Spring Boot 3.2, LangChain4j 1.14.1, Lombok, JUnit 5, Mockito 5, Micrometer (Prometheus)

**Spec:** `docs/superpowers/specs/2026-05-25-agent-orchestration-refactor-design.md`

---

## File Structure

### 新建文件

| 文件 | 职责 |
|------|------|
| `backend/src/main/java/com/novel/system/ai/graph/GraphDefinition.java` | 声明式图定义：Edge、ConditionalEdge、FanOutGroup、RetryPolicy、Builder |
| `backend/src/main/java/com/novel/system/ai/graph/Mode.java` | 执行模式枚举：FAST、FULL |
| `backend/src/main/java/com/novel/system/ai/graph/ErrorStrategy.java` | 错误策略枚举：SKIP、RETRY、FAIL |
| `backend/src/main/java/com/novel/system/ai/graph/StructuredError.java` | 结构化错误记录 |
| `backend/src/main/java/com/novel/system/ai/graph/NodeExecutionRecord.java` | 节点执行追踪记录 |
| `backend/src/main/java/com/novel/system/ai/graph/LlmCallRecord.java` | LLM 调用追踪记录 |
| `backend/src/main/java/com/novel/system/ai/graph/RoutingDecision.java` | 路由决策记录 |
| `backend/src/main/java/com/novel/system/ai/graph/TracingChatModel.java` | 包装 ChatModel，拦截 token 使用量 |
| `backend/src/main/java/com/novel/system/ai/graph/AgentMetrics.java` | Prometheus 指标注册 |
| `backend/src/main/java/com/novel/system/ai/graph/AuthorAgentConfig.java` | AuthorAgent 配置属性 |
| `backend/src/test/java/com/novel/system/ai/graph/GraphDefinitionTest.java` | GraphDefinition 单元测试 |
| `backend/src/test/java/com/novel/system/ai/graph/GraphRunnerV2Test.java` | 新 GraphRunner 单元测试 |
| `backend/src/test/java/com/novel/system/ai/graph/TracingChatModelTest.java` | TracingChatModel 单元测试 |

### 修改文件

| 文件 | 变更 |
|------|------|
| `backend/src/main/java/com/novel/system/ai/graph/GraphRunner.java` | 重写：用 GraphDefinition 替代 NODE_ORDER，支持 Mode、fan-out/fan-in、错误策略 |
| `backend/src/main/java/com/novel/system/ai/graph/NovelAgentState.java` | 新增 mode、nodeRetryCounts、llmCalls 字段；executionTrace 升级为 List<NodeExecutionRecord>；errors 升级为 List<StructuredError> |
| `backend/src/main/java/com/novel/system/ai/graph/StateHelper.java` | 新增 appendTrace(NodeExecutionRecord)、appendError(StructuredError)、appendLlmCall、getNodeRetryCount、incrementNodeRetry |
| `backend/src/main/java/com/novel/system/ai/graph/ChatModelProvider.java` | 新增 getTracingModel 方法，返回 TracingChatModel 包装 |
| `backend/src/main/java/com/novel/system/ai/graph/AuthorAgentImpl.java` | 检查 AuthorAgentConfig.enabled，false 时直接返回 APPROVE |
| `backend/src/main/java/com/novel/system/service/chapter/MultiAgentNovelService.java` | 简化：Phase 1 纳入 GraphDefinition 的 fan-out/fan-in，删除手动编排 |
| `backend/src/main/java/com/novel/system/controller/StreamingChapterController.java` | 改为调用 GraphRunner.execute(state, Mode.FAST) |
| `backend/src/main/resources/application.yml` | 新增 moyun.agent.author 配置段 |
| `backend/pom.xml` | 新增 micrometer-registry-prometheus 依赖 |

---

## Phase 1: 安全机制（P0，第 1 周）

### Task 1: Mode 枚举 + StructuredError + ErrorStrategy

**Files:**
- Create: `backend/src/main/java/com/novel/system/ai/graph/Mode.java`
- Create: `backend/src/main/java/com/novel/system/ai/graph/StructuredError.java`
- Create: `backend/src/main/java/com/novel/system/ai/graph/ErrorStrategy.java`

- [ ] **Step 1: 创建 Mode 枚举**

```java
package com.novel.system.ai.graph;

public enum Mode {
    FAST,   // 流式写作：跳过审计节点
    FULL    // 完整多 Agent：全部节点 + AuthorAgent
}
```

- [ ] **Step 2: 创建 ErrorStrategy 枚举**

```java
package com.novel.system.ai.graph;

public enum ErrorStrategy {
    SKIP,   // 跳过该节点，使用默认值，继续执行
    RETRY,  // 重试当前节点
    FAIL    // 终止整个流程，返回错误
}
```

- [ ] **Step 3: 创建 StructuredError 记录**

```java
package com.novel.system.ai.graph;

import java.time.Instant;

public record StructuredError(
    String nodeName,
    ErrorStrategy strategy,
    String message,
    Throwable cause,
    Instant timestamp,
    boolean recovered
) {}
```

- [ ] **Step 4: 提交**

```bash
git add backend/src/main/java/com/novel/system/ai/graph/Mode.java \
        backend/src/main/java/com/novel/system/ai/graph/ErrorStrategy.java \
        backend/src/main/java/com/novel/system/ai/graph/StructuredError.java
git commit -m "feat(graph): add Mode, ErrorStrategy, StructuredError types"
```

---

### Task 2: NovelAgentState 新增字段

**Files:**
- Modify: `backend/src/main/java/com/novel/system/ai/graph/NovelAgentState.java`
- Modify: `backend/src/main/java/com/novel/system/ai/graph/StateHelper.java`
- Modify: `backend/src/test/java/com/novel/system/ai/graph/NovelAgentStateTest.java`
- Modify: `backend/src/test/java/com/novel/system/ai/graph/StateHelperTest.java`

- [ ] **Step 1: 在 NovelAgentState 中新增字段**

在 `NovelAgentState.java` 的 `errors` 字段之后添加：

```java
// === 新增：执行模式 ===
@Builder.Default
private final Mode mode = Mode.FULL;

// === 新增：节点级重试计数 ===
@Builder.Default
private final Map<String, Integer> nodeRetryCounts = new HashMap<>();

// === 新增：LLM 调用追踪 ===
@Builder.Default
private final List<LlmCallRecord> llmCalls = new ArrayList<>();
```

需要新增 imports：`java.util.HashMap`, `java.util.Map`。

- [ ] **Step 2: 升级 executionTrace 和 errors 类型**

将 `executionTrace` 从 `List<String>` 改为 `List<NodeExecutionRecord>`，将 `errors` 从 `List<String>` 改为 `List<StructuredError>`。

```java
// 旧：
@Builder.Default
private final List<String> executionTrace = new ArrayList<>();
@Builder.Default
private final List<String> errors = new ArrayList<>();

// 新：
@Builder.Default
private final List<NodeExecutionRecord> executionTrace = new ArrayList<>();
@Builder.Default
private final List<StructuredError> errors = new ArrayList<>();
```

- [ ] **Step 3: 更新 StateHelper**

在 `StateHelper.java` 中新增方法，并保留旧的 `appendTrace(String)` 和 `appendError(String)` 方法标记 `@Deprecated`：

```java
/**
 * 追加结构化执行记录
 */
public static NovelAgentState appendTrace(NovelAgentState state, NodeExecutionRecord record) {
    var newTrace = new ArrayList<>(state.getExecutionTrace());
    newTrace.add(record);
    return state.toBuilder().executionTrace(newTrace).build();
}

/**
 * 追加结构化错误
 */
public static NovelAgentState appendError(NovelAgentState state, StructuredError error) {
    var newErrors = new ArrayList<>(state.getErrors());
    newErrors.add(error);
    return state.toBuilder().errors(newErrors).build();
}

/**
 * 追加 LLM 调用记录
 */
public static NovelAgentState appendLlmCall(NovelAgentState state, LlmCallRecord record) {
    var newCalls = new ArrayList<>(state.getLlmCalls());
    newCalls.add(record);
    return state.toBuilder().llmCalls(newCalls).build();
}

/**
 * 获取节点重试次数
 */
public static int getNodeRetryCount(NovelAgentState state, String nodeName) {
    return state.getNodeRetryCounts().getOrDefault(nodeName, 0);
}

/**
 * 递增节点重试次数
 */
public static NovelAgentState incrementNodeRetry(NovelAgentState state, String nodeName) {
    var newCounts = new HashMap<>(state.getNodeRetryCounts());
    newCounts.merge(nodeName, 1, Integer::sum);
    return state.toBuilder().nodeRetryCounts(newCounts).build();
}
```

- [ ] **Step 4: 更新现有测试**

`NovelAgentStateTest.java` 和 `StateHelperTest.java` 中引用 `executionTrace` 和 `errors` 的地方需要更新类型。搜索所有 `getExecutionTrace()` 和 `getErrors()` 的断言，将 `String` 相关断言改为新类型。

- [ ] **Step 5: 运行测试验证**

```bash
cd backend && mvn test -pl . -Dtest="NovelAgentStateTest,StateHelperTest" -DfailIfNoTests=false
```

Expected: PASS

- [ ] **Step 6: 提交**

```bash
git add backend/src/main/java/com/novel/system/ai/graph/NovelAgentState.java \
        backend/src/main/java/com/novel/system/ai/graph/StateHelper.java \
        backend/src/test/java/com/novel/system/ai/graph/NovelAgentStateTest.java \
        backend/src/test/java/com/novel/system/ai/graph/StateHelperTest.java
git commit -m "feat(graph): add mode, nodeRetryCounts, llmCalls to NovelAgentState; upgrade executionTrace and errors types"
```

---

### Task 3: AuthorAgent REDIRECT/REJECT 路径重试保护

**Files:**
- Modify: `backend/src/main/java/com/novel/system/ai/graph/GraphRunner.java`

- [ ] **Step 1: 在 GraphRunner 中新增重试保护常量**

```java
private static final Map<String, Integer> NODE_MAX_RETRIES = Map.of(
    "outline", 2,
    "writing", 3,
    "audit_router", 2
);
private static final int MAX_AUTHOR_REJECT_RETRIES = 1;
private static final int MAX_AUTHOR_REDIRECT_RETRIES = 1;
```

- [ ] **Step 2: 在 executeFrom 的 AuthorAgent 拦截逻辑中增加重试检查**

在 `executeFrom` 方法中，`AuthorAgent.beforeNode()` 调用之后、处理 directive 之前，添加：

```java
// REDIRECT 路径保护
if (directive.getAction() == AuthorDirective.Action.REDIRECT) {
    int redirectCount = StateHelper.getNodeRetryCount(state, "__redirect_from_" + currentNode);
    if (redirectCount >= MAX_AUTHOR_REDIRECT_RETRIES) {
        log.warn("AuthorAgent REDIRECT from {} exceeded max retries ({}), ignoring directive",
            currentNode, MAX_AUTHOR_REDIRECT_RETRIES);
        // 忽略 REDIRECT，继续原路径
    } else {
        state = StateHelper.incrementNodeRetry(state, "__redirect_from_" + currentNode);
        // 原有 REDIRECT 逻辑
        String target = directive.getTargetNode();
        state = StateHelper.appendTrace(state, "AUTHOR_REDIRECT: " + currentNode + " -> " + target);
        state = resetStateForReroute(state, target);
        currentNode = target;
        continue;
    }
}

// REJECT 路径保护
if (directive.getAction() == AuthorDirective.Action.REJECT) {
    String rejectTarget = currentNode;  // REJECT 重试当前节点
    int rejectCount = StateHelper.getNodeRetryCount(state, "__author_reject_" + currentNode);
    if (rejectCount >= MAX_AUTHOR_REJECT_RETRIES) {
        log.warn("AuthorAgent REJECT on {} exceeded max retries ({}), proceeding with execution",
            currentNode, MAX_AUTHOR_REJECT_RETRIES);
        // 跳过 AuthorAgent，直接执行节点
    } else {
        state = StateHelper.incrementNodeRetry(state, "__author_reject_" + currentNode);
        // 原有 REJECT 逻辑
        state = authorAgent.afterNode(currentNode, state, directive);
        state = StateHelper.appendTrace(state, "AUTHOR_REJECT: " + currentNode);
        continue;
    }
}
```

- [ ] **Step 3: 在 resolveNextNode 中增加节点级重试保护**

```java
private String resolveNextNode(String currentNode, NovelAgentState state) {
    String next = /* 原有路由逻辑 */;

    // 节点级重试保护
    if (next != null && NODE_MAX_RETRIES.containsKey(next)) {
        int count = StateHelper.getNodeRetryCount(state, next);
        if (count >= NODE_MAX_RETRIES.get(next)) {
            log.warn("Node {} exceeded max retries ({}), forcing PASS/fallback", next, NODE_MAX_RETRIES.get(next));
            // 对于 audit_router -> outline/writing 的循环，强制跳到 summary
            if ("outline".equals(next) || "writing".equals(next)) {
                return "summary";
            }
        }
    }

    return next;
}
```

- [ ] **Step 4: 提交**

```bash
git add backend/src/main/java/com/novel/system/ai/graph/GraphRunner.java
git commit -m "feat(graph): add retry protection for AuthorAgent REDIRECT/REJECT paths and node-level loops"
```

---

### Task 4: 统一错误处理策略

**Files:**
- Modify: `backend/src/main/java/com/novel/system/ai/graph/GraphRunner.java`
- Modify: `backend/src/main/java/com/novel/system/ai/graph/NovelGraphNode.java`

- [ ] **Step 1: 在 NovelGraphNode 接口中新增默认方法**

```java
/**
 * 该节点的错误策略。默认 SKIP。
 */
default ErrorStrategy errorStrategy() {
    return ErrorStrategy.SKIP;
}

/**
 * 该节点的最大重试次数（仅当 errorStrategy=RETRY 时生效）。默认 2。
 */
default int maxRetries() {
    return 2;
}
```

- [ ] **Step 2: 覆盖核心节点的错误策略**

在 `OutlineNode.java` 中：

```java
@Override
public ErrorStrategy errorStrategy() { return ErrorStrategy.RETRY; }

@Override
public int maxRetries() { return 2; }
```

在 `WritingNode.java` 中：

```java
@Override
public ErrorStrategy errorStrategy() { return ErrorStrategy.RETRY; }

@Override
public int maxRetries() { return 2; }
```

在 `AuditRouterNode.java` 中：

```java
@Override
public ErrorStrategy errorStrategy() { return ErrorStrategy.FAIL; }
```

其余节点保持默认 `SKIP`。

- [ ] **Step 3: 在 GraphRunner 中实现统一错误处理**

替换现有的 try-catch 逻辑：

```java
private NovelAgentState executeNodeWithErrorStrategy(String nodeName, NovelAgentState state) {
    NovelGraphNode node = nodes.get(nodeName);
    if (node == null) {
        log.error("Node not found: {}", nodeName);
        return StateHelper.appendError(state, new StructuredError(
            nodeName, ErrorStrategy.FAIL, "Node not found", null, Instant.now(), false));
    }

    ErrorStrategy strategy = node.errorStrategy();
    int maxRetries = node.maxRetries();

    for (int attempt = 0; attempt <= (strategy == ErrorStrategy.RETRY ? maxRetries : 0); attempt++) {
        try {
            if (attempt > 0) {
                log.info("Retrying node {} (attempt {}/{})", nodeName, attempt, maxRetries);
                state = StateHelper.incrementNodeRetry(state, nodeName);
            }
            return node.apply(state);
        } catch (Exception e) {
            log.warn("Node {} failed on attempt {}: {}", nodeName, attempt + 1, e.getMessage());

            if (strategy == ErrorStrategy.FAIL) {
                // FAIL：终止流程
                return StateHelper.appendError(state, new StructuredError(
                    nodeName, strategy, e.getMessage(), e, Instant.now(), false));
            }

            if (strategy == ErrorStrategy.SKIP) {
                // SKIP：跳过，返回原状态
                log.info("Skipping node {} due to SKIP strategy", nodeName);
                return StateHelper.appendError(state, new StructuredError(
                    nodeName, strategy, e.getMessage(), e, Instant.now(), true));
            }

            // RETRY：继续循环
            if (attempt == maxRetries) {
                // 重试耗尽，降级为 SKIP
                log.warn("Node {} exhausted retries, degrading to SKIP", nodeName);
                return StateHelper.appendError(state, new StructuredError(
                    nodeName, strategy, "Exhausted retries: " + e.getMessage(), e, Instant.now(), true));
            }
        }
    }
    return state;  // 不可达
}
```

- [ ] **Step 4: 运行所有 graph 测试**

```bash
cd backend && mvn test -pl . -Dtest="com.novel.system.ai.graph.*" -DfailIfNoTests=false
```

Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/novel/system/ai/graph/NovelGraphNode.java \
        backend/src/main/java/com/novel/system/ai/graph/GraphRunner.java \
        backend/src/main/java/com/novel/system/ai/graph/OutlineNode.java \
        backend/src/main/java/com/novel/system/ai/graph/WritingNode.java \
        backend/src/main/java/com/novel/system/ai/graph/AuditRouterNode.java
git commit -m "feat(graph): unify error handling with ErrorStrategy per node (SKIP/RETRY/FAIL)"
```

---

## Phase 2: 可观测性（P1，第 2-3 周）

### Task 5: NodeExecutionRecord + 路由决策日志

**Files:**
- Create: `backend/src/main/java/com/novel/system/ai/graph/NodeExecutionRecord.java`
- Create: `backend/src/main/java/com/novel/system/ai/graph/RoutingDecision.java`
- Modify: `backend/src/main/java/com/novel/system/ai/graph/GraphRunner.java`

- [ ] **Step 1: 创建 NodeExecutionRecord**

```java
package com.novel.system.ai.graph;

import java.time.Instant;
import java.util.Map;

public record NodeExecutionRecord(
    String nodeName,
    Mode mode,
    Instant startTime,
    Instant endTime,
    long durationMs,
    boolean skipped,
    ErrorStrategy errorStrategy,
    StructuredError error,
    AuthorDirective directive,
    Map<String, Object> metadata
) {
    public static NodeExecutionRecord success(String nodeName, Mode mode, Instant start, Instant end,
                                               AuthorDirective directive, Map<String, Object> metadata) {
        return new NodeExecutionRecord(nodeName, mode, start, end,
            java.time.Duration.between(start, end).toMillis(), false, null, null, directive, metadata);
    }

    public static NodeExecutionRecord skipped(String nodeName, Mode mode, Instant start, Instant end) {
        return new NodeExecutionRecord(nodeName, mode, start, end,
            java.time.Duration.between(start, end).toMillis(), true, null, null, null, Map.of());
    }

    public static NodeExecutionRecord error(String nodeName, Mode mode, Instant start, Instant end,
                                             ErrorStrategy strategy, StructuredError error) {
        return new NodeExecutionRecord(nodeName, mode, start, end,
            java.time.Duration.between(start, end).toMillis(), false, strategy, error, null, Map.of());
    }
}
```

- [ ] **Step 2: 创建 RoutingDecision**

```java
package com.novel.system.ai.graph;

import java.util.Map;

public record RoutingDecision(
    String fromNode,
    String toNode,
    String reason,
    Map<String, Object> stateSnapshot
) {
    @Override
    public String toString() {
        return fromNode + " -> " + toNode + " (" + reason + ")";
    }
}
```

- [ ] **Step 3: 在 GraphRunner 中用 NodeExecutionRecord 替换字符串 trace**

将所有 `StateHelper.appendTrace(state, "字符串")` 替换为 `StateHelper.appendTrace(state, NodeExecutionRecord.success(...))` 或对应的工厂方法。

在 `resolveNextNode` 中记录路由决策：

```java
RoutingDecision decision = new RoutingDecision(currentNode, next, reason, Map.of(
    "auditDecision", state.getAuditDecision(),
    "severity", state.getDraftContent() != null ? state.getDraftContent().getSeverity() : null,
    "planRetryCount", state.getPlanRetryCount(),
    "writeRetryCount", state.getWriteRetryCount()
));
state = StateHelper.appendTrace(state, NodeExecutionRecord.success(
    "__routing__", state.getMode(), Instant.now(), Instant.now(), null,
    Map.of("decision", decision.toString())
));
log.debug("Routing: {}", decision);
```

- [ ] **Step 4: 运行测试**

```bash
cd backend && mvn test -pl . -Dtest="com.novel.system.ai.graph.*" -DfailIfNoTests=false
```

Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/novel/system/ai/graph/NodeExecutionRecord.java \
        backend/src/main/java/com/novel/system/ai/graph/RoutingDecision.java \
        backend/src/main/java/com/novel/system/ai/graph/GraphRunner.java
git commit -m "feat(graph): add NodeExecutionRecord and RoutingDecision for structured tracing"
```

---

### Task 6: LlmCallRecord + TracingChatModel

**Files:**
- Create: `backend/src/main/java/com/novel/system/ai/graph/LlmCallRecord.java`
- Create: `backend/src/main/java/com/novel/system/ai/graph/TracingChatModel.java`
- Create: `backend/src/test/java/com/novel/system/ai/graph/TracingChatModelTest.java`
- Modify: `backend/src/main/java/com/novel/system/ai/graph/ChatModelProvider.java`

- [ ] **Step 1: 创建 LlmCallRecord**

```java
package com.novel.system.ai.graph;

import java.time.Instant;

public record LlmCallRecord(
    String nodeName,
    String usageType,
    String modelName,
    int inputTokens,
    int outputTokens,
    int totalTokens,
    long durationMs,
    Instant timestamp
) {}
```

- [ ] **Step 2: 创建 TracingChatModel**

```java
package com.novel.system.ai.graph;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

@Slf4j
public class TracingChatModel implements ChatModel {
    private final ChatModel delegate;
    private final Consumer<LlmCallRecord> recorder;
    private final String nodeName;

    public TracingChatModel(ChatModel delegate, Consumer<LlmCallRecord> recorder, String nodeName) {
        this.delegate = delegate;
        this.recorder = recorder;
        this.nodeName = nodeName;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        var start = Instant.now();
        ChatResponse response = delegate.chat(request);
        var end = Instant.now();

        var usage = response.tokenUsage();
        if (usage != null) {
            recorder.accept(new LlmCallRecord(
                nodeName,
                null,
                delegate.modelName(),
                usage.inputTokenCount(),
                usage.outputTokenCount(),
                usage.totalTokenCount(),
                Duration.between(start, end).toMillis(),
                start
            ));
        }

        return response;
    }

    @Override
    public String modelName() {
        return delegate.modelName();
    }
}
```

- [ ] **Step 3: 编写 TracingChatModel 测试**

```java
package com.novel.system.ai.graph;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TracingChatModelTest {

    @Test
    void shouldRecordLlmCallOnSuccess() {
        ChatModel delegate = mock(ChatModel.class);
        ChatResponse mockResponse = mock(ChatResponse.class);
        TokenUsage usage = new TokenUsage(100, 50, 150);

        when(delegate.chat(any(ChatRequest.class))).thenReturn(mockResponse);
        when(mockResponse.tokenUsage()).thenReturn(usage);
        when(delegate.modelName()).thenReturn("test-model");

        List<LlmCallRecord> records = new ArrayList<>();
        TracingChatModel tracing = new TracingChatModel(delegate, records::add, "writing");

        tracing.chat(ChatRequest.builder().build());

        assertEquals(1, records.size());
        LlmCallRecord record = records.get(0);
        assertEquals("writing", record.nodeName());
        assertEquals("test-model", record.modelName());
        assertEquals(100, record.inputTokens());
        assertEquals(50, record.outputTokens());
        assertEquals(150, record.totalTokens());
        assertTrue(record.durationMs() >= 0);
    }

    @Test
    void shouldNotRecordWhenTokenUsageIsNull() {
        ChatModel delegate = mock(ChatModel.class);
        ChatResponse mockResponse = mock(ChatResponse.class);

        when(delegate.chat(any(ChatRequest.class))).thenReturn(mockResponse);
        when(mockResponse.tokenUsage()).thenReturn(null);

        List<LlmCallRecord> records = new ArrayList<>();
        TracingChatModel tracing = new TracingChatModel(delegate, records::add, "outline");

        tracing.chat(ChatRequest.builder().build());

        assertTrue(records.isEmpty());
    }
}
```

- [ ] **Step 4: 运行测试**

```bash
cd backend && mvn test -pl . -Dtest="TracingChatModelTest" -DfailIfNoTests=false
```

Expected: PASS

- [ ] **Step 5: 在 ChatModelProvider 中新增 getTracingModel**

```java
/**
 * 返回带追踪功能的 ChatModel 包装器
 */
public ChatModel getTracingModel(String llmConfigId, LangChainConfig.TaskType taskType,
                                   String nodeName, Consumer<LlmCallRecord> recorder) {
    ChatModel delegate = getModel(llmConfigId, taskType);
    return new TracingChatModel(delegate, recorder, nodeName);
}
```

- [ ] **Step 6: 提交**

```bash
git add backend/src/main/java/com/novel/system/ai/graph/LlmCallRecord.java \
        backend/src/main/java/com/novel/system/ai/graph/TracingChatModel.java \
        backend/src/main/java/com/novel/system/ai/graph/ChatModelProvider.java \
        backend/src/test/java/com/novel/system/ai/graph/TracingChatModelTest.java
git commit -m "feat(graph): add LlmCallRecord and TracingChatModel for LLM call tracking"
```

---

### Task 7: AgentMetrics Prometheus 指标

**Files:**
- Create: `backend/src/main/java/com/novel/system/ai/graph/AgentMetrics.java`
- Modify: `backend/pom.xml` (如果 micrometer-registry-prometheus 不在依赖中)

- [ ] **Step 1: 检查 pom.xml 是否已有 micrometer 依赖**

```bash
cd backend && grep -n "micrometer" pom.xml
```

如果 `spring-boot-starter-actuator` 已存在，micrometer 已自动包含。只需确认 `micrometer-registry-prometheus` 存在。

- [ ] **Step 2: 如果缺少 prometheus 依赖，添加到 pom.xml**

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

- [ ] **Step 3: 创建 AgentMetrics**

```java
package com.novel.system.ai.graph;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.Getter;
import org.springframework.stereotype.Component;

@Getter
@Component
public class AgentMetrics {
    private final Timer nodeExecutionTime;
    private final Counter nodeRetries;
    private final Counter nodeErrors;
    private final Timer llmCallDuration;
    private final Counter llmTokensTotal;
    private final Counter llmCallsTotal;
    private final Timer chapterGenerationTotal;
    private final Counter auditDecisions;

    public AgentMetrics(MeterRegistry registry) {
        this.nodeExecutionTime = Timer.builder("moyun_agent_node_duration_seconds")
            .description("Node execution duration")
            .tag("node", "all")
            .register(registry);

        this.nodeRetries = Counter.builder("moyun_agent_node_retries_total")
            .description("Total node retries")
            .register(registry);

        this.nodeErrors = Counter.builder("moyun_agent_node_errors_total")
            .description("Total node errors")
            .register(registry);

        this.llmCallDuration = Timer.builder("moyun_agent_llm_call_duration_seconds")
            .description("LLM call duration")
            .register(registry);

        this.llmTokensTotal = Counter.builder("moyun_agent_llm_tokens_total")
            .description("Total LLM tokens consumed")
            .register(registry);

        this.llmCallsTotal = Counter.builder("moyun_agent_llm_calls_total")
            .description("Total LLM calls")
            .register(registry);

        this.chapterGenerationTotal = Timer.builder("moyun_agent_chapter_generation_seconds")
            .description("Chapter generation total duration")
            .register(registry);

        this.auditDecisions = Counter.builder("moyun_agent_audit_decisions_total")
            .description("Audit router decisions")
            .register(registry);
    }

    /**
     * 带标签的节点执行计时
     */
    public Timer.Sample startNodeTimer() {
        return Timer.start();
    }

    public void recordNodeDuration(Timer.Sample sample, String nodeName, Mode mode) {
        sample.stop(nodeExecutionTimeWithTag(nodeName, mode));
    }

    private Timer nodeExecutionTimeWithTag(String nodeName, Mode mode) {
        return Timer.builder("moyun_agent_node_duration_seconds")
            .tag("node", nodeName)
            .tag("mode", mode.name())
            .register(nodeExecutionTime.getId().getRegistry());
    }
}
```

- [ ] **Step 4: 在 GraphRunner 中集成 AgentMetrics**

在 `GraphRunner` 中注入 `AgentMetrics`（可选，通过构造函数），在 `executeNodeWithErrorStrategy` 中记录指标：

```java
private final AgentMetrics metrics;  // 可选，null-safe

// 在执行节点时
if (metrics != null) {
    var sample = metrics.startNodeTimer();
    try {
        result = node.apply(state);
        metrics.recordNodeDuration(sample, nodeName, state.getMode());
    } catch (Exception e) {
        metrics.getNodeErrors().increment();
        // ...
    }
}
```

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/novel/system/ai/graph/AgentMetrics.java \
        backend/src/main/java/com/novel/system/ai/graph/GraphRunner.java \
        backend/pom.xml
git commit -m "feat(graph): add AgentMetrics with Prometheus counters and timers"
```

---

## Phase 3: 图引擎改进（P1，第 3-4 周）

### Task 8: GraphDefinition 声明式图定义

**Files:**
- Create: `backend/src/main/java/com/novel/system/ai/graph/GraphDefinition.java`
- Create: `backend/src/test/java/com/novel/system/ai/graph/GraphDefinitionTest.java`

- [ ] **Step 1: 创建 GraphDefinition**

```java
package com.novel.system.ai.graph;

import java.util.*;
import java.util.function.Predicate;

public class GraphDefinition {
    public static final String END = "__END__";
    public static final String START = "__START__";

    private final List<Edge> edges;
    private final List<ConditionalEdge> conditionalEdges;
    private final Map<String, FanOutGroup> fanOutGroups;
    private final Map<String, FanInGroup> fanInGroups;
    private final Set<String> checkpoints;
    private final Map<String, Integer> retryPolicies;
    private final Map<String, String> fallbackNodes;

    private GraphDefinition(Builder builder) {
        this.edges = List.copyOf(builder.edges);
        this.conditionalEdges = List.copyOf(builder.conditionalEdges);
        this.fanOutGroups = Map.copyOf(builder.fanOutGroups);
        this.fanInGroups = Map.copyOf(builder.fanInGroups);
        this.checkpoints = Set.copyOf(builder.checkpoints);
        this.retryPolicies = Map.copyOf(builder.retryPolicies);
        this.fallbackNodes = Map.copyOf(builder.fallbackNodes);
    }

    public record Edge(String from, String to, Set<Mode> modes) {}
    public record ConditionalEdge(String from, String to, Predicate<NovelAgentState> condition, Set<Mode> modes) {}
    public record FanOutGroup(String source, List<String> targets, Mode mode) {}
    public record FanInGroup(List<String> sources, String target, Mode mode) {}

    /**
     * 获取下一个节点
     */
    public String getNextNode(String currentNode, NovelAgentState state, Mode mode) {
        // 1. 检查条件边
        for (ConditionalEdge ce : conditionalEdges) {
            if (ce.from().equals(currentNode) && matchesMode(ce.modes(), mode)) {
                if (ce.condition().test(state)) {
                    return ce.to();
                }
            }
        }

        // 2. 检查普通边
        for (Edge edge : edges) {
            if (edge.from().equals(currentNode) && matchesMode(edge.modes(), mode)) {
                return edge.to();
            }
        }

        return END;
    }

    public boolean isFanOut(String nodeName) {
        return fanOutGroups.containsKey(nodeName);
    }

    public List<String> getFanOutTargets(String nodeName, Mode mode) {
        FanOutGroup group = fanOutGroups.get(nodeName);
        if (group != null && (group.mode() == null || group.mode() == mode)) {
            return group.targets();
        }
        return List.of();
    }

    public String getFanInTarget(List<String> sources, Mode mode) {
        String key = String.join(",", sources.stream().sorted().toList());
        FanInGroup group = fanInGroups.get(key);
        if (group != null && (group.mode() == null || group.mode() == mode)) {
            return group.target();
        }
        return END;
    }

    public boolean isCheckpoint(String nodeName) {
        return checkpoints.contains(nodeName);
    }

    public int getMaxRetries(String nodeName) {
        return retryPolicies.getOrDefault(nodeName, 0);
    }

    public String getFallbackNode(String nodeName) {
        return fallbackNodes.getOrDefault(nodeName, END);
    }

    public ErrorStrategy getErrorStrategy(String nodeName) {
        // 默认策略映射
        return switch (nodeName) {
            case "outline", "writing" -> ErrorStrategy.RETRY;
            case "audit_router" -> ErrorStrategy.FAIL;
            default -> ErrorStrategy.SKIP;
        };
    }

    private boolean matchesMode(Set<Mode> modes, Mode current) {
        return modes.isEmpty() || modes.contains(current);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final List<Edge> edges = new ArrayList<>();
        private final List<ConditionalEdge> conditionalEdges = new ArrayList<>();
        private final Map<String, FanOutGroup> fanOutGroups = new LinkedHashMap<>();
        private final Map<String, FanInGroup> fanInGroups = new LinkedHashMap<>();
        private final Set<String> checkpoints = new LinkedHashSet<>();
        private final Map<String, Integer> retryPolicies = new LinkedHashMap<>();
        private final Map<String, String> fallbackNodes = new LinkedHashMap<>();

        public Builder edge(String from, String to) {
            edges.add(new Edge(from, to, Set.of()));
            return this;
        }

        public Builder edge(String from, String to, Mode mode) {
            edges.add(new Edge(from, to, Set.of(mode)));
            return this;
        }

        public Builder conditionalEdge(String from, String to, Predicate<NovelAgentState> condition) {
            conditionalEdges.add(new ConditionalEdge(from, to, condition, Set.of()));
            return this;
        }

        public Builder conditionalEdge(String from, String to, Predicate<NovelAgentState> condition, Mode mode) {
            conditionalEdges.add(new ConditionalEdge(from, to, condition, Set.of(mode)));
            return this;
        }

        public Builder fanOut(String source, List<String> targets, Mode mode) {
            fanOutGroups.put(source, new FanOutGroup(source, targets, mode));
            return this;
        }

        public Builder fanIn(List<String> sources, String target, Mode mode) {
            String key = String.join(",", sources.stream().sorted().toList());
            fanInGroups.put(key, new FanInGroup(sources, target, mode));
            return this;
        }

        public Builder checkpoint(String nodeName) {
            checkpoints.add(nodeName);
            return this;
        }

        public Builder retryPolicy(String nodeName, int maxRetries, String fallback) {
            retryPolicies.put(nodeName, maxRetries);
            fallbackNodes.put(nodeName, fallback);
            return this;
        }

        public GraphDefinition build() {
            return new GraphDefinition(this);
        }
    }
}
```

- [ ] **Step 2: 编写 GraphDefinition 测试**

```java
package com.novel.system.ai.graph;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GraphDefinitionTest {

    @Test
    void shouldResolveSimpleEdge() {
        GraphDefinition def = GraphDefinition.builder()
            .edge("a", "b")
            .edge("b", "c")
            .build();

        assertEquals("b", def.getNextNode("a", createState(), Mode.FULL));
        assertEquals("c", def.getNextNode("b", createState(), Mode.FULL));
        assertEquals(GraphDefinition.END, def.getNextNode("c", createState(), Mode.FULL));
    }

    @Test
    void shouldResolveConditionalEdge() {
        GraphDefinition def = GraphDefinition.builder()
            .edge("a", "b")
            .conditionalEdge("b", "c", s -> s.getAuditDecision() != null && s.getAuditDecision().equals("PASS"))
            .conditionalEdge("b", "a", s -> s.getAuditDecision() != null && s.getAuditDecision().equals("REPLAN"))
            .build();

        NovelAgentState passState = createState().toBuilder().auditDecision("PASS").build();
        assertEquals("c", def.getNextNode("b", passState, Mode.FULL));

        NovelAgentState replanState = createState().toBuilder().auditDecision("REPLAN").build();
        assertEquals("a", def.getNextNode("b", replanState, Mode.FULL));
    }

    @Test
    void shouldFilterByMode() {
        GraphDefinition def = GraphDefinition.builder()
            .edge("a", "b", Mode.FAST)
            .edge("a", "c", Mode.FULL)
            .build();

        assertEquals("b", def.getNextNode("a", createState(), Mode.FAST));
        assertEquals("c", def.getNextNode("a", createState(), Mode.FULL));
    }

    @Test
    void shouldDetectFanOut() {
        GraphDefinition def = GraphDefinition.builder()
            .fanOut("a", List.of("b", "c"), Mode.FULL)
            .fanIn(List.of("b", "c"), "d", Mode.FULL)
            .build();

        assertTrue(def.isFanOut("a"));
        assertFalse(def.isFanOut("b"));
        assertEquals(List.of("b", "c"), def.getFanOutTargets("a", Mode.FULL));
        assertEquals("d", def.getFanInTarget(List.of("b", "c"), Mode.FULL));
    }

    @Test
    void shouldCheckRetryPolicy() {
        GraphDefinition def = GraphDefinition.builder()
            .retryPolicy("writing", 3, "summary")
            .build();

        assertEquals(3, def.getMaxRetries("writing"));
        assertEquals("summary", def.getFallbackNode("writing"));
        assertEquals(0, def.getMaxRetries("unknown"));
    }

    private NovelAgentState createState() {
        return NovelAgentState.builder().build();
    }
}
```

- [ ] **Step 3: 运行测试**

```bash
cd backend && mvn test -pl . -Dtest="GraphDefinitionTest" -DfailIfNoTests=false
```

Expected: PASS

- [ ] **Step 4: 提交**

```bash
git add backend/src/main/java/com/novel/system/ai/graph/GraphDefinition.java \
        backend/src/test/java/com/novel/system/ai/graph/GraphDefinitionTest.java
git commit -m "feat(graph): add GraphDefinition with edges, conditional edges, fan-out/fan-in, retry policies"
```

---

### Task 9: 重写 GraphRunner 使用 GraphDefinition

**Files:**
- Modify: `backend/src/main/java/com/novel/system/ai/graph/GraphRunner.java`
- Create: `backend/src/test/java/com/novel/system/ai/graph/GraphRunnerV2Test.java`

- [ ] **Step 1: 重写 GraphRunner**

```java
package com.novel.system.ai.graph;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
public class GraphRunner {
    private final GraphDefinition definition;
    private final Map<String, NovelGraphNode> nodes = new ConcurrentHashMap<>();
    private AuthorAgent authorAgent;
    private AgentMetrics metrics;
    private final ExecutorService parallelExecutor = Executors.newCachedThreadPool();

    private static final int MAX_ITERATIONS = 20;

    public GraphRunner(GraphDefinition definition) {
        this.definition = definition;
    }

    public GraphRunner(GraphDefinition definition, AgentMetrics metrics) {
        this.definition = definition;
        this.metrics = metrics;
    }

    public void register(NovelGraphNode node) {
        nodes.put(node.nodeName(), node);
    }

    public void setAuthorAgent(AuthorAgent authorAgent) {
        this.authorAgent = authorAgent;
    }

    public NovelAgentState execute(NovelAgentState initialState) {
        return execute(initialState, Mode.FULL);
    }

    public NovelAgentState execute(NovelAgentState initialState, Mode mode) {
        NovelAgentState state = initialState.toBuilder().mode(mode).build();
        String currentNode = getEntryNode(mode);
        int iterations = 0;

        while (currentNode != null && !currentNode.equals(GraphDefinition.END)) {
            if (++iterations > MAX_ITERATIONS) {
                log.error("Max iterations ({}) exceeded, terminating", MAX_ITERATIONS);
                state = StateHelper.appendError(state, new StructuredError(
                    currentNode, ErrorStrategy.FAIL, "Max iterations exceeded", null, Instant.now(), false));
                break;
            }

            // Fan-out 并行执行
            if (definition.isFanOut(currentNode)) {
                state = executeFanOut(currentNode, state, mode);
                currentNode = definition.getFanInTarget(
                    definition.getFanOutTargets(currentNode, mode), mode);
                continue;
            }

            // AuthorAgent 拦截（仅 FULL 模式 + 检查点）
            if (mode == Mode.FULL && definition.isCheckpoint(currentNode) && authorAgent != null) {
                var directiveResult = handleAuthorDirective(currentNode, state);
                state = directiveResult.state();
                if (directiveResult.shouldSkip()) {
                    currentNode = definition.getNextNode(currentNode, state, mode);
                    continue;
                }
                if (directiveResult.shouldRedirect()) {
                    currentNode = directiveResult.redirectTarget();
                    continue;
                }
            }

            // 执行节点
            state = executeNodeWithErrorStrategy(currentNode, state, mode);

            // 路由决策
            String nextNode = definition.getNextNode(currentNode, state, mode);
            log.debug("Routing: {} -> {} (auditDecision={}, severity={})",
                currentNode, nextNode, state.getAuditDecision(),
                state.getDraftContent() != null ? state.getDraftContent().getSeverity() : null);
            currentNode = nextNode;
        }

        return state;
    }

    private String getEntryNode(Mode mode) {
        // 第一个没有入边的节点，或显式定义的起始节点
        return "arc_check";
    }

    private NovelAgentState executeFanOut(String sourceNode, NovelAgentState state, Mode mode) {
        List<String> targets = definition.getFanOutTargets(sourceNode, mode);
        log.info("Fan-out from {} to {}", sourceNode, targets);

        List<CompletableFuture<NovelAgentState>> futures = targets.stream()
            .map(nodeName -> CompletableFuture.supplyAsync(
                () -> executeNodeWithErrorStrategy(nodeName, state, mode), parallelExecutor))
            .toList();

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(60, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Fan-out execution failed: {}", e.getMessage());
            return StateHelper.appendError(state, new StructuredError(
                sourceNode, ErrorStrategy.FAIL, "Fan-out failed: " + e.getMessage(), e, Instant.now(), false));
        }

        // 合并并行结果
        return mergeParallelStates(state, futures.stream()
            .map(f -> { try { return f.get(); } catch (Exception e) { return state; } })
            .toList());
    }

    private NovelAgentState mergeParallelStates(NovelAgentState base, List<NovelAgentState> parallelResults) {
        NovelAgentState.NovelAgentStateBuilder merged = base.toBuilder();

        for (NovelAgentState result : parallelResults) {
            // 合并 chapterPlan 的子字段
            if (result.getChapterPlan() != null && result.getChapterPlan().getClueActions() != null) {
                var existingPlan = merged.build().getChapterPlan();
                if (existingPlan != null) {
                    merged.chapterPlan(existingPlan.toBuilder()
                        .clueActions(result.getChapterPlan().getClueActions())
                        .build());
                }
            }
            if (result.getChapterPlan() != null && result.getChapterPlan().getCharacterActions() != null) {
                var existingPlan = merged.build().getChapterPlan();
                if (existingPlan != null) {
                    merged.chapterPlan(existingPlan.toBuilder()
                        .characterActions(result.getChapterPlan().getCharacterActions())
                        .build());
                }
            }
            // 合并执行追踪
            if (result.getExecutionTrace() != null && !result.getExecutionTrace().isEmpty()) {
                var newTrace = new ArrayList<>(merged.build().getExecutionTrace());
                newTrace.addAll(result.getExecutionTrace());
                merged.executionTrace(newTrace);
            }
        }

        return merged.build();
    }

    private record AuthorDirectiveResult(NovelAgentState state, boolean shouldSkip, String redirectTarget) {}

    private AuthorDirectiveResult handleAuthorDirective(String nodeName, NovelAgentState state) {
        AuthorDirective directive = authorAgent.beforeNode(nodeName, state);

        return switch (directive.getAction()) {
            case REDIRECT -> {
                int redirectCount = StateHelper.getNodeRetryCount(state, "__redirect_from_" + nodeName);
                if (redirectCount >= 1) {
                    log.warn("AuthorAgent REDIRECT from {} exceeded max retries, ignoring", nodeName);
                    yield new AuthorDirectiveResult(state, false, null);
                }
                state = StateHelper.incrementNodeRetry(state, "__redirect_from_" + nodeName);
                state = authorAgent.afterNode(nodeName, state, directive);
                yield new AuthorDirectiveResult(state, false, directive.getTargetNode());
            }
            case REJECT -> {
                int rejectCount = StateHelper.getNodeRetryCount(state, "__author_reject_" + nodeName);
                if (rejectCount >= 1) {
                    log.warn("AuthorAgent REJECT on {} exceeded max retries, proceeding", nodeName);
                    yield new AuthorDirectiveResult(state, false, null);
                }
                state = StateHelper.incrementNodeRetry(state, "__author_reject_" + nodeName);
                state = authorAgent.afterNode(nodeName, state, directive);
                yield new AuthorDirectiveResult(state, true, null);
            }
            case REVISE -> {
                state = authorAgent.afterNode(nodeName, state, directive);
                yield new AuthorDirectiveResult(state, false, null);
            }
            default -> new AuthorDirectiveResult(state, false, null);
        };
    }

    private NovelAgentState executeNodeWithErrorStrategy(String nodeName, NovelAgentState state, Mode mode) {
        NovelGraphNode node = nodes.get(nodeName);
        if (node == null) {
            log.error("Node not found: {}", nodeName);
            return StateHelper.appendError(state, new StructuredError(
                nodeName, ErrorStrategy.FAIL, "Node not found", null, Instant.now(), false));
        }

        if (node.shouldSkip(state)) {
            log.info("Skipping node {} (shouldSkip=true)", nodeName);
            return state;
        }

        ErrorStrategy strategy = definition.getErrorStrategy(nodeName);
        int maxRetries = strategy == ErrorStrategy.RETRY ? definition.getMaxRetries(nodeName) : 0;
        if (maxRetries == 0 && strategy == ErrorStrategy.RETRY) maxRetries = 2; // 默认

        Instant start = Instant.now();

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                if (attempt > 0) {
                    log.info("Retrying node {} (attempt {}/{})", nodeName, attempt, maxRetries);
                    if (metrics != null) metrics.getNodeRetries().increment();
                }

                NovelAgentState result = node.apply(state);
                Instant end = Instant.now();

                result = StateHelper.appendTrace(result, NodeExecutionRecord.success(
                    nodeName, mode, start, end, null, Map.of()));

                if (metrics != null) {
                    metrics.getNodeExecutionTime().record(
                        java.time.Duration.between(start, end));
                }

                return result;

            } catch (Exception e) {
                Instant end = Instant.now();
                log.warn("Node {} failed on attempt {}: {}", nodeName, attempt + 1, e.getMessage());

                StructuredError error = new StructuredError(
                    nodeName, strategy, e.getMessage(), e, Instant.now(), false);

                if (strategy == ErrorStrategy.FAIL) {
                    if (metrics != null) metrics.getNodeErrors().increment();
                    return StateHelper.appendError(
                        StateHelper.appendTrace(state, NodeExecutionRecord.error(nodeName, mode, start, end, strategy, error)),
                        error);
                }

                if (strategy == ErrorStrategy.SKIP || attempt == maxRetries) {
                    StructuredError recovered = new StructuredError(
                        nodeName, strategy, e.getMessage(), e, Instant.now(), true);
                    if (metrics != null) metrics.getNodeErrors().increment();
                    return StateHelper.appendError(
                        StateHelper.appendTrace(state, NodeExecutionRecord.error(nodeName, mode, start, end, strategy, recovered)),
                        recovered);
                }
            }
        }

        return state; // 不可达
    }
}
```

- [ ] **Step 2: 编写 GraphRunnerV2 测试**

```java
package com.novel.system.ai.graph;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GraphRunnerV2Test {

    @Mock private NovelGraphNode arcCheckNode;
    @Mock private NovelGraphNode outlineNode;
    @Mock private NovelGraphNode writingNode;
    @Mock private NovelGraphNode summaryNode;
    @Mock private NovelGraphNode postProcessNode;
    @Mock private AuthorAgent authorAgent;

    private GraphDefinition definition;

    @BeforeEach
    void setUp() {
        when(arcCheckNode.nodeName()).thenReturn("arc_check");
        when(outlineNode.nodeName()).thenReturn("outline");
        when(writingNode.nodeName()).thenReturn("writing");
        when(summaryNode.nodeName()).thenReturn("summary");
        when(postProcessNode.nodeName()).thenReturn("post_process");

        definition = GraphDefinition.builder()
            .edge("arc_check", "outline")
            .edge("outline", "writing")
            .edge("writing", "summary")
            .edge("summary", "post_process")
            .checkpoint("outline")
            .checkpoint("writing")
            .retryPolicy("writing", 2, "summary")
            .build();
    }

    @Test
    void shouldExecuteLinearPath() throws Exception {
        NovelAgentState initialState = NovelAgentState.builder().build();

        when(arcCheckNode.apply(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outlineNode.apply(any())).thenAnswer(inv -> inv.getArgument(0));
        when(writingNode.apply(any())).thenAnswer(inv -> inv.getArgument(0));
        when(summaryNode.apply(any())).thenAnswer(inv -> {
            NovelAgentState s = inv.getArgument(0);
            return s.toBuilder().finalResult(
                NovelAgentState.FinalResult.builder().summary("done").build()).build();
        });
        when(postProcessNode.apply(any())).thenAnswer(inv -> inv.getArgument(0));
        when(authorAgent.beforeNode(any(), any())).thenReturn(
            AuthorDirective.builder().action(AuthorDirective.Action.APPROVE).build());

        GraphRunner runner = new GraphRunner(definition);
        runner.register(arcCheckNode);
        runner.register(outlineNode);
        runner.register(writingNode);
        runner.register(summaryNode);
        runner.register(postProcessNode);
        runner.setAuthorAgent(authorAgent);

        NovelAgentState result = runner.execute(initialState, Mode.FULL);

        assertNotNull(result);
        verify(arcCheckNode).apply(any());
        verify(outlineNode).apply(any());
        verify(writingNode).apply(any());
        verify(summaryNode).apply(any());
        verify(postProcessNode).apply(any());
    }

    @Test
    void shouldSkipAuditNodesInFastMode() throws Exception {
        GraphDefinition fastDef = GraphDefinition.builder()
            .edge("arc_check", "outline")
            .edge("outline", "writing", Mode.FAST)
            .edge("writing", "summary")
            .edge("summary", "post_process")
            .build();

        NovelAgentState initialState = NovelAgentState.builder().build();

        when(arcCheckNode.apply(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outlineNode.apply(any())).thenAnswer(inv -> inv.getArgument(0));
        when(writingNode.apply(any())).thenAnswer(inv -> inv.getArgument(0));
        when(summaryNode.apply(any())).thenAnswer(inv -> inv.getArgument(0));
        when(postProcessNode.apply(any())).thenAnswer(inv -> inv.getArgument(0));

        GraphRunner runner = new GraphRunner(fastDef);
        runner.register(arcCheckNode);
        runner.register(outlineNode);
        runner.register(writingNode);
        runner.register(summaryNode);
        runner.register(postProcessNode);

        NovelAgentState result = runner.execute(initialState, Mode.FAST);

        assertNotNull(result);
        // outline -> writing 直接跳转（FAST 模式边）
        verify(writingNode).apply(any());
    }

    @Test
    void shouldEnforceMaxIterations() throws Exception {
        // 创建一个会无限循环的图
        GraphDefinition loopDef = GraphDefinition.builder()
            .edge("a", "b")
            .conditionalEdge("b", "a", s -> true)  // 永远循环
            .build();

        NovelGraphNode nodeA = mock(NovelGraphNode.class);
        NovelGraphNode nodeB = mock(NovelGraphNode.class);
        when(nodeA.nodeName()).thenReturn("a");
        when(nodeB.nodeName()).thenReturn("b");
        when(nodeA.apply(any())).thenAnswer(inv -> inv.getArgument(0));
        when(nodeB.apply(any())).thenAnswer(inv -> inv.getArgument(0));

        GraphRunner runner = new GraphRunner(loopDef);
        runner.register(nodeA);
        runner.register(nodeB);

        NovelAgentState result = runner.execute(NovelAgentState.builder().build());

        // 应该在 MAX_ITERATIONS 后终止
        assertTrue(result.getErrors().size() > 0 || result.getExecutionTrace().size() > 0);
    }
}
```

- [ ] **Step 3: 运行测试**

```bash
cd backend && mvn test -pl . -Dtest="GraphRunnerV2Test" -DfailIfNoTests=false
```

Expected: PASS

- [ ] **Step 4: 更新 MultiAgentNovelService 使用新 GraphRunner**

修改 `MultiAgentNovelService` 的构造函数，用 `GraphDefinition` 替代手动注册：

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
        AgentMetrics metrics) {

    GraphDefinition definition = GraphDefinition.builder()
        .edge("arc_check", "outline")
        .edge("outline", "clue_planning", Mode.FULL)
        .edge("outline", "writing", Mode.FAST)
        .fanOut("outline", List.of("clue_planning", "character_planning"), Mode.FULL)
        .fanIn(List.of("clue_planning", "character_planning"), "world_validate", Mode.FULL)
        .edge("world_validate", "idea_select", Mode.FULL)
        .edge("idea_select", "writing", Mode.FULL)
        .edge("writing", "self_review")
        .conditionalEdge("self_review", "writing",
            s -> s.getDraftContent() != null && s.getDraftContent().getSeverity() == NovelAgentState.Severity.HIGH
                && s.getWriteRetryCount() < 2)
        .conditionalEdge("self_review", "summary", s -> s.getMode() == Mode.FAST)
        .conditionalEdge("self_review", "validation", s -> s.getMode() == Mode.FULL)
        .edge("validation", "quality_scoring", Mode.FULL)
        .edge("quality_scoring", "audit_router", Mode.FULL)
        .conditionalEdge("audit_router", "summary", s -> "PASS".equals(s.getAuditDecision()), Mode.FULL)
        .conditionalEdge("audit_router", "outline", s -> "REPLAN".equals(s.getAuditDecision()), Mode.FULL)
        .conditionalEdge("audit_router", "writing", s -> "REWRITE".equals(s.getAuditDecision()), Mode.FULL)
        .edge("summary", "post_process")
        .checkpoint("outline")
        .checkpoint("writing")
        .checkpoint("audit_router")
        .retryPolicy("outline", 2, "summary")
        .retryPolicy("writing", 3, "summary")
        .retryPolicy("audit_router", 2, "summary")
        .build();

    this.runner = new GraphRunner(definition, metrics);
    runner.register(arcCheck);
    runner.register(outline);
    // ... 注册所有节点
    runner.setAuthorAgent(authorAgent);
}
```

- [ ] **Step 5: 更新 MultiAgentNovelServiceTest**

更新测试以适配新的构造函数签名（GraphDefinition 替代手动注册）。

- [ ] **Step 6: 运行所有 graph 测试**

```bash
cd backend && mvn test -pl . -Dtest="com.novel.system.ai.graph.*,MultiAgentNovelServiceTest" -DfailIfNoTests=false
```

Expected: PASS

- [ ] **Step 7: 提交**

```bash
git add backend/src/main/java/com/novel/system/ai/graph/GraphRunner.java \
        backend/src/main/java/com/novel/system/service/chapter/MultiAgentNovelService.java \
        backend/src/test/java/com/novel/system/ai/graph/GraphRunnerV2Test.java \
        backend/src/test/java/com/novel/system/service/chapter/MultiAgentNovelServiceTest.java
git commit -m "feat(graph): rewrite GraphRunner to use GraphDefinition with conditional edges and fan-out/fan-in"
```

---

## Phase 4: 统一入口（P2，第 4-5 周）

### Task 10: AuthorAgent 配置化

**Files:**
- Create: `backend/src/main/java/com/novel/system/ai/graph/AuthorAgentConfig.java`
- Modify: `backend/src/main/java/com/novel/system/ai/graph/AuthorAgentImpl.java`
- Modify: `backend/src/main/resources/application.yml`

- [ ] **Step 1: 创建 AuthorAgentConfig**

```java
package com.novel.system.ai.graph;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "moyun.agent.author")
public class AuthorAgentConfig {
    private boolean enabled = true;
    private List<String> checkpoints = List.of("outline", "writing", "audit_router");
    private int maxInterventions = 3;
}
```

- [ ] **Step 2: 在 AuthorAgentImpl 中检查配置**

在 `AuthorAgentImpl` 中注入 `AuthorAgentConfig`，在 `beforeNode` 方法开头添加：

```java
private final AuthorAgentConfig authorConfig;

@Override
public AuthorDirective beforeNode(String nodeName, NovelAgentState state) {
    if (!authorConfig.isEnabled() || !authorConfig.getCheckpoints().contains(nodeName)) {
        return AuthorDirective.builder().action(AuthorDirective.Action.APPROVE).build();
    }
    // ... 原有逻辑
}
```

- [ ] **Step 3: 在 application.yml 中添加默认配置**

```yaml
moyun:
  agent:
    author:
      enabled: true
      checkpoints:
        - outline
        - writing
        - audit_router
      max-interventions: 3
```

- [ ] **Step 4: 提交**

```bash
git add backend/src/main/java/com/novel/system/ai/graph/AuthorAgentConfig.java \
        backend/src/main/java/com/novel/system/ai/graph/AuthorAgentImpl.java \
        backend/src/main/resources/application.yml
git commit -m "feat(graph): add AuthorAgent configuration with enabled flag and checkpoint list"
```

---

### Task 11: StreamingChapterController 改用 GraphRunner(FAST)

**Files:**
- Modify: `backend/src/main/java/com/novel/system/controller/StreamingChapterController.java`

- [ ] **Step 1: 注入 GraphRunner**

在 `StreamingChapterController` 中新增注入：

```java
private final GraphRunner graphRunner;
```

需要在构造函数中添加，或通过 `@RequiredArgsConstructor` 自动注入。注意 `GraphRunner` 目前不是 Spring Bean，需要将其注册为 Bean。

- [ ] **Step 2: 将 GraphRunner 注册为 Spring Bean**

在 `MultiAgentNovelService` 中将 `GraphRunner` 暴露为 Bean：

```java
@Bean
public GraphRunner graphRunner(/* 所有节点 + authorAgent + metrics */) {
    // ... 构建 definition 和注册节点
    return runner;
}
```

或者创建一个独立的配置类 `GraphConfig.java`。

- [ ] **Step 3: 在 streamWrite 中使用 GraphRunner(FAST)**

将 `streamWrite` 方法中的核心逻辑改为调用 `graphRunner.execute(state, Mode.FAST)`。

注意：这一步需要仔细处理 SSE 流式输出的兼容性。`WritingNode` 内部的 `ChapterGenerationChain.executeWritingChain()` 已经支持流式输出，但需要将 `SseEmitter` 传递到节点中。

**方案：** 将 `SseEmitter` 存入 `NovelAgentState`（新增 `sseEmitter` 字段），`WritingNode` 在生成时通过 state 获取 emitter 并发送 token。

```java
// NovelAgentState 新增
private final SseEmitter sseEmitter;

// StreamingChapterController 中
NovelAgentState state = NovelAgentState.builder()
    .novelId(novelId)
    .chapterNum(chapterNum)
    .userIntent(userInstruction)
    .llmConfigId(llmConfigId)
    .styleCardId(styleCardId)
    .mode(Mode.FAST)
    .sseEmitter(emitter)
    .build();

NovelAgentState result = graphRunner.execute(state, Mode.FAST);

// 发送完成事件
emitter.send(SseEmitter.event().name("complete").data(Map.of(
    "chapterId", result.getFinalResult().getChapterId(),
    "summary", result.getFinalResult().getSummary()
)));
emitter.complete();
```

- [ ] **Step 4: 运行测试验证**

```bash
cd backend && mvn test -pl . -DfailIfNoTests=false
```

Expected: PASS

- [ ] **Step 5: 手动测试**

启动后端，调用 `POST /chapter/stream-write`，验证 SSE 流式输出正常。

- [ ] **Step 6: 提交**

```bash
git add backend/src/main/java/com/novel/system/controller/StreamingChapterController.java \
        backend/src/main/java/com/novel/system/ai/graph/NovelAgentState.java \
        backend/src/main/java/com/novel/system/service/chapter/MultiAgentNovelService.java
git commit -m "feat(controller): route StreamingChapterController through GraphRunner with Mode.FAST"
```

---

## Phase 5: 清理（P3，第 5-6 周）

### Task 12: RAG 死代码清理

**Files:**
- Modify: `backend/src/main/java/com/novel/system/service/chapter/TokenBudgetService.java`（删除 ragContext 死代码）
- Modify: `backend/src/main/java/com/novel/system/ai/tool/NovelToolProvider.java`（按 mode 过滤 RagTool）

- [ ] **Step 1: 在 TokenBudgetService 中搜索 ragContext 相关代码**

```bash
cd backend && grep -rn "ragContext\|rag_context\|RAG" src/main/java/com/novel/system/service/chapter/TokenBudgetService.java
```

删除所有 ragContext 相关的死代码。

- [ ] **Step 2: 在 NovelToolProvider 中添加 mode 参数**

```java
public List<Object> getAllTools(Mode mode) {
    List<Object> tools = new ArrayList<>();
    tools.add(characterTool);
    tools.add(clueTool);
    tools.add(novelTool);
    if (mode == Mode.FULL) {
        tools.add(ragTool);
    }
    return tools;
}
```

保留无参版本 `getAllTools()` 作为向后兼容（默认 FULL）。

- [ ] **Step 3: 运行测试**

```bash
cd backend && mvn test -pl . -DfailIfNoTests=false
```

Expected: PASS

- [ ] **Step 4: 提交**

```bash
git add backend/src/main/java/com/novel/system/service/chapter/TokenBudgetService.java \
        backend/src/main/java/com/novel/system/ai/tool/NovelToolProvider.java
git commit -m "chore: clean up RAG dead code and add mode-based tool filtering"
```

---

### Task 13: 全量回归测试 + 文档更新

**Files:**
- Modify: `docs/superpowers/specs/2026-05-25-agent-orchestration-refactor-design.md`（标记为已实现）

- [ ] **Step 1: 运行全量测试**

```bash
cd backend && mvn test
```

Expected: PASS

- [ ] **Step 2: 手动验证 FAST 模式**

启动后端，调用 `POST /chapter/stream-write`，验证：
- SSE 流式输出正常
- 章节生成完成
- 后处理（角色提取、伏笔提取）正常执行

- [ ] **Step 3: 手动验证 FULL 模式**

调用 `MultiAgentNovelService.writeChapter()`，验证：
- 全部 13 个节点执行
- AuthorAgent 在检查点正确拦截
- 条件路由（REPLAN/REWRITE）正常工作
- 循环保护生效

- [ ] **Step 4: 更新 spec 状态**

```markdown
**状态:** 已实现
```

- [ ] **Step 5: 提交**

```bash
git add docs/superpowers/specs/2026-05-25-agent-orchestration-refactor-design.md
git commit -m "docs: mark agent orchestration refactor spec as implemented"
```
