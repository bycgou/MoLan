# 墨澜 (MoLan) 项目学习指南

> AI 驱动的百万字长篇小说创作引擎 — 从零到精通

---

## 目录

- [一、项目概览](#一项目概览)
- [二、项目评价](#二项目评价)
- [三、前置知识储备](#三前置知识储备)
- [四、学习路线（推荐顺序）](#四学习路线推荐顺序)
- [五、GraphRunner 深度学习](#五graphrunner-深度学习)
- [六、核心概念速查表](#六核心概念速查表)
- [七、推荐阅读顺序（文件清单）](#七推荐阅读顺序文件清单)

---

## 一、项目概览

### 1.1 墨澜是什么

墨澜是一个面向**百万字长篇网文**的 AI 创作系统。它不是简单的"prompt → LLM → 输出"单链路工具，而是一套**多 Agent 图编排引擎 + 六层记忆架构 + 五重校验体系**的完整创作流水线，让 AI 在写第 300 章时仍然精确记得第 3 章埋的伏笔、角色的性格底线、以及上一章结尾的悬念。

### 1.2 项目结构

项目包含 **4 个子项目**：

| 子项目 | 技术栈 | 定位 |
|--------|--------|------|
| `backend/` | Java 17 + Spring Boot 3.2 + LangChain4j | 主后端，生产级 |
| `frontend/` | Vue 3 + Element Plus + Pinia | 前端 v1（旧版） |
| `frontend-v2/` | Next.js 16 + React 19 + shadcn/ui + Tailwind | 前端 v2（主力） |
| `moyun-ts/` | TypeScript + LangChain.js + Drizzle ORM + SQLite | 轻量重写版（进行中） |

### 1.3 技术栈

| 层 | 技术 |
|----|------|
| **后端** | Java 17, Spring Boot 3.2, LangChain4j, MyBatis-Plus, Spring Security, JWT |
| **前端 (v1)** | Vue 3, TypeScript, Element Plus, Pinia, Vite |
| **前端 (v2)** | Next.js 16, React 19, shadcn/ui, Tailwind CSS, Zustand, SWR |
| **TypeScript 引擎** | LangChain.js, Drizzle ORM, better-sqlite3, MiniSearch |
| **数据库** | MySQL 8, MongoDB, Redis Stack (向量索引) |
| **AI** | OpenAI 兼容 API, LangChain4j / LangChain.js, MCP SDK |
| **部署** | Docker, Nginx |

### 1.4 核心亮点速览

- **多 Agent 图编排引擎 (GraphRunner)** — 自研 DAG 执行引擎，支持条件路由、扇出并行、检查点拦截、重试容错、多模式执行
- **AuthorAgent "人类作者"在环** — 在关键决策点模拟人类作者判断（APPROVE/REVISE/REJECT/REDIRECT），预筛优化省 Token
- **五重并行校验 + 加权评分** — OOC / 逻辑 / 伏笔 / 设定 / 节奏，可配权重和严重度
- **反 AI 味三道防线** — 生成时约束 + 生成后检测 + 自动修正
- **六层记忆架构** — 故事快照→伏笔账本→角色档案→滑动窗口→事件图谱→RAG向量
- **12 种体裁深度模板** — 每种体裁 5-8 个 Markdown 写作指南
- **文风卡系统** — 分析参考文本风格，生成时注入，生成后检测偏离度
- **卷级编排 (Arc Orchestration)** — 整卷的章节大纲、伏笔节奏、人物动线规划
- **MCP 协议集成** — 同时支持 Client（接入外部工具）和 Server（暴露能力）

---

## 二、项目评价

### 2.1 总体评价：★★★★☆（4/5）

架构设计 90 分，工程落地 70 分——思路远超同类，但细节打磨不够。

### 2.2 亮点

#### 架构设计有深度，不是玩具项目

最令人印象深刻的是 **GraphRunner 图编排引擎**。这不是简单的链式调用，而是一个真正的 DAG 执行引擎，支持条件路由、扇出并行、检查点拦截、多模式执行、节点级重试容错。这种设计在开源 AI 创作工具中非常罕见，更接近 LangGraph 的思路，但它是自研的、为小说创作场景深度定制的。

#### 六层记忆架构解决了真问题

| 设计决策 | 评价 |
|----------|------|
| 精确查询优先，语义检索兜底 | 非常正确，避免了 RAG 的不确定性 |
| Push-Pull 二分 | 前四层自动注入、后两层按需调用，兼顾效率与灵活性 |
| 写时更新，读时组装 | 避免了实时组装的性能开销 |
| 渐进式记忆（近精确远模糊） | 符合人类记忆规律，也节省 Token |

#### "反 AI 味"三道防线是差异化竞争力

大多数 AI 写作工具只管生成，不管"AI 味"。墨澜的三道防线（生成时约束 → 生成后检测 → 自动修正）是真正面向网文场景的实用设计。

#### 五重并行校验 + 加权评分体系

OOC / 逻辑 / 伏笔 / 设定 / 节奏五个维度并行校验，每个维度可配权重和严重度（FATAL / WARNING），FATAL 不过直接 REPLAN。这种可配置的分级校验比简单的"通过/不通过"要成熟得多。

#### AuthorAgent 预筛优化

不是每个决策都需要 LLM，结构完整就跳过、质量够高就放行，既保留了人类把控力，又节省了 Token。

### 2.3 不足

#### RAG 模块存在严重问题

项目自己的 `rag-refactor-plan.md` 列出了 18 个问题，其中 3 个 P0：
- BM25 正则回退存在注入风险
- `calculateRelevanceScore` 中文处理有缺陷（中文网文系统中文 RAG 质量差，很讽刺）
- `saveVector` 双写不同步导致数据永久丢失

#### 存在死代码和重复代码

`backend-dead-code-analysis.md` 揭示了：
- 5 个死类（`OutlineAgentRequest`、`SummaryAgentRequest`、`WritingAgentRequest`、`RoutingDecision`、`AgentMetrics`）
- 重复 Controller（`EnhancedRhythmController` 和 `RhythmController`）
- 两套章节版本管理并行存在

#### 测试覆盖不均匀

- GraphRunner 核心模块测试较好
- Service 层测试薄弱
- Controller 层零测试
- 前端零测试

#### 前端 v1 和 v2 并存，定位不清

三套前端并存（frontend/、frontend-v2/、moyun-ts/packages/web/），对新人来说很困惑。

#### TypeScript 重写版完成度存疑

更像概念验证而非可用的替代方案。

#### 缺乏可观测性

`AgentMetrics` 写了但从未被注入使用，没有结构化日志规范、没有 tracing。

### 2.4 架构决策评价

| 决策 | 评价 | 理由 |
|------|------|------|
| 自研 GraphRunner 而非用 LangGraph | ✅ 正确 | Java 生态没有成熟的 LangGraph 等价物 |
| MySQL + MongoDB 双数据库 | ⚠️ 有争议 | 增加运维复杂度，但对此场景合理 |
| Redis Stack 做向量索引 | ⚠️ 有争议 | 简化部署，但 Redis 向量搜索能力有限 |
| LangChain4j 而非 Spring AI | ✅ 正确 | LangChain4j 更成熟 |
| SSE 流式输出 | ✅ 正确 | 适合 LLM 逐字生成场景 |
| MCP 双向集成 | ⭐ 前瞻 | 为未来 Agent 生态留了接口 |

### 2.5 与同类项目对比

| 维度 | 墨澜 | 典型 AI 写作工具 |
|------|------|-----------------|
| 架构深度 | 多 Agent 图编排 + 六层记忆 | 单链路 prompt → LLM |
| 长程一致性 | 五重校验 + 伏笔追踪 + 故事快照 | 几乎没有 |
| 反 AI 味 | 三道防线 | 通常没有 |
| 体裁支持 | 12 种深度模板 | 通常 1-2 种通用模板 |
| 部署复杂度 | MySQL + MongoDB + Redis + Java | 通常一个 Python 进程 |
| 上手门槛 | 高 | 低 |

### 2.6 最值得学习的三个设计

1. **GraphRunner 图编排引擎** — 条件路由 + 扇出并行 + 检查点拦截 + 重试容错
2. **六层记忆架构** — Push-Pull 二分、精确查询优先语义检索兜底
3. **AuthorAgent 预筛优化** — 不是每个决策都需要 LLM

---

## 三、前置知识储备

### 第一层：编程语言与框架基础

1. **Java 17** — record、sealed class、var、Stream API
2. **Spring Boot 3.2** — 自动配置、依赖注入、Web MVC、Data Redis/MongoDB
3. **MyBatis-Plus** — ORM 映射、Mapper XML
4. **React 19 + Next.js App Router** — Server/Client Component、路由、SSR
5. **TypeScript** — 类型系统、泛型、装饰器

### 第二层：AI/LLM 开发框架

6. **LangChain4j**（Java）— AI Service、@Tool、ChatMemory、Chain
7. **LangChain.js**（TypeScript）— 同上概念的 JS 版
8. **RAG（检索增强生成）** — 向量嵌入、相似度检索、RRF 融合
9. **Agent 模式** — ReAct、Tool Calling、Multi-Agent 编排

### 第三层：基础设施

10. **MySQL 8** — 关系型数据存储
11. **MongoDB** — 文档型数据存储（故事状态、事件图谱）
12. **Redis Stack** — 缓存 + 向量索引（FT.SEARCH）
13. **Docker Compose** — 一键启动基础设施

---

## 四、学习路线（推荐顺序）

### 🟢 阶段一：跑起来（1-2 天）

**目标：让项目在本地运行，理解用户视角**

1. 阅读 `README.md`，理解项目定位
2. 用 Docker Compose 启动 MySQL + MongoDB + Redis：
   ```bash
   cp .env.example .env    # 填入密码和 JWT_SECRET
   docker-compose up -d
   ```
3. 启动后端：`cd backend && mvnw.cmd spring-boot:run`
4. 启动前端 v2：`cd frontend-v2 && pnpm install && pnpm dev`
5. 访问 http://localhost:3000，走一遍完整流程：
   - 注册/登录 → 创建小说 → 向导流程 → 生成章纲 → 生成章节 → 查看角色/伏笔/节奏

**关键理解点：** 用户体验的完整链路是什么？每一步对应后端的哪个 API？

---

### 🟡 阶段二：理解数据模型（2-3 天）

**目标：搞清楚数据怎么存的，为什么用两种数据库**

阅读顺序：

1. **MySQL 实体**（结构化数据）— `backend/src/main/java/com/novel/system/entity/mysql/`
   - `NovelBase` — 小说基本信息
   - `NovelChapter` / `NovelChapterVersion` — 章节与版本
   - `NovelArc` / `ArcSummary` — 故事弧与摘要
   - `ClueBook` — 伏笔账本
   - `StyleCard` / `StyleSample` — 文风卡
   - `LlmConfig` — LLM 配置
   - `WorldClock` — 世界时钟

2. **MongoDB 实体**（非结构化/动态数据）— `backend/src/main/java/com/novel/system/entity/mongo/`
   - `StoryState` — 故事快照（核心枢纽）
   - `CharacterState` — 角色状态
   - `EventNode` — 事件图谱节点
   - `NovelGraph` — 角色关系图谱
   - `ChapterSummary` — 章节摘要
   - `ArcOrchestration` — 卷级编排

3. **Mapper XML** — `backend/src/main/resources/mapper/`，看 SQL 怎么写的

**关键理解点：** 为什么 MySQL 存结构化数据（伏笔、章节），MongoDB 存动态数据（故事状态、事件图谱）？这是"精确查询优先，语义检索兜底"设计哲学的体现。

---

### 🟠 阶段三：理解核心 AI 架构（5-7 天）⭐ 最重要

这是项目的灵魂，需要花最多时间。详见 [第五章 GraphRunner 深度学习](#五graphrunner-深度学习)。

---

### 🔴 阶段四：理解六层记忆架构（3-4 天）

**目标：理解系统怎么做到"写第 300 章还记得第 3 章"**

按层级阅读：

| 层级 | 服务 | 存储 | 注入方式 |
|------|------|------|----------|
| 第 1 层：故事快照 | `StoryStateServiceImpl` | MongoDB | Push（全量注入） |
| 第 2 层：伏笔账本 | `ClueBookServiceImpl` | MySQL | Push（SQL 精确查询） |
| 第 3 层：角色档案 | `CharacterStateServiceImpl` | MongoDB | Push（按角色注入） |
| 第 4 层：滑动窗口 | `ContextBuilderService` | MySQL | Push（近章摘要） |
| 第 5 层：事件图谱 | `EventGraphService` + `RuleDrivenRetrievalService` | MongoDB | Pull（规则触发） |
| 第 6 层：RAG 向量 | `RagServiceImpl` + `RedisVectorServiceImpl` | Redis | Pull（Agent @Tool） |

**关键理解点：**
- **Push-Pull 二分**：前四层自动注入上下文，后两层由 Agent 按需调用
- **精确查询优先，语义检索兜底**：结构化数据用 SQL/MongoDB 查，只有"回忆早期细节"才用 RAG
- **写时更新，读时组装**：每章生成后，后处理流水线自动更新所有状态

---

### 🟣 阶段五：理解 Controller → Service → AI 的调用链（2-3 天）

**目标：理解一个 HTTP 请求怎么变成一章小说**

以"生成章节"为例，追踪完整调用链：

1. `StreamingChapterController` — SSE 流式入口
2. `ChapterController` — REST 入口
3. `ChapterService` → `ChapterGenerationServiceImpl`
4. `MultiAgentChapterGenerationService` — 选择生成引擎
5. `MultiAgentNovelService` — 调用 GraphRunner
6. GraphRunner 执行图 → 各节点 → 后处理

同时阅读其他 Controller 理解 API 全貌：
- `NovelController` — 小说 CRUD
- `CharacterController` — 角色管理
- `ClueController` — 伏笔管理
- `WizardController` — 向导流程

---

### 🔵 阶段六：理解前端架构（2-3 天）

**目标：理解前端怎么和后端交互，SSE 流式输出怎么实现**

阅读顺序：

1. `frontend-v2/lib/api.ts` — API 客户端基础（认证、错误处理）
2. `frontend-v2/lib/api/index.ts` — 各模块 API 封装
3. `frontend-v2/hooks/useSSE.ts` — SSE 流式 Hook（核心！）
4. `frontend-v2/stores/auth.ts` — Zustand 认证状态
5. `frontend-v2/hooks/useAuth.ts` — 认证 Hook

关键页面：
- `WizardSteps.tsx` — 向导流程（最复杂）
- `StreamViewer.tsx` — 流式输出展示
- `app/(app)/novel/[id]/page.tsx` — 小说详情页

---

### ⚪ 阶段七：理解 TypeScript 重写版（1-2 天）

**目标：理解 moyun-ts 的架构差异和设计取舍**

阅读顺序：

1. `packages/core/src/db/schema.ts` — Drizzle ORM + SQLite 替代 MySQL + MongoDB
2. `packages/core/src/ai/graph/graph-runner.ts` — TS 版 GraphRunner
3. `packages/core/src/ai/nodes/` — TS 版图节点
4. `packages/core/src/ai/rag/rag.service.ts` — MiniSearch BM25 替代 Redis 向量
5. `packages/cli/src/index.ts` — CLI 入口

**关键差异：**
- SQLite 替代 MySQL + MongoDB（轻量部署）
- MiniSearch BM25 替代 Redis Stack 向量索引
- LangChain.js 替代 LangChain4j
- 无 Spring Boot 框架开销

---

### 🟤 阶段八：特色系统深入（2-3 天）

按兴趣选读：

1. **反 AI 味系统** — `AntiAICliches.java` + `StyleCheckerService`
2. **文风卡系统** — `StyleCardService` + `StyleTextAnalyzer`
3. **体裁模板系统** — `GenreServiceImpl` + `resources/genres/`（12 种体裁 Markdown）
4. **卷级编排** — `ArcOrchestrationServiceImpl`
5. **MCP 集成** — `mcp/client/` + `mcp/server/`
6. **节奏分析** — `RhythmServiceImpl` + `StrandRhythmServiceImpl`

---

### 学习时间线总览

| 阶段 | 内容 | 建议时间 |
|------|------|----------|
| 阶段一 | 跑起来 | 1-2 天 |
| 阶段二 | 数据模型 | 2-3 天 |
| 阶段三 | 核心 AI 架构 ⭐ | 5-7 天 |
| 阶段四 | 六层记忆 | 3-4 天 |
| 阶段五 | 调用链 | 2-3 天 |
| 阶段六 | 前端架构 | 2-3 天 |
| 阶段七 | TS 重写版 | 1-2 天 |
| 阶段八 | 特色系统 | 2-3 天 |
| **总计** | | **约 3-4 周** |

---

## 五、GraphRunner 深度学习

### 5.1 全景图

```
┌─────────────────────────────────────────────────────────────────┐
│                        GraphRunner 执行全景                       │
│                                                                 │
│  arc_check ──→ outline ──→ clue_planning ──┐                   │
│                         character_planning ─┘ (扇出并行)         │
│                              │                                  │
│                              ▼                                  │
│                        idea_select                               │
│                              │                                  │
│                              ▼                                  │
│                        writing ──→ self_review                  │
│                              │                                  │
│                              ▼                                  │
│                   validation(5维并行) ──→ quality_scoring        │
│                              │                                  │
│                              ▼                                  │
│    ┌──→ audit_router ──→ PASS ──→ summary ──→ post_process ──→ END
│    │        │                                                    │
│    │        ├──→ REWRITE ──→ writing (重写)                      │
│    │        └──→ REPLAN ──→ outline (重规划)                    │
│    │                                                             │
│    └── AuthorAgent 在 outline/writing/audit_router 三个检查点拦截 │
└─────────────────────────────────────────────────────────────────┘
```

### 5.2 第 1 层：理解图的定义 — GraphDefinition

**文件**：`backend/src/main/java/com/novel/system/ai/graph/GraphDefinition.java`（176 行）

这是整个引擎的**骨架**，定义了图的拓扑结构。用 Builder 模式构建，包含 6 种核心概念：

| 概念 | 数据结构 | 作用 | 对应方法 |
|------|----------|------|----------|
| **简单边** | `Edge(from, to, modes)` | A 执行完走 B | `builder.edge("a", "b")` |
| **条件边** | `ConditionalEdge(from, to, predicate, modes)` | 根据状态决定走哪 | `builder.conditionalEdge("a", "b", state -> "PASS".equals(state.getAuditDecision()))` |
| **扇出组** | `FanOutGroup(source, targets, mode, mergeStrategy)` | 一个节点扇出到多个并行节点 | `builder.fanOut("outline", List.of("clue_planning", "character_planning"), Mode.FULL)` |
| **扇入组** | `FanInGroup(sources, target, mode)` | 多个并行节点汇合到一个节点 | `builder.fanIn(List.of("clue_planning", "character_planning"), "idea_select", Mode.FULL)` |
| **检查点** | `Set<String> checkpoints` | AuthorAgent 在这些节点前拦截 | `builder.checkpoint("outline")` |
| **重试策略** | `retryPolicies + fallbackNodes` | 节点失败后重试几次，重试耗尽走哪个降级节点 | `builder.retryPolicy("writing", 2, "summary")` |

**关键路由逻辑**：

```java
public String getNextNode(String currentNode, NovelAgentState state, Mode mode) {
    // 1. 优先检查条件边（first match wins）
    for (ConditionalEdge ce : conditionalEdges) { ... }
    // 2. 再检查简单边
    for (Edge edge : edges) { ... }
    // 3. 都没匹配 → END
    return END;
}
```

**学习要点**：
- 条件边优先级高于简单边——这就是 `audit_router` 能根据 `PASS/REWRITE/REPLAN` 走不同路径的原理
- `Mode.FAST` vs `Mode.FULL`：边可以绑定模式，FAST 模式下只走 FAST 边，跳过审计节点
- 所有集合用 `List.copyOf` / `Map.copyOf` 包装——**不可变**，构建后不可修改

**动手练习**：读 `GraphDefinitionTest.java`，7 个测试覆盖了所有核心路由逻辑。

---

### 5.3 第 2 层：理解状态流转 — NovelAgentState

**文件**：`backend/src/main/java/com/novel/system/ai/graph/NovelAgentState.java`（221 行）

这是整个引擎的**血液**，在节点之间流转。用 Lombok `@Builder(toBuilder = true)` 实现**不可变模式**——每次修改都通过 `toBuilder()` 创建新对象。

状态按执行阶段分为 5 个区域：

```
┌──────────────────────────────────────────────────┐
│  请求输入    novelId, chapterNum, userIntent, ...  │
│  SSE输出     sseEmitter                           │
│  上下文      outlineContext, writingContext        │
├──────────────────────────────────────────────────┤
│  Phase 1    chapterPlan, preWriteValidation       │  ← outline 阶段产出
│  Phase 2    selectedIdea, draftContent            │  ← writing 阶段产出
│  Phase 3    auditResult                           │  ← validation 阶段产出
│  Phase 4    finalResult                           │  ← summary 阶段产出
├──────────────────────────────────────────────────┤
│  流程控制    iterationCount, rewriteCount,         │
│             auditDecision, executionTrace, errors  │
│  AuthorAgent authorDirective                      │
└──────────────────────────────────────────────────┘
```

**关键内嵌结构**：

| 结构 | 作用 | 谁写入 | 谁读取 |
|------|------|--------|--------|
| `ChapterPlan` | 章纲（标题、概要、要点、钩子、伏笔操作、角色动线） | OutlineNode, CluePlanningNode, CharacterPlanningNode | WritingNode, ValidationNode |
| `DraftContent` | 初稿（正文、字数、严重度、修订意见） | WritingNode, SelfReviewNode | ValidationNode, AuthorAgent |
| `AuditResult` | 校验结果（5维 + 质量分 + 加权分） | ValidationNode, QualityScoringNode | AuditRouterNode, AuthorAgent |
| `FinalResult` | 最终结果（正文、字数、质量分、摘要） | SummaryNode | PostProcessNode |
| `AuthorDirective` | 作者指令（APPROVE/REVISE/REJECT/REDIRECT） | AuthorAgentImpl | NodeExecutor |

**学习要点**：
- `auditDecision` 是条件路由的关键——值为 `"PASS"` / `"REWRITE"` / `"REPLAN"` / `"MANUAL_REVIEW"`
- `executionTrace` 记录每个节点的执行历史，用于调试和审计
- `nodeRetryCounts` 是节点级重试计数器，防止死循环
- 所有修改都通过 `state.toBuilder().xxx(newValue).build()` —— 不可变状态是理解整个引擎的关键前提

---

### 5.4 第 3 层：理解节点接口 — NovelGraphNode

**文件**：`backend/src/main/java/com/novel/system/ai/graph/NovelGraphNode.java`（37 行）

这是所有图节点的**统一接口**，非常精简：

```java
public interface NovelGraphNode {
    NovelAgentState apply(NovelAgentState state) throws Exception;  // 核心逻辑
    String nodeName();                                                // 节点名
    default boolean shouldSkip(NovelAgentState state) { return false; }  // 是否跳过
    default ErrorStrategy errorStrategy() { return ErrorStrategy.SKIP; } // 错误策略
    default int maxRetries() { return 2; }                               // 最大重试次数
    default NovelAgentState resetState(NovelAgentState state, String source) { return state; } // 重置状态
}
```

**5 个 default 方法的设计意图**：

| 方法 | 何时被调用 | 典型覆盖 |
|------|-----------|----------|
| `shouldSkip` | NodeExecutor 执行前 | OutlineNode：已有章纲则跳过；WritingNode：severity 非 HIGH 则跳过 |
| `errorStrategy` | 节点抛异常时 | WritingNode/OutlineNode 覆盖为 `RETRY`；AuditRouterNode 覆盖为 `FAIL` |
| `maxRetries` | errorStrategy=RETRY 时 | 默认 2 次 |
| `resetState` | 流程回退到该节点时 | OutlineNode：清空 chapterPlan；WritingNode：将 severity 设为 HIGH |

**学习要点**：
- `shouldSkip` 是**幂等性保障**——如果节点已经产出过结果，就不重复执行
- `resetState` 是**回退重做机制**——REPLAN 回到 outline 时，需要清空旧章纲
- 这 5 个方法构成了节点的"生命周期钩子"，理解了它们就理解了节点的执行语义

---

### 5.5 第 4 层：理解执行引擎 — GraphRunner + NodeExecutor

#### GraphRunner — 图的遍历与调度

**文件**：`backend/src/main/java/com/novel/system/ai/graph/GraphRunner.java`（243 行）

核心执行流程：

```
executeFrom(startNode, state, mode)
    │
    ├─ while (currentNode != END):
    │     │
    │     ├─ 1. 防死循环：iterations > 20 → 终止
    │     ├─ 2. 查找节点：nodes.get(currentNode)
    │     ├─ 3. 执行节点：nodeExecutor.execute(node, state, mode)
    │     ├─ 4. 记录追踪：appendTrace
    │     ├─ 5. 扇出检测：isFanOut(currentNode)?
    │     │     ├─ YES → executeFanOut → 跳到扇入目标
    │     │     └─ NO  → 继续
    │     ├─ 6. 路由下一节点：getNextNode(currentNode, state, mode)
    │     │     ├─ 条件边（含重试保护）
    │     │     ├─ 简单边（含重试保护）
    │     │     └─ fallback / END
    │     └─ 7. 重置下一节点状态：nextGraphNode.resetState(state, currentNode)
    │
    └─ 清理检查点 → 返回最终 state
```

**扇出并行执行**：

```java
private NovelAgentState executeFanOut(String sourceNode, NovelAgentState state, Mode mode) {
    List<String> targets = definition.getFanOutTargets(sourceNode, mode);
    MergeStrategy strategy = definition.getFanOutMergeStrategy(sourceNode, mode);

    // 并行执行所有目标节点
    List<CompletableFuture<NovelAgentState>> futures = targets.stream()
            .map(target -> CompletableFuture.supplyAsync(() -> node.apply(currentState), parallelExecutor))
            .toList();

    // 等待所有结果（60秒超时）
    List<NovelAgentState> results = futures.stream()
            .map(f -> f.get(60, TimeUnit.SECONDS))
            .toList();

    // 用 MergeStrategy 合并结果
    return strategy.merge(state, results);
}
```

**重试保护覆盖层**：

```java
private String resolveWithRetryProtection(String target, NovelAgentState state, String fromNode) {
    int maxRetries = definition.getMaxRetries(target);
    int retryCount = StateHelper.getNodeRetryCount(state, target);
    if (retryCount >= maxRetries) {
        return definition.getFallbackNode(target);  // 降级到 fallback 节点
    }
    return target;
}
```

**检查点恢复**：

```java
public NovelAgentState execute(NovelAgentState initialState, Mode mode) {
    // 尝试从 Redis 加载最近的检查点
    Optional<StateSnapshot> checkpoint = checkpointService.loadLatestCheckpoint(...);
    if (checkpoint.isPresent()) {
        NovelAgentState restored = objectMapper.readValue(checkpoint.getStateJson(), NovelAgentState.class);
        String resumeNode = getNextNode(checkpoint.getNodeName(), restored, mode);
        return executeFrom(resumeNode, restored, mode);  // 从断点恢复
    }
    return executeFrom("arc_check", state, mode);  // 从头执行
}
```

#### NodeExecutor — 单节点的执行包装

**文件**：`backend/src/main/java/com/novel/system/ai/graph/NodeExecutor.java`（144 行）

NodeExecutor 是 GraphRunner 的**装饰器**，为每个节点添加了 4 层横切关注点：

```
NodeExecutor.execute(node, state, mode)
    │
    ├─ 1. shouldSkip 检查 → 跳过则直接返回
    │
    ├─ 2. AuthorAgent.beforeNode → 检查点拦截
    │     ├─ REDIRECT → 仍然执行当前节点，但注入重定向意图
    │     ├─ REJECT  → 跳过执行，标记为 author_rejected
    │     └─ APPROVE/REVISE → 继续
    │
    ├─ 3. executeWithRetry → 带重试的节点执行
    │     ├─ ErrorStrategy.RETRY → 最多重试 maxRetries 次
    │     ├─ ErrorStrategy.SKIP  → 记录错误，继续
    │     └─ ErrorStrategy.FAIL  → 记录错误，终止
    │
    ├─ 4. AuthorAgent.afterNode → 后处理（注入修订意见等）
    │
    └─ 5. 检查点保存 → 如果是 checkpoint 节点，保存到 Redis
```

**学习要点**：
- GraphRunner 负责**遍历和调度**（走哪条路、并行执行、重试保护）
- NodeExecutor 负责**单节点的执行包装**（跳过、拦截、重试、检查点）
- 两者分离使得 GraphRunner 不关心节点内部逻辑，NodeExecutor 不关心图的路由

---

### 5.6 第 5 层：理解 AuthorAgent — "人类作者"在环

**接口**：`AuthorAgent.java`（8 行）
**实现**：`AuthorAgentImpl.java`（393 行）
**指令**：`AuthorDirective.java`（38 行）
**配置**：`AuthorAgentConfig.java`（21 行）

AuthorAgent 只在 **3 个检查点**介入：`outline`、`writing`、`audit_router`。

**执行流程**：

```
beforeNode(nodeName, state)
    │
    ├─ 检查点不在配置列表中？→ APPROVE（不介入）
    ├─ 不在 LLM_CHECKPOINTS 中？→ APPROVE（不介入）
    │
    ├─ 预筛（preFilterEnabled=true）:
    │     ├─ outline: 章纲结构完整 + 无用户意图 + 无叙事方向 → APPROVE（省 Token）
    │     ├─ writing: severity=LOW + 字数正常 + 无修订意见 → APPROVE（省 Token）
    │     └─ audit_router: 全部通过 + 质量分≥90 → APPROVE（省 Token）
    │
    └─ 调用 LLM 判断 → 解析为 AuthorDirective
          ├─ APPROVE  → 继续
          ├─ REVISE   → 注入 creativeNotes 到后续流程
          ├─ REJECT   → 清空当前阶段产出，重做
          └─ REDIRECT → 设置 auditDecision=REPLAN，跳转到其他阶段

afterNode(nodeName, state, directive)
    │
    ├─ REVISE:
    │     ├─ outline → 在章纲摘要后追加【作者修订】
    │     └─ writing → 设置 severity=HIGH + 追加 revisionNotes
    │
    ├─ REJECT:
    │     ├─ outline → 清空 chapterPlan
    │     ├─ writing → 设置 severity=HIGH + 追加"作者否决"
    │     └─ audit_router → 设置 auditDecision=REWRITE
    │
    └─ REDIRECT:
          └─ 设置 auditDecision=REPLAN + 清空 chapterPlan
```

**预筛优化是核心亮点**：

不是每个检查点都需要调用 LLM。预筛规则：
- **outline 预筛**：章纲结构完整（有标题、概要、要点、钩子）且无用户创作意图 → 自动放行
- **writing 预筛**：自审 severity=LOW 且字数正常且无修订意见 → 自动放行
- **audit 预筛**：全部通过且质量分 ≥ 90 → 自动放行

这意味着大部分正常流程中 AuthorAgent 不会调用 LLM，只在需要人类判断时才花 Token。

---

### 5.7 第 6 层：理解每个图节点的具体实现

按执行顺序逐一理解：

| 顺序 | 节点 | 核心逻辑 | 错误策略 | shouldSkip |
|------|------|----------|----------|------------|
| 1 | `arc_check` | 检查卷级编排约束，缺失则自动生成 | SKIP | 有 outlineContext 则跳过 |
| 2 | `outline` | 生成章纲（优先用已确认章纲，否则调 LLM） | **RETRY** (2次) | 已有 chapterPlan 则跳过 |
| 3 | `clue_planning` | 卷级编排硬约束 + LLM 补充伏笔操作 | SKIP | - |
| 4 | `character_planning` | LLM 规划角色动线 + 程序化行为校验 | SKIP | - |
| 5 | `idea_select` | 选择实现思路（当前取第一个，预留 HITL） | SKIP | - |
| 6 | `writing` | 生成正文 + 增强上下文（角色动线/伏笔/修订意见） | **RETRY** (2次) | severity 非 HIGH 则跳过 |
| 7 | `self_review` | LLM 自审，输出 severity + revisionNotes | SKIP | - |
| 8 | `validation` | 五维并行校验（OOC/逻辑/伏笔/设定/节奏） | SKIP | - |
| 9 | `quality_scoring` | LLM 质量评分 + 伏笔落地检查 | SKIP | - |
| 10 | `audit_router` | 决策：PASS / REWRITE / REPLAN / MANUAL_REVIEW | **FAIL** | - |
| 11 | `summary` | 生成摘要 + 结构化指纹 | SKIP | 已有 finalResult.summary 则跳过 |
| 12 | `post_process` | 保存章节 + 后处理（更新记忆）+ 清缓存 | SKIP | 已有 chapterId 则跳过 |

**重点理解 AuditRouterNode 的路由逻辑**：

```
audit_router 路由决策树：

加权模式 (useGradedAudit=true):
  ├─ 有 FATAL 失败?
  │     ├─ planRetryCount < 3 → REPLAN（回 outline 重规划）
  │     └─ planRetryCount ≥ 3 → MANUAL_REVIEW（放弃，标记人工审阅）
  ├─ 加权分 < 阈值?
  │     ├─ rewriteCount < 2 → REWRITE（回 writing 重写）
  │     └─ rewriteCount ≥ 2 → MANUAL_REVIEW
  └─ 否则 → PASS

传统模式 (useGradedAudit=false):
  ├─ rewriteCount ≥ 2 且未通过 → MANUAL_REVIEW
  ├─ 写前校验未通过 → REPLAN
  ├─ OOC/设定未通过 → REPLAN
  ├─ 其他未通过 → REWRITE
  ├─ 质量分 < 60 → REWRITE
  └─ 否则 → PASS
```

---

### 5.8 第 7 层：理解辅助组件

| 组件 | 文件 | 作用 |
|------|------|------|
| **MergeStrategy** | `MergeStrategy.java` | 扇出合并策略。`defaultStrategy` 累积 trace/errors/llmCalls；`chapterPlanMerge` 额外合并 clueActions 和 characterActions |
| **StateHelper** | `StateHelper.java` | 不可变状态操作工具。所有 `appendTrace`、`appendError`、`incrementNodeRetry` 都通过 toBuilder 创建新对象 |
| **StateCheckpointService** | `StateCheckpointService.java` | Redis 检查点服务。在 checkpoint 节点保存 state JSON，失败时从最近检查点恢复 |
| **ChatModelProvider** | `ChatModelProvider.java` | LLM 工厂。根据 llmConfigId 获取 ChatModel，区分 CREATIVE（高 temperature）和 STRUCTURED（低 temperature） |
| **ErrorStrategy** | `ErrorStrategy.java` | SKIP（跳过继续）/ RETRY（重试）/ FAIL（终止） |
| **Mode** | `Mode.java` | FAST（跳过审计）/ FULL（完整流程） |
| **NodeExecutionRecord** | `NodeExecutionRecord.java` | 节点执行记录（名称、耗时、是否跳过、错误信息） |
| **StructuredError** | `StructuredError.java` | 结构化错误（节点名、策略、消息、是否已恢复） |
| **LlmCallRecord** | `LlmCallRecord.java` | LLM 调用记录（节点名、模型、Token 用量、耗时） |

---

### 5.9 从测试入手理解行为

#### GraphDefinitionTest — 理解路由规则

| 测试 | 验证什么 |
|------|----------|
| `shouldResolveSimpleEdge` | 简单边 A→B→C，C 之后是 END |
| `shouldResolveConditionalEdge` | 条件边根据 auditDecision 走不同路径 |
| `shouldFilterByMode` | FAST 模式走 FAST 边，FULL 模式走 FULL 边 |
| `shouldDetectFanOut` | 扇出检测和扇入目标查找 |
| `shouldCheckRetryPolicy` | 重试策略和降级节点 |
| `shouldDetectCheckpoints` | 检查点检测 |
| `conditionalEdgesTakePrecedenceOverSimpleEdges` | 条件边优先级高于简单边 |

#### NodeExecutorTest — 理解节点执行包装

| 测试 | 验证什么 |
|------|----------|
| `executesNodeAndReturnsResult` | 正常执行节点 |
| `savesCheckpointForCheckpointNodes` | checkpoint 节点执行后保存检查点 |
| `skipsNodeWhenShouldSkipReturnsTrue` | shouldSkip=true 时跳过执行 |

#### GraphRunnerV2Test — 理解完整图执行

| 测试 | 验证什么 |
|------|----------|
| `shouldExecuteLinearPath` | 线性路径执行 + AuthorAgent 在检查点被调用 |
| `shouldExecuteInFastMode` | FAST 模式下 AuthorAgent 不被调用 |
| `shouldHandleMissingNodeGracefully` | 节点未注册时优雅降级 |
| `shouldSkipNodesWhenShouldSkipReturnsTrue` | shouldSkip=true 时跳过节点 |
| `shouldTerminateAtMaxIterations` | 死循环图在 20 次迭代后终止 |

---

### 5.10 动手练习建议

#### 练习 1：画图

在纸上画出完整的图拓扑，标注：
- 每条边是简单边还是条件边
- 条件边的判断条件是什么
- 哪些节点是扇出/扇入
- 哪些节点是检查点
- 每个节点的错误策略

#### 练习 2：追踪一次完整执行

假设生成第 10 章，auditResult 为 OOC 未通过 + 逻辑通过 + 伏笔通过 + 设定未通过 + 节奏通过，质量分 55。手动追踪：

1. `arc_check` → 通过
2. `outline` → 生成章纲 → AuthorAgent APPROVE
3. 扇出：`clue_planning` + `character_planning` 并行 → 合并
4. `idea_select` → 选第一个思路
5. `writing` → 生成正文 → AuthorAgent APPROVE
6. `self_review` → severity=MEDIUM
7. `validation` → OOC 未通过、设定未通过
8. `quality_scoring` → 质量分 55
9. `audit_router` → OOC/设定 FATAL → REPLAN → 回到 outline
10. `outline` → resetState 清空旧章纲 → 重新生成
11. ...（最多 3 次 REPLAN）

#### 练习 3：写一个自定义节点

尝试写一个简单的 `WordCountCheckNode`，检查字数是否在 1000-8000 之间：

```java
@Component
public class WordCountCheckNode implements NovelGraphNode {
    @Override
    public String nodeName() { return "word_count_check"; }

    @Override
    public NovelAgentState apply(NovelAgentState state) throws Exception {
        NovelAgentState.DraftContent draft = state.getDraftContent();
        if (draft == null) return state;

        boolean passed = draft.getWordCount() >= 1000 && draft.getWordCount() <= 8000;
        // 修改 state 并返回
        return state;
    }
}
```

然后在 GraphDefinition 中注册它。

#### 练习 4：对比 moyun-ts 版本

读 `moyun-ts/packages/core/src/ai/graph/graph-runner.ts`，对比 Java 版和 TypeScript 版的实现差异：
- 状态管理方式有什么不同？
- 并行执行用了什么机制？
- 错误处理策略是否一致？

---

### 5.11 核心设计模式总结

| 模式 | 在哪里用 | 解决什么问题 |
|------|----------|-------------|
| **Builder** | GraphDefinition | 灵活构建复杂图拓扑 |
| **不可变状态** | NovelAgentState + StateHelper | 避免并发修改、便于调试和回滚 |
| **策略** | MergeStrategy, ErrorStrategy | 扇出合并和错误处理可插拔 |
| **模板方法** | NovelGraphNode 的 default 方法 | 统一节点接口，各节点按需覆盖 |
| **装饰器** | NodeExecutor 包装 NovelGraphNode | 横切关注点（拦截、重试、检查点）与业务逻辑分离 |
| **观察者** | AuthorAgent 的 before/after | 在关键节点注入人类判断力 |
| **预筛优化** | AuthorAgentImpl.preFilter* | 大部分正常流程不调 LLM，省 Token |

---

## 六、核心概念速查表

| 概念 | 一句话解释 |
|------|-----------|
| **GraphRunner** | 自研 DAG 执行引擎，按图定义顺序执行 AI 节点 |
| **GraphDefinition** | 图的拓扑定义，包含边、条件边、扇出/扇入、检查点、重试策略 |
| **NovelAgentState** | 图执行过程中的全局不可变状态，在节点间流转 |
| **NovelGraphNode** | 所有图节点的统一接口，5 个生命周期钩子 |
| **NodeExecutor** | 节点执行包装器，添加跳过/拦截/重试/检查点横切逻辑 |
| **AuthorAgent** | 在关键节点模拟人类作者决策（通过/修订/拒绝/重定向） |
| **AuthorDirective** | 作者指令对象，包含 action/rationale/creativeNotes |
| **MergeStrategy** | 扇出合并策略，默认累积 trace/errors，章纲合并额外合并 clueActions/characterActions |
| **ErrorStrategy** | 错误策略：SKIP（跳过继续）/ RETRY（重试）/ FAIL（终止） |
| **Mode** | 执行模式：FAST（跳过审计）/ FULL（完整流程） |
| **六层记忆** | 故事快照→伏笔账本→角色档案→滑动窗口→事件图谱→RAG向量 |
| **五重校验** | OOC + 逻辑 + 伏笔 + 设定 + 节奏，并行执行后加权评分 |
| **反 AI 味** | 生成时约束 + 生成后检测 + 自动修正，三道防线 |
| **Push-Pull** | 前四层记忆自动注入（Push），后两层 Agent 按需调用（Pull） |
| **SSE 流式** | 章节生成时通过 Server-Sent Events 实时推送文本 |
| **MCP** | Model Context Protocol，同时支持 Client（接入外部工具）和 Server（暴露能力） |
| **StateCheckpointService** | Redis 检查点服务，支持断点恢复 |
| **ChatModelProvider** | LLM 工厂，区分 CREATIVE 和 STRUCTURED 两种 temperature |

---

## 七、推荐阅读顺序（文件清单）

### 第 1 轮：理解骨架（约 1 小时）

| 文件 | 行数 | 重点 |
|------|------|------|
| `ai/graph/Mode.java` | 3 | 两种执行模式 |
| `ai/graph/ErrorStrategy.java` | 6 | 三种错误策略 |
| `ai/graph/AuthorDirective.java` | 38 | 四种作者动作 |
| `ai/graph/NovelGraphNode.java` | 37 | 节点接口的 5 个生命周期钩子 |
| `ai/graph/AuthorAgent.java` | 8 | AuthorAgent 的 before/after 接口 |
| `ai/graph/GraphDefinition.java` | 176 | 图拓扑定义 + Builder |

### 第 2 轮：理解状态与执行（约 2 小时）

| 文件 | 行数 | 重点 |
|------|------|------|
| `ai/graph/NovelAgentState.java` | 221 | 全局状态结构，5 个 Phase 区域 |
| `ai/graph/StateHelper.java` | 99 | 不可变状态操作工具 |
| `ai/graph/NodeExecutionRecord.java` | 37 | 节点执行记录 |
| `ai/graph/StructuredError.java` | 12 | 结构化错误 |
| `ai/graph/LlmCallRecord.java` | 13 | LLM 调用记录 |
| `ai/graph/NodeExecutor.java` | 144 | 节点执行包装器 |
| `ai/graph/GraphRunner.java` | 243 | 图遍历与调度引擎 |

### 第 3 轮：理解 AuthorAgent（约 1.5 小时）

| 文件 | 行数 | 重点 |
|------|------|------|
| `ai/graph/AuthorAgentConfig.java` | 21 | 配置项 |
| `ai/graph/ChatModelProvider.java` | 65 | LLM 工厂 |
| `ai/graph/AuthorAgentImpl.java` | 393 | 预筛优化 + before/after 实现 |
| `ai/graph/StateCheckpointService.java` | 101 | Redis 检查点 |
| `ai/graph/StateSnapshot.java` | 18 | 检查点快照 |

### 第 4 轮：理解各节点（约 3 小时）

| 文件 | 行数 | 重点 |
|------|------|------|
| `ai/graph/ArcOrchestrationNode.java` | 89 | 卷级编排检查 + 兜底自动生成 |
| `ai/graph/OutlineNode.java` | 152 | 章纲生成 + 优先用已确认章纲 |
| `ai/graph/CluePlanningNode.java` | 129 | 伏笔规划 = 硬约束 + LLM 补充 |
| `ai/graph/CharacterPlanningNode.java` | 112 | 角色动线 = LLM 规划 + 程序化校验 |
| `ai/graph/IdeaSelectionNode.java` | 36 | 实现思路选择（预留 HITL） |
| `ai/graph/WritingNode.java` | 151 | 正文生成 + 增强上下文 |
| `ai/graph/SelfReviewNode.java` | 103 | LLM 自审 → severity + revisionNotes |
| `ai/graph/ValidationNode.java` | 161 | 五维并行校验 |
| `ai/graph/QualityScoringNode.java` | 139 | 质量评分 + 伏笔落地检查 |
| `ai/graph/AuditRouterNode.java` | 140 | 路由决策 PASS/REWRITE/REPLAN/MANUAL_REVIEW |
| `ai/graph/MergeStrategy.java` | 69 | 扇出合并策略 |
| `ai/graph/SummaryNode.java` | 114 | 摘要生成 + 结构化指纹 |
| `ai/graph/PostProcessNode.java` | 158 | 保存 + 后处理 + 清缓存 |

### 第 5 轮：从测试验证理解（约 1.5 小时）

| 文件 | 行数 | 重点 |
|------|------|------|
| `test/.../GraphDefinitionTest.java` | 88 | 7 个路由规则测试 |
| `test/.../NodeExecutorTest.java` | 73 | 3 个节点执行包装测试 |
| `test/.../GraphRunnerV2Test.java` | 196 | 5 个完整图执行测试 |

---

> **学习建议**：核心精力放在阶段三（GraphRunner）和阶段四（六层记忆），这两个是项目的精华。理解了 GraphRunner 的 DAG 执行 + 条件路由 + 扇出并行 + 检查点拦截 + 重试容错，就理解了整个系统的心脏。
