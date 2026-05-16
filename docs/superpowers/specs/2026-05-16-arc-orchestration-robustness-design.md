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

### 1. ArcOrchestrationAgent — 返回类型改为 POJO

**文件：** `backend/src/main/java/com/novel/system/ai/agent/ArcOrchestrationAgent.java`

将 `orchestrateArc` 返回类型从 `String` 改为 `ArcOrchestration`：

```java
// 改前
String orchestrateArc(@UserMessage String userPrompt);

// 改后
ArcOrchestration orchestrateArc(@UserMessage String userPrompt);
```

LangChain4j 的 AiServices 会根据 `@SystemMessage` 中的 JSON schema 描述和实体类字段自动反序列化。如果模型返回无法解析的内容，框架会抛出异常而非返回截断字符串。

### 2. ArcOrchestration — 添加 @Description 注解

**文件：** `backend/src/main/java/com/novel/system/entity/mongo/ArcOrchestration.java`

给所有需要反序列化的字段添加 `dev.langchain4j.service.Description` 注解，帮助框架理解字段含义和约束：

**顶层字段：**
- `arcName` — @Description("卷名称")
- `overallRhythm` — @Description("本卷整体节奏描述，不超过50字")
- `climaxChapters` — @Description("高潮章节号数组，元素为章节序号整数")

**ChapterPlan 字段：**
- `chapterNum` — @Description("章节序号，从1开始的整数")
- `rhythmTag` — @Description("节奏标签，只能是 quest(任务驱动)、fire(冲突爆发)、constellation(多线交织) 之一")
- `rhythmIntensity` — @Description("节奏强度，1到10的整数")
- `perspective` — @Description("叙事视角，只能是 主角、配角、反派、旁观者 之一")
- `narrativeMode` — @Description("叙事模式，只能是 线性、倒叙、插叙、多线并行 之一")
- `cluePlan` — @Description("伏笔计划，包含 bury 和 recycle 两个字符串数组")
- `conflictType` — @Description("冲突类型，只能是 外部战斗、内心挣扎、人际矛盾、道德困境、信息不对称 之一")
- `outcomeType` — @Description("结局类型，只能是 险胜、惨胜、平局、撤退、得而复失、敌退、平淡过渡 之一")
- `keyEvent` — @Description("本章必须发生的核心事件，不超过20字")
- `hookDirection` — @Description("章尾钩子方向，不超过10字")

**CluePlan 字段：**
- `bury` — @Description("本章需埋设的伏笔描述字符串数组")
- `recycle` — @Description("本章需回收的伏笔描述字符串数组")

### 3. ArcOrchestrationServiceImpl — 重试 + 简化

**文件：** `backend/src/main/java/com/novel/system/service/orchestration/ArcOrchestrationServiceImpl.java`

**3a. `executeArcOrchestrationChain` 改为返回 `ArcOrchestration` 并加重试：**

```java
private static final int MAX_RETRIES = 3;

private ArcOrchestration executeArcOrchestrationChain(String userPrompt, String llmConfigId, String novelId, String arcId) {
    // ... 解析 config 和构建 model（不变）...

    ArcOrchestrationAgent agent = AiServices.builder(ArcOrchestrationAgent.class)
            .chatModel(model)
            .build();

    Exception lastException = null;
    for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
        try {
            ArcOrchestration result = agent.orchestrateArc(userPrompt);
            if (result == null) {
                log.warn("LLM返回null, attempt={}/{}, model={}",
                        attempt, MAX_RETRIES, config.getModelName());
                continue;
            }
            return result;
        } catch (Exception e) {
            lastException = e;
            log.warn("编排调用异常, attempt={}/{}, model={}, error={}",
                    attempt, MAX_RETRIES, config.getModelName(), e.getMessage());
        }
    }
    throw new RuntimeException("卷编排失败，已重试" + MAX_RETRIES + "次", lastException);
}
```

**3b. `orchestrate` 方法调整调用：**

```java
// 改前
String resultJson = executeArcOrchestrationChain(userPrompt, llmConfigId);
ArcOrchestration orchestration = parseOrchestrationResult(resultJson, novelId, arcId);

// 改后
ArcOrchestration orchestration = executeArcOrchestrationChain(userPrompt, llmConfigId, novelId, arcId);
orchestration.setNovelId(novelId);
orchestration.setArcId(arcId);
orchestration.setCreatedAt(LocalDateTime.now().toString());
if (orchestration.getId() == null) {
    orchestration.setId(novelId + "_" + arcId);
}
```

**3c. 删除以下两个方法（不再需要）：**
- `parseOrchestrationResult`
- `repairTruncatedJson`

**3d. 移除不再需要的 `ObjectMapper` 依赖**（如果此类中没有其他地方使用）。

### 4. ArcOrchestrationAgent @SystemMessage 微调

**文件：** `backend/src/main/java/com/novel/system/ai/agent/ArcOrchestrationAgent.java`

在 @SystemMessage 的【输出铁律】中追加一条，强调字段类型约束：

```
4. 所有数值字段(rhythmIntensity, chapterNum)必须输出整数，不要输出字符串
5. bury和recycle必须输出字符串数组，即使为空也要输出空数组[]
```

## 不改动的部分

- `LangChainConfig.java` — 不需要改，现有的 `returnThinking(true)` 和 `TaskType.STRUCTURED` 配置可以保留
- `buildOrchestrationPrompt` — 不需要改，prompt 内容已经足够
- `getChapterPlan` 和 `invalidate` — 不涉及

## 预期效果

| 失败模式 | 改动前 | 改动后 |
|---------|-------|-------|
| JSON 截断 | 直接解析失败 | 框架抛异常 → 重试 3 次 |
| LLM 返回 null | NPE 崩溃 | 检测到 null → 重试 3 次 + 诊断日志 |
| 字段类型错误 | Jackson 解析失败 | @Description 引导 + 框架校验 |
| markdown 包裹 | 需手动剥离 | 框架自动处理 |

## 涉及文件清单

1. `backend/src/main/java/com/novel/system/ai/agent/ArcOrchestrationAgent.java` — 返回类型改 POJO + @SystemMessage 微调
2. `backend/src/main/java/com/novel/system/entity/mongo/ArcOrchestration.java` — 添加 @Description 注解
3. `backend/src/main/java/com/novel/system/service/orchestration/ArcOrchestrationServiceImpl.java` — 重试逻辑 + 删除手动解析
