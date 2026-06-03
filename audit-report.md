# MoYun 项目代码审计与改进方案

> 本文档整合自三份独立审计报告，分别从**项目整体质量**、**完整写书能力与 Token 成本**、**多章连贯性问题**三个维度对 MoYun 长篇小说自动生成系统进行深度分析。

---

## 一、项目整体审计（a1）

### 1.1 亮点

1. **产品定位清晰**：围绕"百万字长篇小说"构建，重点解决长篇创作里的记忆、伏笔、角色一致性、节奏和文风问题。
2. **后端模块拆分细**：`ai/graph`、`service/chapter`、`service/story`、`service/style`、`service/genre`、`service/orchestration` 等职责清楚，说明项目已经做过多轮架构迭代。
3. **文风卡和反 AI 味检测深入**：包括句式多样性、套话黑名单、对话比例、感官描写、样本文风注入等。
4. **体裁模板丰富**：`backend/src/main/resources/genres/` 下已有玄幻、仙侠、科幻、都市、末世、悬疑、古言、知乎短篇等多个类型指南。
5. **测试并非完全缺失**：后端有 21 个测试文件，TypeScript core 有 13 个测试文件，主要覆盖 GraphRunner、校验、RAG、服务层等核心逻辑。

### 1.2 主要问题与风险

#### 问题 1：数据库初始化缺失，README 启动流程不完整

README 只让用户启动 MySQL/MongoDB/Redis 容器，但项目里没有找到 SQL schema、migration、Flyway/Liquibase、docker-compose 或初始化脚本。

后端 mapper 依赖这些表：`sys_user`、`novel_base`、`novel_chapter`、`novel_arc`、`llm_config`、`clue_book`、`world_clock`、`style_card`、`novel_style_binding` 等。MyBatis 不会自动建表，新环境很可能启动后注册/登录/创建小说时直接报表不存在。

#### 问题 2：权限隔离不完整，存在越权风险

认证已接入 Spring Security，但业务层没有系统性做资源归属校验。明显风险包括：

- `NovelController` 创建和列表使用了 `userId`，但获取、更新、删除、导出小说主要按 `novelId` 操作，没有检查小说是否属于当前用户。
- `ChapterController`、`CharacterController`、`ClueController` 多数接口按 `novelId` 操作，也没有统一 owner 校验。
- `LlmConfigController` 的 `getConfig`、`deleteConfig`、`testSavedConfig` 按 `configId` 操作，没有检查配置归属；`testSavedConfig` 会读取真实 API Key 去调用模型，可能被滥用。
- `/auth/**` 被整体放行，导致 `/auth/me` 也被 `permitAll`。无 token 时控制器会取不到 `userId` request attribute，可能返回 500 而不是 401。

建议增加统一的 `ResourceAccessService` 或 AOP/Interceptor，在所有 `novelId/configId/cardId/chapterId` 操作前做 owner 校验。

#### 问题 3：默认密钥与默认密码不适合生产

`application.yml` 中：

- MySQL 默认密码是 `123456`
- JWT 默认值是 `changeme_generate_with_openssl_rand_base64_48`

`JwtConfig` 会校验 secret 长度，也会拒绝包含 `default` 或 `123456` 的值，但当前默认值长度足够且不包含这些关键词，因此会被接受。生产环境应改成：没有 `JWT_SECRET` 就直接拒绝启动，而不是提供可用默认 JWT 密钥。

#### 问题 4：SSE 异步流式生成里 SecurityContext 没有真正传入子线程

`StreamingChapterController.streamWrite()` 捕获了 `SecurityContext`，但在 `CompletableFuture.runAsync()` 内没有调用 `SecurityContextHolder.setContext(securityContext)`。这会导致异步线程里通过 `SecurityContextHolder` 获取当前用户失败，LLM 路由可能 fallback 到"任意活跃配置"，用错用户的模型配置。这是比较严重的多用户场景 bug。

#### 问题 5：Redis Stack 向量索引实现疑似有命令调用问题

`RedisVectorServiceImpl` 里有类似 `connection.execute("FT.INFO " + indexName)` 的调用。Spring Data Redis 的低层 `execute` 通常需要把命令名和参数分开传，当前把整条命令当成单个 command string，疑似会导致索引创建/查询失败。

另外，向量维度固定为 1024，但不同 embedding 模型常见维度可能是 768、1024、1536、3072 等。建议将维度绑定到 LLM/embedding 配置，或者在首次建索引时动态确定。

#### 问题 6：Next 前端的文件上传有 Content-Type 问题

`frontend-v2/lib/api.ts` 默认设置 `"Content-Type": "application/json"`。`postForm()` 传入 `FormData` 时使用 `headers: {}`，但这不会移除默认 JSON Content-Type。结果上传文风文件时，浏览器不会正确设置 multipart boundary，`/style-card/upload` 可能失败。修复方式：FormData 请求不要手动设置 Content-Type，让浏览器自动生成。

#### 问题 7：工程入口太多，维护成本高

当前同时存在：

- Java 后端主系统
- Vue 前端
- Next 前端
- TypeScript core/cli/web 重写版
- docs 中大量演进计划

API 类型、页面能力、核心逻辑容易分叉。建议明确一个主线：短期可以把 Vue 作为稳定版，Next 作为新版；或直接冻结 Vue，只维护 Next。

#### 问题 8：缺少一键本地开发环境

有 Dockerfile，但没有根级 `docker-compose.yml`，也没有统一 `.env.example`。建议补齐：

- `docker-compose.yml`
- `backend/src/main/resources/db/migration`
- `.env.example`
- `Makefile` 或 `justfile`
- healthcheck
- demo seed 数据

#### 问题 9：本地开发配置文件被提交

仓库里有 `.claude/settings.local.json`，包含本地路径、工具权限、历史开发命令等。建议把 `.claude/` 加入 `.gitignore`，并从仓库移除。

### 1.3 建议优先级

| 优先级 | 内容 |
|--------|------|
| **P0：先让项目稳定可跑** | 1. 增加数据库 schema/migration；2. 增加 docker-compose.yml；3. 增加 .env.example；4. 提供明确的启动顺序和健康检查；5. 加 Maven wrapper 或说明必须安装 Maven |
| **P1：修安全和多用户隔离** | 1. `/auth/me` 不应被 `/auth/**` 放行；2. 所有 novelId/chapterId/configId/cardId 操作都要校验 owner；3. LLM 配置的读取、删除、测试必须按当前用户过滤；4. 异步线程恢复 SecurityContext，或直接把 userId 显式传入服务层；5. JWT_SECRET 必须由环境变量提供，禁止默认密钥启动 |
| **P2：修关键运行 bug** | 1. 修 Redis Stack FT.CREATE/FT.INFO/FT.SEARCH 命令封装；2. 修 Next.js FormData 上传；3. 对 embedding 维度做配置化或自动校验；4. 校准 README API；5. 统一 Vue 和 Next 的 API 类型定义 |
| **P3：降低复杂度** | 1. 明确 Vue/Next 谁是主前端；2. 明确 Java 后端和 moyun-ts 的关系；3. 把 docs 中的历史计划整理成当前架构说明；4. 给 GraphRunner、RAG、文风卡、权限模型补充架构图和 ADR |

### 1.4 一句话评价

这是一个想法完整、业务野心很大、核心 Agent 编排设计有价值的项目；当前最大短板不是功能，而是**可运行性、权限隔离、部署初始化和多端一致性**。先补齐 schema/docker-compose/权限边界，再继续扩展 Agent 能力，会更稳。

---

## 二、完整写书能力与 Token 成本分析（a2）

### 2.1 核心结论

项目已经具备完整写小说的基本流水线：**卷纲 → 每卷章纲 → 逐章正文 → 自审/校验 → 摘要 → 人物/伏笔/事件/时间线后处理 → 批量继续生成**。

从功能上说，只要每章都有章纲，它可以批量把一本小说写完。但当前更适合：

- ✅ **自动生成长篇初稿**，然后人工抽检、修订、补设定、改节奏
- ❌ **从一句创意开始，无人值守生成百万字高质量完本**

### 2.2 最大问题：默认 multi-agent 批量生成可能不真正使用确认过的章纲

向导里有"生成卷章纲 → 用户确认章纲 → 批量生成正文"的流程。但默认生成引擎是 multi-agent，批量生成时只是筛选"有章纲但无正文"的章节，然后调用 `generateChapter()`。

**问题在于 `OutlineNode.apply()` 没有优先读取数据库里已经确认的 `chapterOutline`。** 也就是说，你确认的章纲目前更像"允许进入批量生成的门票"，不一定是正文生成真正遵守的章纲。

修复方案：

1. `OutlineNode` 先查 `NovelChapter.chapterOutline`
2. 如果已有章纲，就把它作为当前 `ChapterPlan` 或强制约束
3. 只有没有章纲时才重新生成
4. 或者批量生成时临时切换到 legacy 引擎

### 2.3 Token 用量：偏高，主要不是正文消耗

#### 单章 LLM 调用次数

默认 FULL multi-agent 每章大约包括：

| 阶段 | LLM 调用次数 |
|------|-------------|
| AuthorAgent 检查点 | 2-3 次 |
| 章纲生成 | 1 次 |
| 伏笔规划 | 1 次 |
| 角色动线规划 | 1 次 |
| 世界观校验 | 1 次 |
| 正文写作 | 1 次 |
| 自审 | 1 次 |
| 五重校验（OOC/逻辑/伏笔/设定/节奏） | 5 次 |
| 质量评分 | 1 次 |
| 摘要 | 1 次 |
| 后处理（人物、伏笔、时间线、事件提取） | 4 次 |
| **合计** | **约 18-20 次** |

如果发生重写、重规划、失败重试，还会继续增加。

#### 单章 Token 粗估

| 模式 | 单章 Token 粗估 |
|------|----------------|
| FAST / 流式模式 | 约 25k-50k tokens/章 |
| 默认 FULL multi-agent | 约 70k-120k tokens/章 |
| FULL + 重写/重规划 | 可能 150k-300k+ tokens/章 |
| legacy 普通生成 | 约 50k-90k tokens/章 |

正文输出本身可能只有 1k-3k tokens，但校验和后处理会反复把章节内容喂给模型，总体 token 被放大很多。

#### 按整本书估算

| 规模 | 章节数 | 默认 FULL 大概 Token |
|------|--------|---------------------|
| 短篇 | 20 章 | 140万-240万 |
| 中篇 | 100 章 | 700万-1200万 |
| 长篇 | 300 章 | 2100万-3600万 |
| 百万字级别 | 400-700 章 | 3000万-8000万+ |

还不包括用户反复改设定、重新生成章节、重新跑卷纲、重新跑文风卡等额外消耗。

#### Token 消耗最大的地方

1. **五重校验**：每章把正文截断到最多 8000 字符，分别跑 OOC、逻辑、伏笔、设定、节奏五个模型调用，通常是最大头。
2. **后处理**：人物提取、伏笔提取、时间线提取、事件提取又会读一遍章节内容，额外 4 次调用。
3. **质量评分和自审**：又分别读取最多 6000 字符的章节内容。
4. **AuthorAgent 检查点**：默认开启，会增加若干结构化模型调用。
5. **章纲重复生成**：向导里已经生成过章纲，正文生成时 multi-agent 又生成一次，浪费 token 且可能破坏规划一致性。

### 2.4 当前 Token 管理存在的问题

1. `TokenBudgetService` 默认按 8192 做预算，很多地方没有读取真实模型上下文窗口。
2. `maxTokens` 在项目里更像"输出 token 上限"，但被拿来推导上下文预算，概念混在一起。
3. `TracingChatModel` 和 `LlmCallRecord` 已经写了，但没有真正接到主要调用链里，目前看不到完整 per-node token 账单。
4. 带文风卡的写作链调用了 `getModel(llmConfigId)`，没有像普通写作链那样强制 `minMaxTokens=16384`，长章节可能更容易输出不够或被截断。
5. 多 Agent 批量生成时，后处理是异步的，但 batch 的等待逻辑主要委托给 legacy service，可能导致下一章开始时上一章记忆还没完全入库。

### 2.5 改进建议

#### P0：先修"按确认章纲写"

让 multi-agent 在生成正文前读取当前章节已确认章纲，并作为硬约束。否则全书规划会漂。

#### P1：加 Token 记账

每次模型调用记录：`novelId`、`chapterNum`、`nodeName`、`modelName`、`inputTokens`、`outputTokens`、`totalTokens`、`durationMs`、`retryCount`、`success/failed`。可以落 Redis + MySQL。

#### P2：拆成三种生成档位

| 档位 | 用途 |
|------|------|
| 草稿模式 | 章纲 + 正文 + 摘要，低成本快速生成 |
| 标准模式 | 加自审、质量评分、轻后处理 |
| 精修模式 | 加完整五重校验、AuthorAgent、事件图谱 |

比较合理的是：普通章节用标准模式，关键章/卷尾/终章用精修模式。

#### P3：合并校验和后处理调用

现在五重校验是 5 次调用，后处理是 4 次调用。可以改成：

- **综合校验 Agent**：一次输出 OOC、逻辑、伏笔、设定、节奏评分。
- **综合提取 Agent**：一次输出人物、伏笔、时间线、事件。

这样每章可以少 6-7 次模型调用，token 成本能明显下降。

#### P4：提高单章字数，减少固定开销

现在配置里章节目标是 1000-1500 字，对长篇来说太短。建议改成：

- 轻小说/短篇：1500-2500 字/章
- 网文长篇：2500-4000 字/章
- 高成本模型：尽量 3000 字以上/章

### 2.6 总体判断

能写完整小说，但现在更适合"自动生成完整初稿"，不适合无人值守高质量完本。真正影响完本质量的是：

1. 默认 multi-agent 没有严格使用已确认章纲
2. Token 用量偏高，FULL 模式每章大约 70k-120k tokens
3. 缺少实际 token 账单统计
4. 校验和后处理调用太多，成本会放大
5. 长篇一致性依赖摘要/状态/RAG，但还缺少"全书级复盘与终章收束"机制

如果把"使用确认章纲""token 记账""生成档位""合并校验/后处理"这四点修掉，它就比较像一个真正可用的长篇小说自动生产系统了。

---

## 三、多章连贯性深度分析（a3）

### 3.1 问题总览

体感非常符合当前代码结构：

- **单章看着舒服**：WritingAgent + 文风卡 + 自审在优化"当前章节"
- **多章连贯性差**："确认章纲、上一章结尾、记忆更新、长线事件"没有形成硬约束闭环

读起来就像：**每章都是一个不错的镜头，但镜头之间剪辑错位。**

### 3.2 Agent 编排的问题

#### 问题 A：multi-agent 会重新生成章纲，容易不按确认过的章纲写

这是最关键的问题。

向导流程里，`WizardServiceImpl.confirmChapterOutlines()` 会把章纲保存到 `novel_chapter.chapter_outline`。但批量生成时，`WizardServiceImpl.batchGenerateChapters()` 只是筛选出"有章纲但没正文"的章节，然后调用 `chapterGenerationService.generateChapter(genRequest)`。

默认引擎是 multi-agent，会走：`arc_check → outline → clue_planning/character_planning → world_validate → idea_select → writing → self_review → validation → quality_scoring → audit_router → summary → post_process`

问题在 `OutlineNode.apply()`：它没有优先读取数据库里已经确认的 `chapter_outline`。确认的章纲目前只是"允许进入批量生成"的条件，但不是正文生成的硬约束。

这会导致：

- 第 1 章按 A 章纲写
- 第 2 章虽然确认过 B 章纲，但 multi-agent 又临时生成了一个 B'
- 第 3 章又生成 C'
- 单章都顺，但全书线索不接

**建议修复**：`OutlineNode` 应该先读 `NovelChapter`：

```java
NovelChapter confirmed = chapterMapper.selectByNovelIdAndChapterNum(
    state.getNovelId(), state.getChapterNum()
);
if (confirmed != null && hasText(confirmed.getChapterOutline())) {
    ChapterPlan plan = parseConfirmedOutline(confirmed);
    return state.toBuilder()
        .chapterPlan(plan)
        .outlineContext(outlineContext)
        .build();
}
```

只有没有确认章纲时，才调用 LLM 生成新章纲。

#### 问题 B：上一章 hook 基本没有真正生效

`ContextBuilderService` 里明确想做"上一章结尾钩子"：

```java
if (prevChapter != null && prevChapter.getHook() != null) {
    sb.append("【上一章结尾钩子（本章开头必须直接承接这个悬念）】");
}
```

但 hook 基本没有被持久化。`ChapterGenerationServiceImpl.parseOutlineJson()` 能解析 hook，但 `persistOutlineToDatabase()` 只保存了 `chapterOutline` 和 `implementationIdea`，没有保存 `hook`。

multi-agent 的 `OutlineNode` 也解析了 hook，但 `PostProcessNode` 保存章节时只用了 `plan.getTitle()`、`plan.getSummary()`、`result.getFinalContent()`，也没有保存 `plan.getHook()`。

结果就是：系统设计上有"上一章钩子承接"，实际数据库里大概率一直是 `null`。下一章生成时根本拿不到上一章结尾悬念。

**典型现象**：每章结尾都像有钩子，下一章开头却不直接接，读起来像一组漂亮片段而不是连续小说。

**建议修复**：

1. 保存章纲时写入 hook：`chapter.setHook(outlineResponse.getHook())`
2. 正文写完后提取"实际章尾钩子"，保存到 `novel_chapter.hook` 或新增字段：

```json
{
  "fingerprint": "...",
  "ending_hook": "...",
  "next_chapter_must_start_with": "...",
  "open_loops": ["..."]
}
```

#### 问题 C：校验主要校验"本章"，不是校验"章与章之间"

`ValidationServiceImpl` 的逻辑校验 prompt 只给了"待检测内容 = 当前章节正文"和"章节大纲 = 当前章节大纲"，没有给：

- 上一章最后 500-1000 字
- 上一章最终状态
- 上一章 unresolved hook
- 当前章开头是否承接上一章
- 当前章是否推进了已确认章纲
- 下一章是否还有接续空间

所以校验系统能判断"这一章本身逻辑通不通"，但判断不了"这一章接不接上一章"。这就是"单章舒服、多章断裂"的核心机制。

**建议新增专门节点 `continuity_validate`**，放在 writing 后、self_review 前或 validation 前。输入：

- 上一章最后 800 字
- 上一章 plotFingerprint
- 上一章 hook
- 当前章前 800 字
- 当前章确认章纲
- StoryState 当前状态

输出：

```json
{
  "passed": true,
  "opening_continues_previous": true,
  "state_consistent": true,
  "unresolved_breaks": [],
  "required_rewrite_instruction": ""
}
```

不通过就只重写开头或重写本章。

### 3.3 小说创建流程的问题

当前流程：生成卷纲 → 保存小说和卷 → 异步生成卷级编排 → 生成每卷章纲 → 用户确认章纲 → 批量生成正文。

#### 问题 A：卷级编排是异步生成的，用户可能还没等它完成就开始生成章纲

`WizardServiceImpl.saveNovelWithArcs()` 里用 `CompletableFuture.runAsync()` 异步生成卷级编排，但保存小说接口马上返回。如果前端立刻进入"生成卷章纲"，这时 `ArcOrchestration` 可能还没生成完。

虽然 `ArcOrchestrationNode` 有 fallback，但向导里生成章纲用的是 `chapterGenerationService.generateOutline(genRequest)`，直接委托 legacy，并不真正经过 multi-agent 的 `arc_check` 节点。

结果：前期章纲可能是没有卷级编排约束的，后面正文生成时 multi-agent 又读取到另一个卷级编排，二者冲突。

**建议修复**：小说保存后不要直接允许生成章纲，应该增加状态：

```
CREATED → ARC_ORCHESTRATING → ARC_READY → CHAPTER_OUTLINE_READY → CHAPTER_CONFIRMED → GENERATING
```

只有 `ARC_READY` 后才能生成章纲。或者更简单：`saveNovelWithArcs()` 同步生成当前第一卷编排，其他卷异步。

#### 问题 B：用户确认章纲后，没有反向更新卷级编排

系统里同时有两套规划：

1. `ArcOrchestration`：卷级编排
2. `NovelChapter.chapter_outline`：用户确认章纲

但它们没有一个明确的主从关系。如果用户修改了第 5 章章纲，系统并不会更新第 5 章的卷级编排、调整第 6 章承接、调整后续伏笔计划、重新计算节奏曲线。

**建议明确规则**：用户确认章纲是最终真相，确认后做一次 `reconcileArcPlan()`：

```
confirmed chapter outlines
→ recompute per-chapter keyEvent / hookDirection / cluePlan / rhythmIntensity
→ save ArcOrchestration v2
```

#### 问题 C：创建流程缺少"全书章节圣经"

现在有总纲、卷纲、章纲，但缺一个真正用于长篇连续性的中间层：**章节圣经 / Chapter Bible**。

建议新增一个持久化表或 Mongo 文档：

```json
{
  "chapterNum": 12,
  "mustStartFrom": "上一章主角刚收到师父失踪信",
  "mustHappen": ["进入黑市", "发现玉牌同源"],
  "mustEndWith": "黑市掌柜认出玉牌",
  "stateDelta": {
    "location": "青石城黑市",
    "protagonistGoal": "查明玉牌来源",
    "knownFactsAdded": ["玉牌来自三十年前的云镜宗"]
  },
  "openLoops": ["师父为何失踪", "掌柜为何认得玉牌"]
}
```

正文生成时，不要只传"章纲"，而是传：上一章 endState、本章 mustStartFrom、本章 mustHappen、本章 mustEndWith、下一章 bridge。

### 3.4 外部记忆系统的问题

这一块问题更大。项目文档里写的是"五层/六层记忆架构"，但实际 multi-agent 主链路里，很多记忆没有真正稳定工作。

#### 问题 A：multi-agent 保存时没有保存完整 ChapterSummary / RAG 摘要

`SummaryNode` 只把摘要解析成一个 `fingerprint`，然后放到 `finalResult.summary`。到 `PostProcessNode` 保存时：

```java
chapterSaveService.saveGenerationResult(
    ...,
    result.getSummary(),
    null    // ← 没有传 ChapterSummary 对象
);
```

如果 summary 是 null，就不会保存章节摘要向量。结果是：

- multi-agent 生成出来的章节，可能没有进入 RAG
- Mongo 的 ChapterSummary 不完整
- 后续 `RagTool.recallHistory()` 能检索到的东西很少甚至没有
- 长篇记忆系统名义存在，实际断链

**建议修复**：`SummaryNode` 不应该只解析 fingerprint，应该解析完整结构：

```json
{
  "fingerprint": "...",
  "core_events": [],
  "characters": [],
  "locations": [],
  "keywords": [],
  "relations": [],
  "ending_hook": "...",
  "state_delta": {}
}
```

然后 `PostProcessNode` 构建 `ChapterSummary` 传给 `ChapterSaveService`。

#### 问题 B：后处理是异步的，批量生成下一章时记忆可能还没更新完

`PostProcessNode` 调用 `postProcessService.postProcess(...)`，而 `ChapterPostProcessService.postProcess()` 是 `@Async("postProcessExecutor")`，会立刻返回，后处理实际在另一个线程跑。

但 `WizardServiceImpl.batchGenerateChapters()` 生成一章后调用 `chapterGenerationService.waitForPostProcessing()`，在 multi-agent 下实际委托给 legacy service 的 `waitForPostProcessing()`，并没有等待 `PostProcessNode` 里的异步后处理完成。

所以批量生成时可能发生：

```
第1章正文保存 → 第1章人物/伏笔/事件还没提取完 → 第2章已经开始构建上下文
→ 第2章拿到的是旧 StoryState / 旧 CharacterState / 旧 ClueBook
```

**建议修复**：把后处理改成可等待的 `CompletableFuture<Void>`，然后批量生成必须：

```
generate chapter N → wait memory commit N done → generate chapter N+1
```

流程上应明确为：

```
write → save_mysql → save_summary/rag → update_story_state
→ extract_character/clue/event → memory_commit_done → next chapter
```

不要让下一章在记忆提交前开始。

#### 问题 C：processedChapters 会导致重新生成章节后记忆不更新

`ChapterPostProcessService` 里有：

```java
private final ConcurrentHashMap.KeySetView<String, Boolean> processedChapters
```

然后：

```java
if (!processedChapters.add(novelId + ":" + chapterNum)) {
    return;
}
```

这意味着：同一 JVM 生命周期内，第 N 章后处理成功过一次，之后重新生成第 N 章，它可能直接跳过后处理。结果是 MySQL 章节内容变了，但人物状态、伏笔、事件图谱、StoryState 还是旧版本。

**建议修复**：后处理幂等不能只按 `novelId:chapterNum`，至少要加内容 hash：`novelId:chapterNum:contentHash`。或者每次重生成章节时先删除该章旧的事件、旧的自动提取伏笔、旧的 summary，再重新提取。

#### 问题 D：所谓"上一章结尾"没有传正文结尾，只传摘要/章纲

现在上下文里近章参考是 `selectRecentOutlines(novelId, chapterNum, 5)`，SQL 查的是前几章的章纲和 hook，不是正文结尾。

但读者的连贯感主要来自：上一章最后发生了什么、最后一句/最后一个动作是什么、本章第一段是否接住那个动作。只靠 80 字章纲摘要是不够的。

**建议修复**：新增查询上一章正文尾部，构建上下文时注入：

```
【上一章最后 800 字，本章开头必须直接承接】
```

这对连续性提升会非常明显。

#### 问题 E：RAG 是工具调用模式，但没有强制 Agent 使用

`WritingAgent` 有工具包括 `RagTool.recallHistory()`，但系统 prompt 只是让模型"可能调用工具"，没有强制在关键场景调用。长篇连续性不能完全靠模型自觉。

**建议改成两层**：

**近距离连续性：强制 push**

每章都自动注入：上一章最后 800 字、上一章 fingerprint、上一章 unresolved hook、当前 StoryState、当前确认章纲。

**远距离回忆：规则触发 RAG**

出现这些情况时自动检索：回收伏笔、旧人物登场、地点重返、物品再次出现、`chapterNum - buryChapter > 10`、当前章纲提到早期事件。

### 3.5 为什么现在会"怪异"

局部文学质量高，叙事状态连续性低。具体原因：

1. WritingAgent 很会把当前章写得舒服
2. 文风卡让语言更顺
3. 自审/质量评分也偏向当前章
4. 但当前章不一定遵守确认的章纲
5. 上一章 hook 没有稳定保存
6. 上一章实际结尾没有注入
7. 后处理记忆可能没来得及更新
8. multi-agent 没有完整保存 RAG summary
9. 校验没有专门校验"章与章之间的接缝"

### 3.6 优先改进的 6 个点

| 优先级 | 改进项 | 说明 |
|--------|--------|------|
| **P0** | 让 multi-agent 使用确认章纲 | 改 `OutlineNode`，优先读取 `novel_chapter.chapter_outline`，有确认章纲就不重新生成 |
| **P0** | 保存并使用 hook | 保存章纲 hook：`chapter.setHook(outlineResponse.getHook())`；正文写完后再提取实际 ending hook |
| **P0** | 批量生成必须等待记忆提交 | 改成：generate → save summary → save rag → update story state → extract character/clue/event → commit done → next chapter |
| **P1** | multi-agent PostProcessNode 要传 ChapterSummary | 现在传了 null，RAG 记忆链断了。应该构建完整 ChapterSummary |
| **P1** | 上下文注入上一章正文结尾 | 在 `ContextBuilderService.buildWritingContext()` 增加"上一章最后 800 字" |
| **P1** | 加跨章连续性校验节点 | 新增 `ContinuityValidationNode`，专门校验章间承接、人物位置、时间、悬念、章纲遵守、结尾接点 |

### 3.7 更合理的长篇生成闭环

建议最终流程改成：

```
创建小说
→ 生成卷纲
→ 生成卷级编排
→ 生成章节圣经 Chapter Bible
→ 用户确认/编辑
→ 锁定本卷规划
→ 第 N 章生成：
    读取：
      - 确认章纲
      - 上一章最后 800 字
      - 上一章 hook / open_loops
      - StoryState
      - CharacterState
      - ClueBook
      - 相关历史事件 / RAG
    生成：
      - continuity_bridge
      - writing
      - self_review
      - continuity_validate
      - setting/character/clue validate
      - save
      - memory_commit
    确认 memory_commit 后：
    → 第 N+1 章
```

### 3.8 最终判断

这三个点都有问题，而且是同一个方向的问题：**系统把"写好单章"做得比较重，把"章节之间的状态传递"做得不够硬。**

最应该先修的不是再加 Agent，而是把这几个信息变成强约束：

1. **确认章纲必须被使用**
2. **上一章实际结尾必须被注入**
3. **上一章 hook 必须保存并强制承接**
4. **记忆后处理必须完成后才能生成下一章**
5. **multi-agent 必须保存完整 summary/RAG**
6. **新增跨章连续性校验**

修完这些，"每章都好看但连起来怪"的感觉会明显下降。

---

## 附录：三份报告核心主题对照

| 维度 | a1：项目整体审计 | a2：写书能力与 Token | a3：多章连贯性 |
|------|-----------------|---------------------|---------------|
| 核心关注 | 可运行性、安全、工程规范 | 生成闭环、Token 成本、档位设计 | 章间状态传递、记忆连续性 |
| 最大问题 | 缺 DB schema + 权限隔离 | 确认章纲未被使用 + Token 偏高 | 确认章纲/hook/记忆均断链 |
| 共识修复 | P0 先能跑起来 | P0 先按确认章纲写 | P0 确认章纲 + hook + 记忆同步 |
| 一句话 | 先补齐基础设施再扩展 | 能写初稿，不能无人值守完本 | 单章好看，多章剪辑错位 |
