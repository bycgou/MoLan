# 卷级编排健壮性提升设计

## 背景

卷级编排（ArcOrchestration）在生产中频繁失败，日志显示两类主要错误：

1. **`json is null`**（4 次）：LLM 返回 null，`json.trim()` 触发 NPE。原因可能是 thinking-only 响应或网络中断。
2. **`Unexpected end-of-input`**（2 次）：LLM 输出的 JSON 被截断（maxTokens 不足），现有的 `repairTruncatedJson` 只能修复括号不平衡，无法处理字符串中间截断。

根本原因：`ArcOrchestrationAgent.orchestrateArc()` 返回 `String`，靠 prompt 软约束要求 JSON 输出，没有硬性保障。

## 目标

- 消除 JSON 解析失败，将失败率从当前 ~50% 降到接近 0
- 解析失败时自动重试，不依赖人工干预
- 保留诊断日志，便于排查模型问题

## 改动范围

### 1. 新建 ArcOrchestrationOutput DTO

**新文件：** `backend/src/main/java/com/novel/system/ai/dto/ArcOrchestrationOutput.java`

`ArcOrchestration` 是 MongoDB 实体，包含 `id`、`novelId`、`arcId`、`createdAt` 等持久化字段。如果直接用它作为 LLM 返回类型，LangChain4j 生成的 JSON Schema 会包含这些字段，导致 LLM 可能生成无意义的 id 值，且后置赋值逻辑混乱。

因此创建一个专用 DTO，只包含 LLM 应生成的字段：

```java
@Data
public class ArcOrchestrationOutput {
    @Description("卷名称")
    private String arcName;

    @Description("本卷整体节奏描述，不超过50字")
    private String overallRhythm;

    @Description("高潮章节号数组，元素为章节序号整数")
    private List<Integer> climaxChapters;

    @Description("各章节编排计划")
    private List<ChapterPlanOutput> chapters;

    @Data
    public static class ChapterPlanOutput {
        @Description("章节序号，从1开始的整数")
        private Integer chapterNum;
        @Description("节奏标签，只能是 quest、fire、constellation 之一")
        private String rhythmTag;
        @Description("节奏强度，1到10的整数")
        private Integer rhythmIntensity;
        @Description("叙事视角，只能是 主角、配角、反派、旁观者 之一")
        private String perspective;
        @Description("叙事模式，只能是 线性、倒叙、插叙、多线并行 之一")
        private String narrativeMode;
        @Description("伏笔计划")
        private CluePlanOutput cluePlan;
        @Description("冲突类型，只能是 外部战斗、内心挣扎、人际矛盾、道德困境、信息不对称 之一")
        private String conflictType;
        @Description("结局类型，只能是 险胜、惨胜、平局、撤退、得而复失、敌退、平淡过渡 之一")
        private String outcomeType;
        @Description("本章必须发生的核心事件，不超过20字")
        private String keyEvent;
        @Description("章尾钩子方向，不超过10字")
        private String hookDirection;
    }

    @Data
    public static class CluePlanOutput {
        @Description("本章需埋设的伏笔描述字符串数组")
        private List<String> bury;
        @Description("本章需回收的伏笔描述字符串数组")
        private List<String> recycle;
    }
}
```

DTO 与实体的映射在 Service 层完成（见第 4 节）。

### 2. ArcOrchestrationAgent — 返回类型改为 DTO

**文件：** `backend/src/main/java/com/novel/system/ai/agent/ArcOrchestrationAgent.java`

```java
// 改前
String orchestrateArc(@UserMessage String userPrompt);

// 改后
ArcOrchestrationOutput orchestrateArc(@UserMessage String userPrompt);
```

同时在 @SystemMessage 的【输出铁律】中追加防御性冗余说明（注意：核心保障来自框架的结构化输出，以下只是降低模型输出偏差的概率）：

```
4. 所有数值字段(rhythmIntensity, chapterNum)必须输出整数，不要输出字符串
5. bury和recycle必须输出字符串数组，即使为空也要输出空数组[]
```

### 3. LangChainConfig — STRUCTURED 任务关闭 thinking

**文件：** `backend/src/main/java/com/novel/system/config/LangChainConfig.java`

当前 `buildChatModel` 无条件设置 `returnThinking(true)` + `sendThinking(true)`。问题：
- 部分模型不支持 thinking + structured output 同时启用
- thinking tokens 可能干扰框架对 JSON 响应的提取
- 结构化输出任务不需要模型的思考过程

改动：将 `TaskType` 传入 `buildChatModel`，当 `TaskType == STRUCTURED` 时关闭 thinking。

```java
// createChatModel(config, taskType) 调用处
ChatModel model = langChainConfig.createChatModel(config, taskType);  // 传入 taskType

// buildChatModel 内部
if (taskType != TaskType.STRUCTURED) {
    builder.returnThinking(true)
           .sendThinking(true);
}
```

### 4. ArcOrchestrationServiceImpl — 重试 + DTO 映射 + 动态 maxTokens

**文件：** `backend/src/main/java/com/novel/system/service/orchestration/ArcOrchestrationServiceImpl.java`

**4a. `executeArcOrchestrationChain` 返回 `ArcOrchestrationOutput`，加重试和退避：**

```java
private static final int MAX_RETRIES = 3;

private ArcOrchestrationOutput executeArcOrchestrationChain(
        String userPrompt, String llmConfigId, int chapterCount) {
    // ... 解析 config ...

    // 动态 maxTokens：每章约 200 tokens，乘以 1.5 安全系数，最低 4096
    int dynamicMaxTokens = Math.max(4096, (int) (chapterCount * 200 * 1.5));
    if (config.getMaxTokens() == null || config.getMaxTokens() < dynamicMaxTokens) {
        config.setMaxTokens(dynamicMaxTokens);
        log.info("动态设置maxTokens={}, 章节数={}", dynamicMaxTokens, chapterCount);
    }

    ChatModel model = langChainConfig.createChatModel(config, LangChainConfig.TaskType.STRUCTURED);

    ArcOrchestrationAgent agent = AiServices.builder(ArcOrchestrationAgent.class)
            .chatModel(model)
            .build();

    Exception lastException = null;
    for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
        try {
            ArcOrchestrationOutput result = agent.orchestrateArc(userPrompt);
            if (result == null) {
                log.warn("LLM返回null, attempt={}/{}, model={}",
                        attempt, MAX_RETRIES, config.getModelName());
                if (attempt < MAX_RETRIES) {
                    Thread.sleep(1000L * attempt);  // 指数退避
                }
                continue;
            }
            return result;
        } catch (Exception e) {
            lastException = e;
            // 区分可重试和不可重试异常
            if (!isRetriableException(e)) {
                log.error("编排调用不可重试异常, model={}, error={}",
                        config.getModelName(), e.getMessage());
                throw new RuntimeException("卷编排失败（不可重试）: " + e.getMessage(), e);
            }
            log.warn("编排调用可重试异常, attempt={}/{}, model={}, error={}",
                    attempt, MAX_RETRIES, config.getModelName(), e.getMessage());
            if (attempt < MAX_RETRIES) {
                Thread.sleep(1000L * attempt);
            }
        }
    }
    throw new RuntimeException("卷编排失败，已重试" + MAX_RETRIES + "次", lastException);
}

private boolean isRetriableException(Exception e) {
    String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
    // 网络错误、超时、null 返回、服务端 5xx → 可重试
    if (msg.contains("connection") || msg.contains("timeout") || msg.contains("reset")) return true;
    if (msg.contains("500") || msg.contains("502") || msg.contains("503")) return true;
    // JSON 解析错误、schema 不匹配 → 不可重试（同一 prompt 结果不会变）
    if (msg.contains("json") || msg.contains("deserialize") || msg.contains("schema")) return false;
    // 默认可重试
    return true;
}
```

**4b. `orchestrate` 方法 — DTO 映射到实体，元数据赋值留在调用方：**

```java
@Override
public ArcOrchestration orchestrate(String novelId, String arcId, String llmConfigId) {
    // ... 幂等检查、收集上下文（不变）...

    String userPrompt = buildOrchestrationPrompt(novel, arc, pendingClues, charStates);

    // 调用 Agent（只传 chapterCount 用于动态 maxTokens）
    ArcOrchestrationOutput output = executeArcOrchestrationChain(
            userPrompt, llmConfigId, arc.getChapterCount());

    // DTO → 实体映射
    ArcOrchestration orchestration = mapToEntity(output, novelId, arcId);
    orchestrationRepository.save(orchestration);

    log.info("卷编排生成完成: novelId={}, arcId={}, 章节数={}",
            novelId, arcId, orchestration.getChapters() != null ? orchestration.getChapters().size() : 0);
    return orchestration;
}

private ArcOrchestration mapToEntity(ArcOrchestrationOutput output, String novelId, String arcId) {
    ArcOrchestration entity = new ArcOrchestration();
    entity.setId(novelId + "_" + arcId);
    entity.setNovelId(novelId);
    entity.setArcId(arcId);
    entity.setArcName(output.getArcName());
    entity.setOverallRhythm(output.getOverallRhythm());
    entity.setClimaxChapters(output.getClimaxChapters());
    entity.setCreatedAt(LocalDateTime.now().toString());

    if (output.getChapters() != null) {
        entity.setChapters(output.getChapters().stream().map(ch -> {
            ArcOrchestration.ChapterPlan plan = new ArcOrchestration.ChapterPlan();
            plan.setChapterNum(ch.getChapterNum());
            plan.setRhythmTag(ch.getRhythmTag());
            plan.setRhythmIntensity(ch.getRhythmIntensity());
            plan.setPerspective(ch.getPerspective());
            plan.setNarrativeMode(ch.getNarrativeMode());
            if (ch.getCluePlan() != null) {
                ArcOrchestration.CluePlan cluePlan = new ArcOrchestration.CluePlan();
                cluePlan.setBury(ch.getCluePlan().getBury());
                cluePlan.setRecycle(ch.getCluePlan().getRecycle());
                plan.setCluePlan(cluePlan);
            }
            plan.setConflictType(ch.getConflictType());
            plan.setOutcomeType(ch.getOutcomeType());
            plan.setKeyEvent(ch.getKeyEvent());
            plan.setHookDirection(ch.getHookDirection());
            return plan;
        }).collect(Collectors.toList()));
    }
    return entity;
}
```

**4c. 删除以下两个方法（不再需要）：**
- `parseOrchestrationResult`
- `repairTruncatedJson`

**4d. 移除 `ObjectMapper` 依赖**（如果此类中没有其他地方使用）。

## 不改动的部分

- `buildOrchestrationPrompt` — 不需要改，prompt 内容已经足够
- `getChapterPlan` 和 `invalidate` — 不涉及
- `ArcOrchestration.java` 实体类 — 不加 @Description（注解只加在 DTO 上，实体保持干净）

## 预期效果

| 失败模式 | 改动前 | 改动后 |
|---------|-------|-------|
| JSON 截断（maxTokens 不足） | 直接解析失败 | 动态 maxTokens 预防 + 框架抛异常 → 重试 |
| LLM 返回 null | NPE 崩溃 | 检测到 null → 重试 3 次（指数退避）+ 诊断日志 |
| thinking 干扰结构化输出 | 未处理 | STRUCTURED 任务关闭 thinking |
| 字段类型错误 | Jackson 解析失败 | @Description 引导 + 框架 Schema 校验 |
| markdown 包裹 | 需手动剥离 | 框架自动处理 |
| 网络瞬断 | 直接失败 | 可重试异常 → 指数退避重试 |
| 模型能力不足（不可重试） | 无区分 | isRetriableException 区分，快速失败 |

## 涉及文件清单

1. **新建** `backend/src/main/java/com/novel/system/ai/dto/ArcOrchestrationOutput.java` — LLM 输出 DTO
2. **修改** `backend/src/main/java/com/novel/system/ai/agent/ArcOrchestrationAgent.java` — 返回类型改 DTO + @SystemMessage 微调
3. **修改** `backend/src/main/java/com/novel/system/config/LangChainConfig.java` — STRUCTURED 任务关闭 thinking
4. **修改** `backend/src/main/java/com/novel/system/service/orchestration/ArcOrchestrationServiceImpl.java` — 重试 + DTO 映射 + 动态 maxTokens + 删除手动解析
