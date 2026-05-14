# MoYun 外部记忆系统 — 现状分析与重新设计方案

---

## 第一部分：现状分析

### 一、现有架构总览

当前项目的外部记忆系统由以下组件构成：

| 组件 | 存储位置 | 职责 | 代码文件 |
|------|----------|------|----------|
| StoryState | MongoDB `story_state` | 故事实时状态枢纽 | `StoryState.java` / `StoryStateServiceImpl.java` |
| LongTermMemory | MongoDB `novel_memory` | 长期记忆（角色变化、伏笔回收、重要情节） | `LongTermMemory.java` |
| NovelChatMemory | Redis | 短期对话记忆（Agent生成过程中的上下文） | `NovelChatMemory.java` |
| CharacterState | MongoDB `character_state` | 角色动态状态（情绪、位置、关系、成长） | `CharacterState.java` / `CharacterStateService.java` |
| CharacterGraph | 内存 HashMap | 人物关系图谱 | `CharacterGraphServiceImpl.java` |
| ChapterSummary | MongoDB `chapter_summary` | 章节摘要 + 向量嵌入 | `ChapterSummary.java` |
| NovelMemory | MongoDB `novel_memory` | 压缩后的全局背景摘要 | `NovelMemory.java` |
| NovelGraph | MongoDB `novel_graph` | 知识图谱（实体+关系边） | `NovelGraph.java` / `ChapterSaveService.java` |
| ClueBook | MySQL `clue_book` | 伏笔精确追踪 | `ClueBookService.java` |
| RAG检索 | Redis Stack + MongoDB | 向量/BM25/混合检索 | `RagServiceImpl.java` / `RedisVectorServiceImpl.java` |
| TokenBudget | 无持久化 | Token预算分配 | `TokenBudgetService.java` |
| ContextBuilder | 无持久化 | Prompt组装引擎 | `ContextBuilderService.java` |
| ChapterPostProcess | 无持久化 | 后处理流水线（人物/伏笔/时间线提取） | `ChapterPostProcessService.java` |

---

### 二、设计合理性分析

#### ✅ 做得好的部分

**1. StoryState 设计理念先进**
- 作为"核心枢纽"定位准确，相当于作者的"桌上进度表"
- 结构化字段（时间线、主角状态、场景上下文、节奏状态、叙事标记）覆盖了长篇小说创作的核心需求
- `formatForPrompt()` 方法直接输出可注入的文本，设计简洁

**2. 分层记忆的思路正确**
- 弧段摘要（全局）→ 近章大纲（局部）→ 上章钩子（微观），三层递进
- 伏笔精确查询 + RAG语义检索，精确与模糊互补

**3. 后处理流水线设计完善**
- `ChapterPostProcessService` 自动提取人物、伏笔、时间线
- `ChapterSaveService` 自动更新知识图谱和故事状态
- 异步执行不阻塞主流程

**4. TokenBudgetService 动态预算分配**
- 按比例分配各部分Token，避免硬编码截断
- 支持不同模型的 maxToken 配置

**5. 混合检索（向量+BM25+RRF融合）**
- `RagServiceImpl.hybridSearch()` 实现了完整的 RRF 融合算法
- Rerank 重排序提升检索质量

---

#### ⚠️ 存在的问题

**问题一：记忆系统存在大量冗余和重叠**

| 数据 | 存储位置1 | 存储位置2 | 重叠说明 |
|------|-----------|-----------|----------|
| 角色状态 | `StoryState.protagonist` | `CharacterState` | 主角状态在两处都有 |
| 角色关系 | `CharacterState.relationshipMap` | `CharacterGraphServiceImpl`（内存HashMap） | `NovelGraph`（MongoDB） | 关系数据三处存储 |
| 章节摘要 | `ChapterSummary.content` | `LongTermMemory.MemoryEntry` | 两者都记录章节级记忆 |
| 全局背景 | `NovelMemory.compressedContent` | `LongTermMemory.getFormattedMemory()` | 功能高度重叠 |

**影响**：数据一致性难以保证，更新一处容易遗漏另一处。

**问题二：CharacterGraphServiceImpl 使用内存 HashMap 存储**

```java
// CharacterGraphServiceImpl.java
private final Map<String, List<CharacterRelationship>> relationshipStore = new HashMap<>();
```

- 服务重启后所有关系数据丢失
- 无法持久化，无法跨实例共享
- 与 MongoDB `NovelGraph` 功能完全重复

**问题三：StoryState 更新过于简陋**

```java
// StoryStateServiceImpl.updateAfterChapter()
// 仅做了：版本号+1、追加事件、递增节奏计数器
// 缺失：主角状态更新、时间线推进、场景上下文更新、叙事标记更新
```

StoryState 被设计为"核心枢纽"，但实际更新逻辑只处理了最简单的字段，主角位置、情绪、实力等级等关键字段从未被自动更新。

**问题四：RAG 检索策略与设计文档矛盾**

设计文档要求 RAG "Agent 按需调用"，但代码实现中 `ContextBuilderService.buildWritingContext()` 每次生成都会执行向量检索：

```java
// ContextBuilderService.java:291-309
List<ChapterSummary> ragResults = ragService.vectorSearch(novelId, queryText, 3);
```

同时 `RagServiceImpl.assembleContext()` 也执行了混合检索。两处重复调用 RAG，浪费 Token。

**问题五：LongTermMemory 与 NovelMemory 职责模糊**

- `LongTermMemory`：存储角色变化、伏笔回收、重要情节（按章节范围查询）
- `NovelMemory`：存储压缩后的全局背景摘要（按章节范围压缩）

两者都做"长期记忆"，但 `LongTermMemory` 是逐条记录，`NovelMemory` 是压缩摘要。它们之间的关系不清晰，`ContextBuilderService` 同时注入了两者，内容可能重复。

**问题六：TokenBudget 已定义但未实际使用**

`TokenBudgetService` 定义了预算比例和截断方法，但 `ContextBuilderService` 中完全未调用，所有截断仍使用硬编码的 `truncate(str, maxLength)`。

**问题七：后处理流水线缺少事务保证**

`ChapterSaveService.saveGenerationResult()` 标注了 `@Transactional`，但内部调用了多个外部服务（RAG、向量存储、知识图谱），这些 MongoDB/Redis 操作不在 MySQL 事务范围内。部分更新失败会导致数据不一致。

**问题八：NovelChatMemory 定位不清**

`NovelChatMemory` 基于 Redis 存储对话消息，但当前 Agent 架构是单次生成（非多轮对话），这个组件几乎没有被使用场景。

---

### 三、问题严重度评级

| 问题 | 严重度 | 影响 |
|------|--------|------|
| 数据冗余与重叠 | P1 | 一致性风险、维护成本高 |
| CharacterGraph 内存存储 | P0 | 数据丢失风险 |
| StoryState 更新不完整 | P1 | 核心枢纽形同虚设 |
| RAG 重复调用 | P2 | Token 浪费、上下文污染 |
| LongTermMemory 与 NovelMemory 重叠 | P2 | 重复注入、Token 浪费 |
| TokenBudget 未使用 | P2 | 预算控制形同虚设 |
| 后处理缺少事务保证 | P2 | 数据不一致风险 |
| NovelChatMemory 定位不清 | P3 | 死代码 |

---

## 第二部分：重新设计方案

### 一、设计哲学

```
核心原则：
1. 单一数据源（Single Source of Truth）—— 每类数据只存一处
2. 精确查询优先，语义检索兜底 —— 结构化数据为主，向量检索为辅
3. 写时更新，读时组装 —— 每章生成后立即更新所有状态
4. 按需注入，预算控制 —— 不是所有记忆都每次注入
5. 渐进式记忆 —— 近期精确、远期模糊、超远期压缩
```

### 二、五层记忆架构（重新设计）

```
┌──────────────────────────────────────────────────────────────┐
│                     WritingAgent 生成                         │
│                                                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐       │
│  │   第一层      │  │   第二层      │  │   第三层      │       │
│  │  故事快照     │  │  活跃角色     │  │  滑动窗口     │       │
│  │  (全量注入)   │  │  (按需注入)   │  │  (全量注入)   │       │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘       │
│         └─────────────────┼─────────────────┘               │
│                           │                                  │
│  ┌────────────────────────┼────────────────────┐             │
│  │          第四层：伏笔账本 + 关系图谱          │             │
│  │       (精确查询，按条件注入)                   │             │
│  └────────────────────────┬────────────────────┘             │
│                           │                                  │
│  ┌────────────────────────┼────────────────────┐             │
│  │          第五层：RAG 语义检索                  │             │
│  │       (Agent @Tool 按需调用，非自动注入)       │             │
│  └─────────────────────────────────────────────┘             │
└──────────────────────────────────────────────────────────────┘
```

---

### 三、各层详细设计

#### 第一层：故事快照（StorySnapshot）— 替代原 StoryState + NovelMemory

**定位**：小说创作的"总控面板"，每次生成时全量注入。

**存储位置**：MongoDB `story_snapshot`（替代原 `story_state` + `novel_memory`）

**核心改动**：将原 `NovelMemory`（压缩全局背景）合并进 `StorySnapshot`，消除冗余。

```json
{
  "novelId": "novel_001",
  "version": 47,
  "lastChapter": 86,

  "timeline": {
    "worldDate": "天元历三年七月初十",
    "elapsedDays": 127,
    "season": "夏末",
    "timeOfDay": "黄昏"
  },

  "protagonist": {
    "name": "林辰",
    "powerLevel": "凝真高阶",
    "location": "青云宗后山秘境",
    "mood": "愤怒但警觉",
    "physicalCondition": "右手经脉受损",
    "inventory": ["焚天火种", "传承碎片x1"],
    "currentGoal": "调查赵峰背后的内门长老"
  },

  "sceneContext": {
    "activeCharacters": [
      {"id": "char_002", "name": "苏清月", "state": "并肩作战后休整"}
    ],
    "immediateThreat": "赵峰临死求救信号已发出",
    "lastMajorEvent": "第86章：击败赵峰"
  },

  "pacingState": {
    "tensionLevel": 8,
    "chaptersSinceLastClimax": 0,
    "chaptersSinceLastRest": 5,
    "recommendation": "需要1-2章缓冲过渡"
  },

  "recentMajorEvents": [
    {"chapter": 81, "event": "击败赵峰小队"},
    {"chapter": 84, "event": "石甲熊进化"},
    {"chapter": 86, "event": "赵峰临死求救"}
  ],

  "narrativeFlags": {
    "currentPOV": "林辰",
    "unresolvedCliffhangers": ["赵峰求救信号"],
    "recentReveals": ["秘境深处有灵脉核心"]
  },

  "compressedBackstory": "林辰自幼父母双亡，被青云宗收养。三年前偶然获得焚天火种..."
}
```

**与原设计的区别**：
- 合并了 `NovelMemory.compressedContent` → `compressedBackstory` 字段
- 删除了独立的 `LongTermMemory`，其功能由 `compressedBackstory` + `recentMajorEvents` 替代
- 主角状态不再与 `CharacterState` 重复，`StorySnapshot.protagonist` 仅保留"当前快照"，`CharacterState` 保留"完整档案"

**更新机制**：每章生成后，由 SummaryAgent 提取状态变化并更新。

---

#### 第二层：活跃角色（Active Characters）— 精简原 CharacterState

**定位**：本章涉及角色的完整档案，按需注入。

**存储位置**：MongoDB `character_state`（保留，但删除冗余字段）

**注入策略**：只注入本章出场角色，不注入全部角色。

```
注入内容：
1. 基础设定（性格、背景、首次出场）← MySQL novel_character
2. 当前状态（情绪、位置、成长轨迹）← MongoDB character_state
3. 禁止行为（forbiddenBehaviors，防止OOC）← MongoDB character_state
4. 与其他出场角色的关系值 ← MongoDB character_state.relationshipMap
```

**核心改动**：
- 删除 `CharacterGraphServiceImpl`（内存 HashMap），关系数据统一存储在 `CharacterState.relationshipMap` + `NovelGraph`
- `NovelGraph` 仅存储"关系边"（用于图谱可视化），`CharacterState.relationshipMap` 存储"关系值"（用于 Prompt 注入）

---

#### 第三层：滑动窗口（Sliding Window）— 优化原实现

**定位**：保证章节之间的微观衔接。

**存储位置**：MySQL `novel_chapter` + MongoDB `chapter_summary`

**注入内容**：

```
1. 最近3章完整章纲 ← MySQL novel_chapter
2. 最近5章正文摘要 ← MongoDB chapter_summary
3. 上一章结尾原文（最后500字）← MySQL novel_chapter.content
```

**核心改动**：
- 原实现中章纲生成取近8章、正文生成取近5章，统一为"近3章章纲 + 近5章摘要"
- 删除 `LongTermMemory.getFormattedMemory()` 的近20章记忆注入（与滑动窗口重叠）

---

#### 第四层：伏笔账本 + 关系图谱 — 精确查询

**定位**：结构化数据的精确查询，不依赖向量检索。

**存储位置**：MySQL `clue_book` + MongoDB `novel_graph`

**注入策略**：

```
伏笔查询规则：
1. 到期预警：deadline <= 当前章号 + 5 的未回收伏笔 → "本章必须推进"
2. 近期埋设：bury_chapter >= 当前章号 - 10 的未回收伏笔 → "需要适当推进"
3. 全局概览：所有未回收伏笔，按 deadline 排序，取前10条

关系图谱查询：
1. 本章涉及角色之间的所有关系边
2. 本章场景地点相关的历史事件
```

**核心改动**：
- 将 `CharacterGraphServiceImpl` 的内存存储迁移到 `NovelGraph`（MongoDB）
- 伏笔查询增加"近期埋设"维度（原实现只有"到期预警"和"全部未回收"）

---

#### 第五层：RAG 语义检索 — Agent 按需调用

**定位**：最后的兜底手段，用于回忆早期章节的具体细节。

**存储位置**：MongoDB `chapter_summary`（embedding）+ Redis Stack 向量索引

**核心改动**：从"每次生成都跑"改为"Agent @Tool 按需调用"。

```java
@Tool("回忆早期章节的具体细节。当需要回收很久以前埋的伏笔、角色重逢需要回忆上次见面的具体情况时调用。")
public String recallHistory(String novelId, String query, int topK) {
    List<ChapterSummary> results = ragService.hybridSearch(novelId, query, topK);
    return formatSearchResults(results);
}
```

**触发场景**：
- 需要回忆某个早期事件的具体细节
- 需要回收一个很久以前埋的伏笔
- 角色重逢场景，需要回忆上次见面的具体情况

---

### 四、Prompt 组装引擎（重新设计）

```
┌─────────────────────────────────────────────────────────────┐
│                   Prompt 组装引擎 v2                         │
│                                                             │
│  ① 系统指令 + 反AI味规则          ← 规则层        [800t]    │
│  ② 文风卡片                       ← 风格层        [500t]    │
│  ③ 小说总纲 + 世界观              ← MySQL         [800t]    │
│  ④ 本章章纲                       ← MySQL         [200t]    │
│  ⑤ 故事快照（第一层）              ← MongoDB       [600t]    │
│  ⑥ 活跃角色档案（第二层）          ← MySQL+Mongo   [600t]    │
│  ⑦ 滑动窗口（第三层）              ← MySQL+Mongo   [2000t]   │
│  ⑧ 伏笔提醒 + 关系子图（第四层）   ← MySQL+Mongo   [400t]    │
│                                                             │
│  预留给 RAG（第五层）              ← Agent @Tool   [800t]    │
│  输出空间                                         [800t]    │
│                                                             │
│  总计：~7800 token                                           │
└─────────────────────────────────────────────────────────────┘
```

**核心改动**：
- 所有内容通过 `TokenBudgetService` 控制长度，不再硬编码截断
- RAG 不再自动注入，改为 Agent 按需调用
- 删除 `LongTermMemory` 的独立注入（已被故事快照吸收）

---

### 五、后处理流水线（重新设计）

```
章节生成完成
    │
    ▼
┌─────────────────────────────────────────────────────────┐
│              后处理流水线 v2（顺序执行）                    │
│                                                         │
│  Step 1: SummaryAgent 生成本章摘要                       │
│          → 写入 MongoDB chapter_summary                  │
│          → 生成 embedding 写入 Redis 向量索引             │
│                                                         │
│  Step 2: 更新故事快照                                    │
│          → SummaryAgent 提取状态变化                     │
│          → 更新主角位置/情绪/实力/目标                    │
│          → 推进时间线                                    │
│          → 更新场景上下文                                │
│          → 更新节奏状态                                  │
│          → 更新叙事标记                                  │
│          → 追加近期事件（保留最近10条）                   │
│          → 每20章触发一次背景压缩                        │
│          → version + 1                                  │
│                                                         │
│  Step 3: 更新活跃角色状态                                │
│          → 本章涉及角色的情绪/位置/成长节点               │
│          → 更新角色关系值                                │
│                                                         │
│  Step 4: 更新知识图谱                                   │
│          → 新角色/新关系/关系变化 → 更新 novel_graph      │
│                                                         │
│  Step 5: 检查伏笔                                       │
│          → 本章是否回收了某个伏笔 → 更新 clue_book       │
│          → 本章是否埋设了新伏笔 → 写入 clue_book         │
│                                                         │
│  Step 6: 审计检查                                       │
│          → 一致性检查（实力/位置/时间线）                 │
│          → AI味检查（禁用词/句式/模板）                   │
│          → 结果写入 audit_log                            │
└─────────────────────────────────────────────────────────┘
```

**核心改动**：
- Step 2 大幅增强：从"仅递增版本号"变为"完整的状态更新"
- 增加背景压缩触发机制（每20章压缩一次，替代独立的 `NovelMemory`）
- 删除 `LongTermMemory` 的写入（已被故事快照吸收）

---

### 六、数据模型变更

#### 删除的组件

| 组件 | 原因 | 替代方案 |
|------|------|----------|
| `LongTermMemory` | 与 `StorySnapshot` + `NovelMemory` 重叠 | `StorySnapshot.recentMajorEvents` + `compressedBackstory` |
| `NovelMemory` | 与 `StorySnapshot` 合并 | `StorySnapshot.compressedBackstory` |
| `NovelChatMemory` | 当前架构无多轮对话场景 | 如需多轮对话，使用 LangChain4j 内置 ChatMemory |
| `CharacterGraphServiceImpl`（内存HashMap） | 数据不持久化 | 统一使用 `NovelGraph`（MongoDB） |

#### 合并的组件

| 原组件 | 合并到 | 说明 |
|--------|--------|------|
| `StoryState` + `NovelMemory` | `StorySnapshot` | 一个文档包含实时状态 + 压缩背景 |
| `LongTermMemory.MemoryEntry` | `StorySnapshot.recentMajorEvents` | 近期事件列表替代逐条记忆 |

#### 新增的字段

| 位置 | 字段 | 说明 |
|------|------|------|
| `StorySnapshot` | `compressedBackstory` | 合并自 NovelMemory 的压缩背景 |
| `StorySnapshot` | `lastCompressChapter` | 上次压缩的章节号，用于触发条件 |

---

### 七、StorySnapshot 更新逻辑（核心代码设计）

```java
public StorySnapshot updateAfterChapter(String novelId, int chapterNum, 
                                         String chapterSummary, 
                                         String chapterContent) {
    StorySnapshot snapshot = getOrCreate(novelId);
    
    // 1. 使用 LLM 提取状态变化
    StateExtraction extraction = extractStateChanges(chapterSummary, chapterContent);
    
    // 2. 更新时间线
    if (extraction.getTimeline() != null) {
        snapshot.setTimeline(extraction.getTimeline());
    }
    
    // 3. 更新主角状态
    if (extraction.getProtagonistUpdate() != null) {
        mergeProtagonistState(snapshot.getProtagonist(), extraction.getProtagonistUpdate());
    }
    
    // 4. 更新场景上下文
    snapshot.setSceneContext(extraction.getSceneContext());
    
    // 5. 更新节奏状态
    updatePacingState(snapshot, extraction);
    
    // 6. 更新叙事标记
    updateNarrativeFlags(snapshot, extraction);
    
    // 7. 追加近期事件（保留最近10条）
    snapshot.getRecentMajorEvents().add(
        new MajorEvent(chapterNum, extraction.getMajorEventDescription())
    );
    trimToLast(snapshot.getRecentMajorEvents(), 10);
    
    // 8. 每20章触发背景压缩
    if (chapterNum - snapshot.getLastCompressChapter() >= 20) {
        String newBackstory = compressBackstory(
            snapshot.getCompressedBackstory(), 
            snapshot.getRecentMajorEvents()
        );
        snapshot.setCompressedBackstory(newBackstory);
        snapshot.setLastCompressChapter(chapterNum);
    }
    
    // 9. 递增版本号
    snapshot.setVersion(snapshot.getVersion() + 1);
    snapshot.setLastChapter(chapterNum);
    
    return save(snapshot);
}
```

---

### 八、Token 预算控制（实际集成）

```java
public String buildWritingContext(String novelId, int chapterNum, ...) {
    TokenBudget budget = tokenBudgetService.calculateBudget(modelMaxToken);
    StringBuilder sb = new StringBuilder();
    
    // 按预算分配各层内容
    sb.append(truncateToBudget(systemInstructions, budget.getAllocated("antiHallucination")));
    sb.append(truncateToBudget(styleCard, budget.getAllocated("styleCard")));
    sb.append(truncateToBudget(storySnapshot, budget.getAllocated("storyState")));
    sb.append(truncateToBudget(characterStates, budget.getAllocated("characterState")));
    sb.append(truncateToBudget(clueBook, budget.getAllocated("clueBook")));
    sb.append(truncateToBudget(recentOutline, budget.getAllocated("recentOutline")));
    sb.append(truncateToBudget(novelOutline, budget.getAllocated("novelOutline")));
    sb.append(truncateToBudget(worldSetting, budget.getAllocated("worldSetting")));
    // RAG 不再自动注入
    
    return sb.toString();
}
```

---

### 九、迁移路径

```
Phase 1：修复关键问题（1-2天）
├── 将 CharacterGraphServiceImpl 的 HashMap 迁移到 NovelGraph（MongoDB）
├── 完善 StoryStateServiceImpl.updateAfterChapter() 的更新逻辑
└── 删除 ContextBuilderService 中的 RAG 自动调用

Phase 2：消除冗余（2-3天）
├── 合并 StoryState + NovelMemory → StorySnapshot
├── 删除 LongTermMemory（功能由 StorySnapshot 吸收）
├── 删除 NovelChatMemory（当前无使用场景）
└── 集成 TokenBudgetService 到 ContextBuilderService

Phase 3：增强后处理（2-3天）
├── 增强 SummaryAgent 提取能力（主角状态、时间线、场景上下文）
├── 实现背景压缩触发机制（每20章）
├── 将 RAG 改为 Agent @Tool 按需调用
└── 添加后处理流水线的事务补偿机制

Phase 4：验证与优化（1-2天）
├── 端到端测试：生成10章验证记忆一致性
├── Token 消耗对比：优化前后
└── 性能测试：百万字级小说的记忆检索延迟
```

---

### 十、新旧架构对比

| 维度 | 原架构 | 新架构 |
|------|--------|--------|
| 记忆层数 | 6层（设计）/ 4层（实现） | 5层（设计=实现） |
| 数据源 | 8个存储组件 | 5个存储组件 |
| 数据冗余 | 严重（角色状态3处、关系3处、长期记忆2处） | 无冗余（单一数据源） |
| RAG调用 | 每次生成自动调用 | Agent @Tool 按需调用 |
| StoryState更新 | 仅递增版本号 | LLM提取+完整更新 |
| Token控制 | 定义了但未使用 | 实际集成到组装引擎 |
| 关系图谱 | 内存HashMap（不持久化） | MongoDB（持久化） |
| 背景压缩 | 独立组件NovelMemory | 合并到StorySnapshot |
| 后处理事务 | 无保证 | 补偿机制 |

---

### 十一、总结

现有记忆系统的**设计理念是正确的**——分层架构、精确查询优先、每章更新。但在**实现层面存在三个核心问题**：

1. **数据冗余**导致一致性风险——同样的数据存了2-3处，更新一处容易遗漏另一处
2. **核心枢纽形同虚设**——StoryState 设计了丰富的字段，但更新逻辑只递增版本号
3. **RAG过度使用**——每次生成都调用向量检索，浪费Token且可能引入噪声

重新设计的核心思路是**做减法**：从8个存储组件精简到5个，消除冗余，让每个组件职责清晰、数据唯一。同时**做加法**：增强 StorySnapshot 的更新逻辑，让"核心枢纽"真正发挥作用；将 Token 预算控制从"纸上谈兵"变为"实际生效"。

最终目标是：**让 AI 写第300章时，仍然精确记得第3章埋的伏笔、角色的性格底线、以及上一章结尾的悬念。**
