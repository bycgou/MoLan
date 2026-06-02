# Chapter Bible + RAG Layer 6 双层修复设计

**日期**: 2026-06-02
**状态**: 已批准
**关联**: a3.pdf 根因分析 — P0 Chapter Bible 缺失 + P1 RAG Layer 6 失效

---

## 背景

a3.pdf 识别了"每章都好看但连起来怪"的根因：系统把"写好单章"做得比较重，把"章节之间的状态传递"做得不够硬。其中两个核心缺陷：

1. **P0: 缺少 Chapter Bible** — ArcOrchestration 只有节奏元数据（rhythmTag、hookDirection 等 10-20 字标签），没有硬性顺序约束（mustStartFrom、mustHappen、mustEndWith）
2. **P1: RAG Layer 6 失效** — `RagTool.recallHistory` 工具存在但 WritingAgent 的 system prompt 没有指导 LLM 使用；`RagService.assembleContext()` 是死代码，从未接入 ContextBuilderService

---

## P0: Chapter Bible

### 数据模型

新建 MongoDB collection `chapter_bibles`，实体类 `ChapterBible`：

```json
{
  "id": "string",
  "novelId": "string",
  "arcId": "string",
  "chapterNum": 12,
  "mustStartFrom": "上一章主角刚收到师父失踪信，正在书房中焦急等待",
  "mustHappen": ["进入黑市", "发现玉牌与师父失踪有关"],
  "mustEndWith": "黑市掌柜认出玉牌，说出'三十年前'四个字后戛然而止",
  "stateDelta": {
    "location": "青石城黑市",
    "protagonistGoal": "查明玉牌来源和师父下落",
    "knownFactsAdded": ["玉牌来自三十年前的云镜宗"]
  },
  "openLoops": ["师父为何失踪", "掌柜为何认得玉牌", "三十年前发生了什么"],
  "createdAt": "2024-01-01T00:00:00"
}
```

字段说明：
- `mustStartFrom`: 本章开头必须从什么状态/场景开始（承接上章）
- `mustHappen`: 本章必须包含的剧情节点列表
- `mustEndWith`: 本章结尾必须到达的状态/场景
- `stateDelta`: 本章引起的世界状态变化
- `openLoops`: 本章产生的未解决悬念

### 生成流程

```
用户确认章纲
→ 并行 LLM 调用：为每章生成初始 Bible（输入：本章章纲 + ArcOrchestration + 上一章 Bible.mustEndWith）
→ 链式校验：检查 N.mustEndWith → N+1.mustStartFrom 的语义连续性
→ 修复断裂点（局部 LLM 重生成，最多 1 轮）
→ 保存到 MongoDB chapter_bibles
```

### 新增文件

| 文件 | 包 | 作用 |
|------|-----|------|
| `ChapterBible.java` | entity/mongo | MongoDB 文档实体 |
| `ChapterBibleRepository.java` | repository/mongo | Spring Data MongoDB Repository |
| `ChapterBibleService.java` | service/chapter | 生成 + 校验 + 保存逻辑 |
| `ChapterBibleNode.java` | ai/graph | 图节点 |

### 图流程变化

```
原: ... → idea_select → writing → ...
新: ... → idea_select → chapter_bible → writing → ...
```

`ChapterBibleNode` 从 state 中读取 chapterPlan，从 DB 读取 ArcOrchestration 和上一章 Bible，调用 ChapterBibleService 生成本章 Bible，写入 state 和 DB。

### ContextBuilderService 改造

在 `buildWritingContext()` 和 `buildOutlineContext()` 中，ArcOrchestration 注入之后追加 Bible 硬约束：

```
【章节圣经——本章必须严格遵守】
本章开头必须从：{mustStartFrom}
本章必须包含以下场景：{mustHappen}
本章结尾必须到达：{mustEndWith}
本章世界状态变化：{stateDelta}
本章开放悬念：{openLoops}
```

该段放在上下文最前面（高优先级区域），替代当前 ArcOrchestration 的 `keyEvent`（20 字标签）和 `hookDirection`（10 字标签）的位置。

### 与现有系统的关系

- **ArcOrchestration 保留**：提供节奏/视角/冲突类型等元数据，以及伏笔计划（CluePlan）
- **Chapter Bible 补充**：提供硬性顺序约束，两者互补
- **CluePlanningNode**：继续从 ArcOrchestration 读取伏笔计划，不受影响
- **Bible 与 keyEvent 对齐**：`mustHappen` 是 `keyEvent` 的展开，`mustEndWith` 是 `hookDirection` 的具体化

### 异常处理

- 如果某章没有 ArcOrchestration（fallback），Bible 生成时省略卷级约束，只基于章纲生成
- 如果链式校验发现断裂但修复失败，记录警告但不阻塞生成流程
- 如果 MongoDB 写入失败，降级为不注入 Bible（ContextBuilder 检查 null）

---

## P1: RAG Layer 6 双层修复

### 问题现状

两套检索机制：
- **Layer 5 RuleDrivenRetrievalService**：✅ 自动触发，基于 EventGraph 结构化事件，正常工作
- **Layer 6 RagTool.recallHistory**：❌ 实质失效，system prompt 无指导，`assembleContext()` 是死代码

### 方案：Push + Pull 双层

#### Push 层：自动注入（ContextBuilderService）

在 `buildWritingContext()` 中 Layer 5（事件图谱）之后新增 Layer 6：

```java
// === 第六层：RAG 向量检索 — 自动注入相关历史 ===
try {
    String ragContext = ragService.assembleContext(
        novelId, chapterNum, chapter.getChapterOutline());
    if (ragContext != null && !ragContext.isEmpty()) {
        sb.append(tokenBudgetService.truncateToBudget(ragContext, 1000));
    }
} catch (Exception e) {
    log.warn("RAG自动检索失败（不影响生成）: {}", e.getMessage());
}
```

`RagService.assembleContext()` 已有实现（`RagServiceImpl.java:452-529`），需要：
1. 确认 `hybridSearch()` 返回的 ChapterSummary 包含 content（摘要文本）
2. 格式化为 `【相关历史剧情（自动检索）】` 注入格式
3. 接入 ContextBuilderService.buildWritingContext()

同样在 `buildOutlineContext()` 中接入，用于章纲生成时的历史参考。

#### Pull 层：System Prompt 指导（WritingAgent）

在 WritingAgent 的 `@SystemMessage` 末尾追加：

```
【历史回忆工具——必须主动使用】
当你遇到以下情况，必须先调用 recallHistory 工具回忆相关历史，再开始写作：
1. 章纲提到一个超过5章未出场的人物 — 调用 recallHistory 搜索该人物上次出场
2. 场景发生在一个有历史事件的地点 — 调用 recallHistory 搜索该地点的历史
3. 需要回收一个埋设超过10章的伏笔 — 调用 recallHistory 搜索伏笔埋设章节
4. 涉及早期章节的关键情节细节 — 调用 recallHistory 确认细节
调用后，将返回的历史信息融入当前写作，确保前后一致。
如果工具返回"未找到相关内容"，则根据上下文信息继续创作。
```

### 生效机制

| 层 | 触发方式 | 操作对象 | Token 消耗 |
|----|----------|----------|------------|
| Push（自动） | 每次写作时自动检索 | 章摘要向量索引 | ~1000 token |
| Pull（按需） | LLM 根据 system prompt 指导调用 | 章摘要向量索引 | 按需，通常 0-2 次调用 |

Push 保证基础覆盖面（最近相关历史总是被注入），Pull 补充远距离精确回忆（LLM 判断需要时主动检索）。

### 改动文件

| 文件 | 改动 |
|------|------|
| `ContextBuilderService.java` | `buildWritingContext()` 和 `buildOutlineContext()` 新增 Layer 6 RAG 注入 |
| `RagServiceImpl.java` | 确认 `assembleContext()` 格式正确，必要时调整输出格式 |
| `WritingAgent.java` | `@SystemMessage` 追加工具使用指令 |

### 异常处理

- RAG 检索失败不阻塞生成流程（try-catch + log.warn）
- 如果 `assembleContext()` 返回 null 或空，跳过 Layer 6 注入
- LLM 不调用 recallHistory 工具时，Push 层仍保证基本覆盖

---

## 验证方式

### P0 Chapter Bible
1. 创建测试小说，走完向导流程（卷纲 → 编排 → 章纲 → 确认 → 批量生成）
2. 检查 MongoDB `chapter_bibles` collection 是否有完整数据
3. 检查生成正文时 ContextBuilder 输出是否包含 `【章节圣经】` 区域
4. 检查 `mustStartFrom` 是否与上一章 `mustEndWith` 语义连续

### P1 RAG Layer 6
1. 创建已有 10+ 章的小说，生成新章节
2. 检查 ContextBuilder 输出是否包含 `【相关历史剧情（自动检索）】`
3. 检查 WritingAgent 日志是否有 recallHistory 工具调用
4. 对比修复前后的章节连续性质量
