# Moyun 记忆系统优化设计方案

> 借鉴 PlotPilot (墨枢) 记忆系统优势，针对 moyun 项目现有六层架构的增量优化方案。

**日期**: 2026-06-14
**状态**: 已批准
**方案**: 分阶段演进 (Phase 1 → 2 → 3)

---

## 1. 背景与问题

### 1.1 现有架构

moyun 项目已有六层记忆架构：

| Layer | 组件 | 存储 | 功能 |
|-------|------|------|------|
| 1 | StoryState | MongoDB | 核心状态枢纽：时间线、主角状态、节奏、压缩背景 |
| 2 | ClueBook | MySQL | 伏笔账本：埋/收线索+截止日期 |
| 3 | CharacterState + CharacterGraph | MongoDB | 角色档案+关系图谱 |
| 4 | SlidingWindow | MySQL/MongoDB | 章节摘要 + 近期大纲 + 上章钩子 |
| 5 | RuleDrivenRetrieval | MongoDB | 四条自动触发规则 |
| 6 | RAG | Milvus + MongoDB | 向量+BM25混合检索 |

### 1.2 四个核心痛点

1. **角色前后矛盾**: 死人复活、受伤后无恙、性格突变——无确定性事实锁定机制
2. **剧情重复/循环**: AI反复写类似情节——无已完成节拍追踪
3. **长篇遗忘早期剧情**: TokenBudget按固定比例分配，无优先级保护
4. **章节摘要丢失情感驱动力**: 传统摘要变成流水账，丢失角色心理变化

### 1.3 PlotPilot 的对应解决方案

| 痛点 | PlotPilot 组件 | 核心思想 |
|------|---------------|---------|
| 事实矛盾 | FACT_LOCK | 不变事实锁 + LLM违规检测 |
| 剧情循环 | CompletedBeats | 已完成节拍列表注入上下文 |
| 长篇遗忘 | T0绝对保护 | 四层洋葱预算模型，T0永不压缩 |
| 情感丢失 | EmotionLedger | 四维度情感账本替代传统摘要 |
| 回响召回 | Echo Recall | 角色早期台词的向量检索 |
| 进度感知 | 收敛沙漏 | 根据完成度自动切换行为模式 |

---

## 2. 整体架构映射

在现有六层架构中嵌入新组件：

```
现有六层架构                    新增组件（嵌入位置）
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Layer 1: StoryState         ← +FactLock（T0不可变事实锁）
                               +CompletedBeats（已完成节拍）
                               +ConvergenceState（收敛沙漏状态）

Layer 2: ClueBook           （不动，已够用）

Layer 3: CharacterState     （未来扩展: KnowledgeBoundary 知识边界追踪，本次不实现）

Layer 4: SlidingWindow      ← +EmotionLedger（情感账本，与摘要并行注入）

Layer 5: RuleDrivenRetrieval ← +EchoRecallTrigger（回响召回触发规则）

Layer 6: RAG                ← +EchoRecallIndex（角色语音向量索引）

TokenBudgetService          ← +T0绝对保护 + 级联压缩
```

### 设计原则

- 每个新组件都是独立 `@Service`，不修改现有 Service 核心逻辑
- 通过 `PostProcessNode`（生成后提取）和 `ContextBuilderService`（生成前注入）两个接入点集成
- 新增数据全部存 MongoDB，与现有 StoryState 同库
- Graph 管线最小化变更，仅在必要位置插入新 Node

---

## 3. Phase 1: FactLock + EmotionLedger

### 3.1 FactLockService（事实锁）

**借鉴来源**: PlotPilot `MemoryEngine.FACT_LOCK`
**存储**: MongoDB 新集合 `fact_lock`
**接入点**: 生成前注入 ContextBuilderService，生成后在 PostProcessNode 中检测违规

#### 数据模型

```java
@Document("fact_lock")
public class FactLock {
    @Id
    private String id;
    private Long novelId;

    // 角色白名单（已确认出场的角色）
    private Set<String> knownCharacters;
    // 死亡名单（角色名 + 死因 + 死亡章节）
    private List<DeathRecord> deathRecords;
    // 关系锁定（已确立的关系不可单方面改变）
    private Map<String, RelationshipLock> relationshipLocks;
    // 时间线锚点（已发生事件的时间顺序）
    private List<TimelineAnchor> timelineAnchors;
    // 世界观硬规则（不可违反的设定）
    private List<String> worldRuleLocks;

    private LocalDateTime updatedAt;
}

public class DeathRecord {
    private String characterName;
    private String causeOfDeath;
    private int chapterNum;
    private String description;
}

public class RelationshipLock {
    private String fromCharacter;
    private String toCharacter;
    private String relationType;    // 师徒/父子/仇敌/恋人
    private int establishedChapter; // 确立章节
    private boolean canChange;      // 是否允许变化
}

public class TimelineAnchor {
    private int chapterNum;
    private String event;
    private String worldDate;       // 小说内时间
}
```

#### 生成前注入（ContextBuilderService）

在 `buildWritingContext()` 中，作为 T0 层注入：

```
=== 事实锁 ===
已知角色: 林宇, 张三, 李四, 王五
已故角色: 赵六(第5章战死于魔域), 钱七(第12章被暗杀)
关系锁定: 林宇→张三(师徒,第2章确立), 林宇→李四(仇敌,第8章确立)
时间线: 第3章-比武大会 → 第5章-魔域之战 → 第8章-师门阴谋揭露
世界观硬规则: 修仙者无法复活死者, 灵力上限由根骨决定
```

#### 生成后违规检测（新增 FactLockViolationNode）

在 Graph 管线中，`audit_router` 之后、`summary` 之前插入：

```java
@Component
public class FactLockViolationNode implements GraphNode {
    // LLM结构化输出检测违规
    // 违规类型: DEAD_CHARACTER_REVIVED / TIMELINE_CONTRADICTION /
    //           UNAUTHORIZED_NEW_CHARACTER / RELATIONSHIP_VIOLATION / WORLD_RULE_BREAK
    // severity: HIGH(触发重写) / MEDIUM(写入warnings) / LOW(仅记录)
}
```

#### 更新时机（ChapterPostProcessService）

每章生成后自动更新 FactLock：
- 新角色出场 → 加入 `knownCharacters`
- 角色死亡 → 加入 `deathRecords`
- 关系变化 → 更新 `relationshipLocks`
- 重大事件 → 追加 `timelineAnchors`

### 3.2 EmotionLedgerService（情感账本）

**借鉴来源**: PlotPilot `EmotionLedger`
**存储**: MongoDB 新集合 `emotion_ledger`（每小说一条记录，append-only）
**接入点**: 在 SummaryNode 中提取，注入 ContextBuilderService 的 Layer 4

#### 数据模型

```java
@Document("emotion_ledger")
public class EmotionLedger {
    @Id
    private String id;
    private Long novelId;
    private List<EmotionEntry> entries;  // append-only
    private int version;

    public static class EmotionEntry {
        private int chapterNum;
        private EmotionalWound wound;     // 核心丧失+心理影响
        private EmotionalBoon boon;       // 核心获得+价值
        private PowerShift powerShift;    // 权力动态变化
        private List<OpenLoop> openLoops; // 未解钩子+紧急度
    }

    public static class EmotionalWound {
        private String loss;           // "失去信任的导师"
        private String psychImpact;    // "心理转向：多疑、谨慎"
    }

    public static class EmotionalBoon {
        private String gain;           // "获得神秘传承"
        private String valueShift;     // "自信、野心膨胀"
    }

    public static class PowerShift {
        private String from;           // "被动受害者"
        private String to;             // "主动谋划者"
    }

    public static class OpenLoop {
        private String hook;           // "神秘人的真实身份"
        private int urgency;           // 1-10
    }
}
```

#### 提取时机（SummaryNode 扩展）

```java
// 在 SummaryNode.apply() 中，summary生成后追加：
emotionLedgerService.extractFromChapter(chapterContent, chapterNum, novelId);
// LLM提取四维度情感增量（结构化JSON输出）
// append到entries列表，不可变：只追加不修改
```

#### 注入上下文（ContextBuilderService Layer 4）

```
=== 情感账本（近5章） ===
第41章: 伤痕[失去师尊→悲痛、自责] 恩惠[获师尊遗剑→使命感] 权力[弟子→代掌门] 开钩[师尊遗言之谜|紧急度9]
第42章: 伤痕[同门背叛→愤怒、不信任] 恩惠[-] 权力[-] 开钩[叛徒幕后指使|紧急度8]
第43章: 伤痕[-] 恩惠[结识盟友→温暖] 权力[-] 开钩[-]
```

**与 ChapterSummary 的关系**: 不替代，而是并行注入。ChapterSummary 提供情节事实，EmotionLedger 提供情感驱动力。

---

## 4. Phase 2: T0 优先级保护 + CompletedBeats

### 4.1 TokenBudgetService 重构

**借鉴来源**: PlotPilot `ContextBudgetAllocator` 四层洋葱模型
**改动**: 修改现有 `TokenBudgetService.java`（100行 → 约180行）

#### 四层优先级

```java
public enum PriorityTier {
    T0_CRITICAL(0.20, false),    // 绝对保护，永不压缩
    T1_COMPRESSIBLE(0.30, true), // 可按比例压缩
    T2_DYNAMIC(0.35, true),      // 按水位线截断
    T3_SACRIFICIAL(0.15, true);  // 最先被牺牲，最低保障5%
}
```

#### Section-Tier 映射

| Section | 当前比例 | 新Tier | 说明 |
|---------|---------|--------|------|
| storyState (核心) | 6% | **T0** | 角色锚点、事实锁、世界观硬规则 |
| clueBook (活跃高优) | 4% | **T0** | 高优先级未回收线索 |
| characterState (当前角色) | 6% | **T0** | 本章出场角色的状态 |
| antiHallucination | 8% | **T0** | 反幻觉规则 |
| recentOutline | 10% | **T2** | 近期章节大纲 |
| arcSummary | 6% | **T1** | 卷级摘要 |
| novelOutline | 8% | **T1** | 总大纲 |
| styleCard | 5% | **T1** | 风格卡 |
| worldSetting | 4% | **T1** | 世界观 |
| emotionLedger | (新增) 4% | **T1** | 情感账本 |
| echoRecall | (新增) 3% | **T3** | 回响召回 |
| output | 39% | — | 输出空间不变 |

#### 预算压力级联

```
总需求 > 预算?
  → 先压T3（但保留最低5%）
  → 再压T2（按比例截断到水位线）
  → 再压T1（按比例压缩内容）
  → T0永不压缩，超出则报错告警
```

#### 其他改进

- `truncateToBudget` 改为句子边界感知截断（按 `。！？\n` 切分，保留完整句子）
- 未使用预算可回收（section 实际用量 < 分配量时，差额回流到 output）
- chars/token 比率从 1.2 修正为 1.5（适配中文）

### 4.2 CompletedBeatsService（已完成节拍）

**借鉴来源**: PlotPilot `MemoryEngine.COMPLETED_BEATS`
**存储**: MongoDB 新集合 `completed_beats`
**接入点**: 生成前注入 ContextBuilderService，生成后在 PostProcessNode 中提取

#### 数据模型

```java
@Document("completed_beats")
public class CompletedBeats {
    @Id
    private String id;
    private Long novelId;
    private List<BeatRecord> beats;

    public static class BeatRecord {
        private String beatId;         // 唯一标识
        private String summary;        // "林宇在比武大会击败张三，获得进入内门资格"
        private int chapterNum;        // 完成章节
        private List<String> charactersInvolved;
        private String plotThread;     // 所属剧情线
    }
}
```

#### 注入上下文

```
=== 已完成节拍 ===
[1] 林宇通过入门考核，成为外门弟子 (第2章, 线:成长线)
[2] 林宇与张三确立师徒关系 (第3章, 线:师徒线)
[3] 林宇在比武大会击败张三，获得内门资格 (第5章, 线:成长线)
[4] 魔域之战，赵六战死 (第7章, 线:魔域线)
```

#### 提取时机

在 `PostProcessNode` 中，章节保存后调用：

```java
completedBeatsService.extractFromChapter(chapterContent, chapterNum, novelId);
// LLM提取本章完成的剧情节拍
// 结构化输出: beat_id, summary, characters_involved, plot_thread
// append到beats列表
```

---

## 5. Phase 3: Echo Recall + 全局收敛沙漏

### 5.1 EchoRecallService（回响召回）

**借鉴来源**: PlotPilot `EchoRecall`
**存储**: MongoDB 新集合 `character_voice_samples` + Milvus 向量索引
**接入点**: RuleDrivenRetrievalService 新增 Rule 5，ContextBuilderService 注入

#### 数据模型

```java
@Document("character_voice_samples")
public class CharacterVoiceSample {
    @Id
    private String id;
    private Long novelId;
    private String characterName;
    private String dialogue;          // 角色原话
    private String context;           // 对话场景描述
    private int chapterNum;           // 出现章节
    private String emotion;           // 当时情感状态
    private String situation;         // "面对强敌时的宣言"
}
```

#### 存储时机

在 `ChapterPostProcessService` 中扩展：

```java
echoRecallService.extractVoiceSamples(chapterContent, chapterNum, novelId);
// LLM提取角色的经典台词/宣言/内心独白
// 存入character_voice_samples集合
// 同时生成embedding存入Milvus
```

#### 触发规则（RuleDrivenRetrievalService 新增 Rule 5）

```
Rule 5: 角色回响召回
  → 当前章大纲提到角色X时
  → 向量搜索该角色在早期章节(距今>15章)的经典台词
  → 召回1-2条最相关的语音样本
```

#### 注入上下文

```
【回响】林宇在第2章曾说过："我绝不向任何人低头。"
当时的他是一个天真的少年。如今第50章的他已历经沧桑，
再次面对类似抉择时，内心或许会闪回那个年轻的自己。
```

**与 RAG 的区别**: RAG 搜索章节摘要的事实内容（"发生了什么"），EchoRecall 搜索角色原话（"他说过什么"）。两者互补。

### 5.2 ConvergenceService（全局收敛沙漏）

**借鉴来源**: PlotPilot `Global Convergence Hourglass`
**存储**: 复用 StoryState，新增 `convergenceState` 字段
**接入点**: ContextBuilderService 注入，GraphDefinition 动态调整

#### 数据模型（嵌入 StoryState）

```java
public class ConvergenceState {
    private int currentChapter;
    private int totalChapters;
    private double progress;           // 0.0 ~ 1.0
    private String phase;              // OPENING / DEVELOPMENT / CONVERGENCE / FINALE
    private List<String> openHooks;    // 未闭合的叙事钩子
    private int chaptersRemaining;     // 距离完结还有多少章
}
```

#### 阶段切换逻辑

| 阶段 | 进度 | 行为 |
|------|------|------|
| OPENING | 0~25% | 侧重世界观建立、角色引入、基调设定 |
| DEVELOPMENT | 25~75% | 正常管线，冲突升级、支线展开 |
| CONVERGENCE | 75~90% | 加速伏笔回收、合并支线、提升紧张感 |
| FINALE | 90~100% | 强制闭合所有开放钩子、高潮收束 |

#### 注入上下文

```
【进度】当前第45章/共60章(75%) → 收束期
未闭合钩子: [1]神秘人身份(紧急度9) [2]师门阴谋(紧急度7) [3]林宇身世(紧急度6)
建议: 本阶段应加速回收伏笔，减少新支线引入
```

#### 管线行为调整

- **CONVERGENCE 阶段**: ValidationNode 增加"伏笔回收检查"，SelfReviewNode 增加"支线合并审查"
- **FINALE 阶段**: 强制注入所有未回收线索到上下文，审计增加"钩子闭合完整性"维度

---

## 6. 集成点清单

### 需要修改的现有文件

| 文件 | 改动内容 | Phase |
|------|---------|-------|
| `ContextBuilderService.java` | 新增 FactLock/EmotionLedger/CompletedBeats/EchoRecall/Convergence 注入 | 1,2,3 |
| `TokenBudgetService.java` | 重构为 T0 优先级分配 + 句子边界截断 + 预算回收 | 2 |
| `PostProcessNode.java` | 新增 FactLock 更新、EmotionLedger 提取、CompletedBeats 提取 | 1,2 |
| `StoryState.java` | 新增 `convergenceState` 字段 | 3 |
| `ChapterPostProcessService.java` | 新增 VoiceSample 提取调用 | 3 |
| `RuleDrivenRetrievalService.java` | 新增 Rule 5: EchoRecall 触发 | 3 |
| `GraphDefinition.java` | 在 audit_router 后插入 FactLockViolationNode | 1 |

### 新增文件

| 文件 | 类型 | Phase |
|------|------|-------|
| `FactLockService.java` / `FactLockServiceImpl.java` | Service | 1 |
| `FactLock.java` | Entity (MongoDB) | 1 |
| `DeathRecord.java` / `RelationshipLock.java` / `TimelineAnchor.java` | Value Object | 1 |
| `FactLockViolationNode.java` | Graph Node | 1 |
| `EmotionLedgerService.java` / `EmotionLedgerServiceImpl.java` | Service | 1 |
| `EmotionLedger.java` | Entity (MongoDB) | 1 |
| `CompletedBeatsService.java` / `CompletedBeatsServiceImpl.java` | Service | 2 |
| `CompletedBeats.java` | Entity (MongoDB) | 2 |
| `EchoRecallService.java` / `EchoRecallServiceImpl.java` | Service | 3 |
| `CharacterVoiceSample.java` | Entity (MongoDB) | 3 |
| `ConvergenceService.java` / `ConvergenceServiceImpl.java` | Service | 3 |
| `ConvergenceState.java` | Value Object | 3 |

### Graph 管线变更

**Phase 1**: 在 `audit_router` 之后、`summary` 之前插入 `FactLockViolationNode`

```
... → audit_router → [FactLockViolationNode] → summary → post_process
```

**Phase 2 / Phase 3**: 无管线结构变更，仅在现有 Node 中增加调用。

---

## 7. 数据流总览

```
生成前（ContextBuilderService）:
  ┌─ FactLock.buildSection() ─────────── T0 注入
  ├─ CompletedBeats.formatForPrompt() ── T0 注入
  ├─ EmotionLedger.formatForPrompt() ─── T1 注入
  ├─ ConvergenceService.formatForPrompt() T0 注入
  ├─ EchoRecall.formatForPrompt() ─────── T3 注入
  └─ [现有6层] ───────────────────────── 正常注入

生成后（PostProcessNode + ChapterPostProcessService）:
  ├─ FactLock.update() ────────────────── 更新事实锁
  ├─ FactLockViolationNode.detect() ───── 违规检测
  ├─ EmotionLedger.extract() ──────────── 提取情感增量
  ├─ CompletedBeats.extract() ─────────── 提取已完成节拍
  ├─ EchoRecall.extractVoiceSamples() ── 提取角色台词
  └─ ConvergenceService.update() ──────── 更新收敛状态
```

---

## 8. 风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| LLM提取不准确 | FactLock 误报/漏报 | 设计人工确认接口 + 置信度阈值 |
| Token超限 | T0内容超出20%预算 | 告警 + 自动降级到T1 |
| 新增LLM调用增加延迟 | 每章多2-3次LLM调用 | 异步执行 + 与现有提取合并prompt |
| MongoDB集合膨胀 | 大量append-only数据 | 定期压缩(复用现有20章压缩机制) |
| 向量索引增长 | EchoRecall的Milvus数据增长 | 仅索引经典台词，不索引所有对话 |

---

## 9. 成功标准

| 指标 | 当前基线 | Phase 1 目标 | Phase 2 目标 | Phase 3 目标 |
|------|---------|-------------|-------------|-------------|
| 事实违规率 | 未测量 | < 5% | < 3% | < 2% |
| 剧情重复率 | 未测量 | — | < 5% | < 3% |
| 长篇遗忘(>20章) | 未测量 | — | 0% | 0% |
| 情感一致性 | 未测量 | 显著提升 | 显著提升 | 显著提升 |
