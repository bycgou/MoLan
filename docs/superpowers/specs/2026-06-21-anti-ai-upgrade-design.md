# Anti-AI 味七层纵深防御升级设计

> 状态：自审通过，待用户审核
> 创建：2026-06-21
> 参考：PlotPilot（墨枢）Anti-AI 防御体系
> 实施目标：仅 Java 后端
> 分阶段策略：方案 B — 核心工程优先

---

## 1. 背景与动机

### 1.1 现状

墨渊当前的 Anti-AI 体系为 3 层防御：

| 层 | 机制 | 成本 |
|----|------|------|
| L1 | `AntiAICliches` 44 条黑名单注入 Prompt | 零 |
| L2 | `StyleTextAnalyzer` 30+ 量化指标 + `StyleCheckerService` 快检/深检 | 零/LLM |
| L3 | `SelfReviewNode` + 五维验证 + 质量评分 → 重试循环 | LLM |

### 1.2 问题

1. **Prompt 反向激活**：黑名单注入方式（"禁止 X"）在 Transformer Self-Attention 中会反向激活被禁止的 Token
2. **无生成中拦截**：写完一整章后才做检测，发现问题只能整章重写（2500+ 字），成本高
3. **一刀切禁止**：所有场景下所有黑名单词都禁，战斗场景中"呼吸一窒息"等合理生理反应也被误杀
4. **角色无行为锚定**：角色卡只有基本信息，缺少声线指纹/紧张习惯/反应模式，导致"所有人一个味道"
5. **规则库静态**：44 条规则不会随使用而增长，无法适应新的 AI 味模式
6. **无文风漂移监控**：长篇创作（50+ 章）中文风逐渐偏移无法发现

### 1.3 目标

构建七层纵深防御体系，将 AI 味问题的发现从"章后"提前到"生成中"，从"否定指令"改为"正向行为映射"，从"静态规则"进化为"自适应学习"。

---

## 2. 整体架构

### 2.1 七层防御体系

```
┌─────────────────────────────────────────────────────────────┐
│                    七层纵深防御体系                            │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  L1  正向行为映射     ─── "遇到场景Y执行Z" 替代 "禁止X"       │
│  L2  替换策略库       ─── 35+ 正则模式，8大分类               │
│  L3  场景化白名单     ─── 战斗/悬疑/告白等场景豁免            │
│  L4  角色状态锁       ─── 声线指纹+紧张习惯+反应模式+信息边界  │
│  L5  上下文配额管理   ─── 动态压缩 + Token预算分配（已有）     │
│  L6  流式拦截         ─── AC自动机扫描 + 分块回滚 + 中段刷新   │
│  L7  章后审计         ─── 量化指标 + LLM复审 + 趋势追踪       │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 与现有系统的关系

```
现有系统                              新增/改造
─────────                            ──────────
AntiAICliches (44条黑名单)      ──→  改造为正向行为映射 + 扩充为35+正则
StyleTextAnalyzer (30+指标)     ──→  保留，增加跨章节趋势追踪
StyleCheckerService (快检+深检)  ──→  保留，增加场景化白名单参数
WritingNode (流式生成)           ──→  嵌入AC自动机扫描 + 分块回滚
SelfReviewNode (LLM复审)        ──→  保留，增加角色状态锁注入
CharacterCard (角色数据)         ──→  扩展为四维状态向量
```

### 2.3 分阶段交付顺序

| Phase | 内容 | 改动范围 | 工作量 |
|-------|------|---------|--------|
| **P1** | 生成中流式拦截 | WritingNode + 新增 ACScanner + ChunkedBuffer | 5-7天 |
| **P2** | 正向行为映射 + 规则扩充 + 白名单 | AntiAICliches + WritingAgent prompt + 新增 AllowlistManager | 2-3天 |
| **P3** | 角色状态向量 + 文风漂移监控 | CharacterCard 扩展 + 新增 VoiceDriftService | 3-4天 |
| **P4** | 规则自适应学习 | 新增 AntiAILearningService | 2-3天 |

---

## 3. Phase 1 — 生成中流式拦截

### 3.1 核心机制

在 `WritingNode` 的流式输出过程中，每积累约 200 字就用 AC 自动机扫描一次。检测到 AI 味模式时：
- **critical 级别**：回滚当前 chunk，注入刷新指令，重新生成该 chunk
- **同类 warning ≥ 3 次**：同上
- **info 级别**：标记但不回滚，记入审计报告

### 3.2 流式拦截流程

```
LLM 流式输出
    │
    ▼
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│ ChunkBuffer  │────▶│ AC Scanner   │────▶│ 判断是否回滚  │
│ (200字/块)   │     │ (35+正则)    │     │              │
└──────────────┘     └──────────────┘     └──────┬───────┘
                                                  │
                                    ┌─────────────┼─────────────┐
                                    ▼             ▼             ▼
                                无问题         需回滚        仅标记
                                    │             │             │
                                    ▼             ▼             ▼
                              确认chunk    回滚+注入刷新    记入审计
                              继续生成     重新生成chunk    继续生成
```

### 3.3 新增类

#### `AntiAIClicheScanner` — AC 自动机扫描器

位置：`com.novel.system.ai.antiai.AntiAIClicheScanner`

```java
public class AntiAIClicheScanner {
    private List<CompiledPattern> patterns;

    // 从正则模式列表构建扫描器
    public AntiAIClicheScanner(List<ClichePatternDef> patternDefs);

    // 扫描单个 chunk
    public List<ScanResult> scanChunk(String text, int offset);

    // 判断是否需要中断
    // 条件：有 critical 命中，或同类 warning >= 3
    public boolean shouldInterrupt(List<ScanResult> results);
}
```

#### `ScanResult` — 扫描结果

位置：`com.novel.system.ai.antiai.ScanResult`

```java
public class ScanResult {
    String patternName;    // 模式名称
    String matchedText;    // 匹配的文本
    int position;          // 在全文中的位置
    String severity;       // critical / warning / info
    String category;       // 微表情 / 声线 / 比喻 / 情绪 / 句式
    String action;         // flag / rollback / replace
}
```

#### `ChunkedBufferScanner` — 分块缓冲扫描器

位置：`com.novel.system.ai.antiai.ChunkedBufferScanner`

```java
public class ChunkedBufferScanner {
    private int chunkSize = 200;
    private int maxRollbackAttempts = 3;
    private AntiAIClicheScanner scanner;
    private List<String> confirmedChunks = new ArrayList<>();
    private StringBuilder currentBuffer = new StringBuilder();

    // 追加文本，达到 chunkSize 时触发扫描
    // 返回 ChunkResult（含 needsRollback 标记），未触发扫描时返回 null
    public ChunkResult appendText(String text);

    // 刷新剩余缓冲区
    public ChunkResult flushRemaining();

    // 获取已确认的全部文本
    public String getConfirmedText();

    // 获取扫描统计
    public ChunkedScanStats getStats();
}

public class ChunkResult {
    int chunkIndex;
    String text;
    List<ScanResult> scanResults;
    boolean needsRollback;
    String rollbackReason;
}
```

#### `MidGenerationRefresh` — 中段刷新指令构建器

位置：`com.novel.system.ai.antiai.MidGenerationRefresh`

```java
public class MidGenerationRefresh {
    // 根据扫描结果构建刷新指令
    public String buildRefreshDirective(List<ScanResult> results);

    // 判断主要问题类别
    public String detectIssueCategory(List<ScanResult> results);
}
```

刷新指令模板：

| 类别 | 刷新指令核心内容 |
|------|----------------|
| 微表情过多 | "停止微表情，改用完整姿态变化或让对白传递情绪，下一段必须有感官锚点" |
| 比喻过多 | "停止比喻，改用体温/光线/触感等感官细节，让读者自己感受" |
| 声线标签过多 | "停止声线标签，改用对白标点和断句表现语气" |
| 情绪标签过多 | "停止直接情绪标签，改用动作暗示情绪" |
| 通用刷新 | "停止检测到的AI味模式，改用感官细节+动作+对白表达" |

### 3.4 WritingNode 改造

在 `ChapterGenerationChain.executeWritingChainStreaming()` 的 `StreamingChatResponseHandler.onPartialResponse()` 中嵌入分块扫描逻辑：

```java
// 伪代码
ChunkedBufferScanner chunkScanner = new ChunkedBufferScanner(200, 3, acScanner);
MidGenerationRefresh midRefresh = new MidGenerationRefresh();

onPartialResponse(token) -> {
    chunkBuffer.append(token);
    ChunkResult result = chunkScanner.appendText(token);

    if (result != null && result.needsRollback()) {
        // 1. 构建刷新指令
        String refresh = midRefresh.buildRefreshDirective(result.scanResults);
        // 2. 将刷新指令作为 system message 追加，调用 LLM 重新生成当前 chunk
        //    regenerateChunk 内部：将 confirmedText + refresh 拼接为新的 user prompt，
        //    调用 llmService.generate() 获取新 chunk（约 200-300 字）
        String newChunk = regenerateChunk(chunkScanner.getConfirmedText(), refresh);
        // 3. 通过 SseEmitter 推送回滚通知 + 新 chunk
        sseEmitter.send(SseEmitter.event()
            .name("rollback")
            .data(Map.of("charCount", result.text.length(), "reason", result.rollbackReason())));
        sseEmitter.send(SseEmitter.event()
            .name("token")
            .data(newChunk));
    } else if (result != null && result.rollbackAttemptsExhausted()) {
        // 单 chunk 重试 3 次仍失败：跳过该 chunk，记入审计报告，继续生成
        logger.warn("chunk {} 重试耗尽，跳过并继续", result.chunkIndex());
        auditLog.recordSkip(result);
    } else {
        // 正常推送 token
        sseEmitter.send(SseEmitter.event()
            .name("token")
            .data(token));
    }
}
```

### 3.5 与 GraphRunner 的集成

在 `ChapterGenerationChain` 的 token handler 中嵌入扫描（方式 A）：
- 改动范围集中于 `ChapterGenerationChain.executeWritingChainStreaming()`
- 在 `StreamingChatResponseHandler` 的 `onPartialResponse()` 中加入 chunk 缓冲和扫描
- GraphRunner 架构不需要改动

### 3.6 前端适配

新增 SSE event 类型 `"rollback"`：

```json
{
    "charCount": 186,
    "reason": "检测到 2 处严重AI味模式"
}
```

前端处理逻辑：
```javascript
eventSource.addEventListener('rollback', (e) => {
    const { charCount, reason } = JSON.parse(e.data);
    // 从已显示的文本中移除回滚的部分
    displayedText = displayedText.slice(0, -charCount);
    // 可选：显示提示 "正在优化写作风格..."
});
```

### 3.7 性能考量

| 指标 | 预估值 |
|------|--------|
| AC 扫描耗时（35+ 正则 × 200 字） | < 1ms |
| 内存开销（12 chunk × 2500 字章） | 可忽略 |
| 单次回滚额外 token 消耗 | 300-500 token |
| 每章平均回滚次数 | 1-2 次 |
| 每章额外成本 | 600-1000 token（约 0.3-0.5 元） |

---

## 4. Phase 2 — 正向行为映射 + 规则扩充 + 场景化白名单

### 4.1 正向行为映射

**核心改造**：将 `AntiAICliches.toPromptText()` 的"禁止列表"改为"正向行为映射"。

改造前（当前方式）：
```
严禁使用以下词汇：不禁、似乎、仿佛、眼神中闪过、嘴角微微上扬...
```

改造后（正向行为映射）：
```
【行为协议 — 当你遇到以下场景时，必须执行对应动作】

场景：角色情绪波动时
动作：通过以下方式之一映射：
  ① 对周遭物体的物理作用力改变
  ② 语速/停顿的改变
  ③ 体温/触感变化
  ④ 对话内容的偏移
  反例：'他感到非常愤怒'
  建议：'他端起杯子，又放下了。指节磕在桌面上，声音比他想的要响。'

场景：需要展现角色内在状态时
动作：写完整动作链而非面部快照
  反例：'嘴角勾起一丝冷笑'
  建议：'他往后靠了靠，把手插进口袋。'

场景：需要修辞增强时
动作：用感官细节替代比喻（体温/光线角度/衣料触感/具体距离）
  反例：'宛如阳光般温暖'
  建议：'她碰到他的手背。那片皮肤是热的。'

场景：需要表达情绪降级时
动作：强制执行至少一档的行为降级
  大哭→沉默、崩溃→转移话题、暴怒→说话变慢变清楚
  反例：'她崩溃地大哭起来'
  建议：'她笑了一下，然后低头系鞋带。系了两次才系好。'

场景：需要对比或转折时
动作：转为直接叙述、因果句或递进句
  反例：'他不是在害怕，而是在等待'
  建议：'他在等。手指按在桌面，一下，又一下。'

场景：高智商/冷静角色思考问题时
动作：思维必须包含直觉跳跃、经验碎片、犹豫或灵光一闪
  反例：'他分析变量1、变量2，得出结论：成功率73%'
  建议：'他总觉得哪里不对。想了两秒，突然明白了。'

场景：不同角色面对相同事件时
动作：反应必须贴着角色本人来写
  反例：'所有人都震惊地看着他'
  建议：'赵四的手下意识摸向腰间。小六往后退了半步。只有老陈没动，但他把茶杯放下了。'
```

**改动文件**：
- `AntiAICliches.java`：新增 `toPositiveFramingText()` 方法，保留 `findCliches()` 用于事后检测
- `WritingAgent.java`：`@SystemMessage` 改用正向行为映射文本
- `StyleAwareChatMemory.java`：合并正向行为映射到 system context

### 4.2 规则库扩充

从简单字符串匹配改为正则表达式，覆盖 8 大分类 35+ 模式：

| 分类 | 现有覆盖 | 扩充后 | 示例模式 |
|------|---------|--------|---------|
| 微表情 | 2 条 | 8 条 | `嘴角(勾起\|上扬\|扬起\|浮现\|翘起)` |
| 声线 | 0 条 | 8 条 | `带着.{1,8}(口吻\|语气)` |
| 比喻 | 1 条 | 6 条 | `(仿佛\|宛如\|犹如\|恰似).{1,15}(般\|一般\|似的)` |
| 生理性 | 0 条 | 2 条 | `生理性(泪水\|水雾\|液体)` |
| 情绪 | 1 条 | 2 条 | `(感到\|觉得\|心中)(非常\|极度\|无比)?(愤怒\|悲伤\|恐惧)` |
| 句式 | 0 条 | 2 条 | `不是[^。，？！]{1,20}(而是\|只是)` |
| 面部大忌 | 0 条 | 4 条 | `眸色一沉\|眼神暗了暗\|邪魅一笑` |
| 身体大忌 | 0 条 | 3 条 | `呼吸一滞\|四肢百骸\|青筋暴起` |

**新增文件**：`com.novel.system.ai.antiai.ClichePatternRegistry`

```java
public class ClichePatternRegistry {
    // 所有 35+ 正则模式定义
    public static final List<ClichePatternDef> ALL_PATTERNS = List.of(
        // 微表情
        new ClichePatternDef("嘴角(勾起|上扬|扬起|浮现|翘起)", "嘴角微表情", "critical", "微表情", "写完整姿态变化"),
        new ClichePatternDef("(眼中|眸中|目光中)闪过一丝", "眼神闪过", "critical", "微表情", "用对话或动作替代"),
        // ... 35+ 模式
    );
}
```

### 4.3 场景化白名单

新增 `AllowlistManager`：

位置：`com.novel.system.ai.antiai.AllowlistManager`

```java
public class AllowlistManager {
    private Map<String, AllowlistRule> rules;

    // 检查某模式在某场景中是否允许
    public boolean isAllowed(String sceneType, String category, String patternName);

    // 生成白名单 Prompt 文本
    public String generateAllowlistBlock(String sceneType);

    // 获取场景类型（从大纲/细纲中提取，匹配失败时返回 "default"）
    // 匹配策略：关键词匹配（战斗/打斗/厮杀 → battle，悬疑/推理 → suspense，
    //   恐怖/阴森 → horror，告白/表白/情愫 → confession，揭秘/反转/真相 → revelation）
    public String detectSceneType(String outline);
}
```

场景规则：

| 场景 | 允许的分类 | 密度限制 | 说明 |
|------|-----------|---------|------|
| default | 无 | 0/千字 | 所有 AI 味模式全禁 |
| battle | 生理性 | 1/千字 | 允许心跳/血压/肾上腺素描写 |
| suspense | 微表情 | 0.5/千字 | 允许瞳孔/目光微动 |
| horror | 生理性+环境 | 1.5/千字 | 允许生理恐惧+氛围描写 |
| confession | 生理性+微表情 | 1/千字 | 允许心跳加速+嘴唇微表情 |

### 4.4 改动文件清单

| 文件 | 改动类型 | 说明 |
|------|---------|------|
| `AntiAICliches.java` | 改造 | 新增 `toPositiveFramingText()`，保留 `findCliches()` |
| `WritingAgent.java` | 改造 | `@SystemMessage` 改用正向行为映射 |
| `StyleCardService.java` | 改造 | `buildSystemStyleRules()` 增加场景参数 |
| `StyleCheckerService.java` | 改造 | `quickCheck()` 白名单命中不扣分 |
| `AllowlistManager.java` | 新增 | 场景化白名单管理 |
| `PositiveFramingRules.java` | 新增 | 正向行为映射规则库 |
| `ClichePatternRegistry.java` | 新增 | 35+ 正则模式注册中心 |

---

## 5. Phase 3 — 角色状态向量 + 文风漂移监控

### 5.1 角色状态向量

#### 数据模型

位置：`com.novel.system.entity.CharacterStateVector`

```java
public class CharacterStateVector {
    String characterId;

    // 维度1：声线指纹
    VoicePrint voicePrint;

    // 维度2：紧张习惯
    NervousHabit nervousHabit;

    // 维度3：反应模式
    ReactionPattern reactionPattern;

    // 维度4：信息边界
    InfoBoundary infoBoundary;
}

public class VoicePrint {
    String sentenceLengthPreference;  // short / medium / long
    String vocabularyStyle;           // colloquial / literary / formal / slang
    List<String> commonExpressions;   // 口头禅
    String punctuationHabits;         // sparse / normal / dense
    List<String> toneMarkers;         // 语气词（啊、嘛、呢、吧）
}

public class NervousHabit {
    String primary;             // 主习惯：如"摸后脑勺"
    String secondary;           // 次习惯：如"抿嘴唇"
    String stressIndicator;     // 极度压力：如"指甲掐进掌心"
    String relaxationIndicator; // 放松：如"肩膀明显松弛下来"
}

public class ReactionPattern {
    String fightResponse;       // confront / evade / freeze / manipulate
    String emotionalExpression; // action / words / suppress / explosive
    String decisionSpeed;       // impulsive / calculated / deferred
    String trustTendency;       // naive / cautious / trusting / hostile
}

public class InfoBoundary {
    List<String> knownFacts;    // 角色已知的事实
    List<String> unknownFacts;  // 角色不知道的事
    List<String> misbeliefs;    // 角色错误相信的事
}
```

#### 自动提取流程

```
CharacterCard (现有)
    │
    ▼
LLM 提取（一次性，创建角色时或首次需要时）
    │
    ▼
CharacterStateVector (新增)
    │
    ▼
存储到 MongoDB（与 CharacterCard 关联）
    │
    ▼
生成时注入 Prompt（"角色状态锁"文本块）
```

提取 Prompt 核心指令：
```
根据以下角色设定，提取角色的行为模式。
要求输出 JSON 格式，包含 voicePrint、nervousHabit、reactionPattern 三个字段。
每个字段的值应该基于角色的性格、背景、经历来推断，而不是通用模板。
```

#### 生成时注入格式

```
━━━ 角色状态锁：{角色名} ━━━
- 身体：{physicalState}
- 情绪底色：{emotionalBaseline}
- 紧张习惯：{nervousHabit.primary}
  次习惯：{nervousHabit.secondary}
- 声线指纹：{voicePrint.description}
  口头禅：{voicePrint.commonExpressions}
- 反应模式：{reactionPattern.description}

锁定规则：
1. 声线指纹不可偏离
2. 紧张习惯必须一致——遇到压力时{角色名}会{nervousHabit.primary}
3. 信息边界不可突破——{角色名}不知道的事不能写他知道
4. 身体状态有惯性——不会突然恢复或恶化
```

#### 改动文件

| 文件 | 改动类型 | 说明 |
|------|---------|------|
| `CharacterStateVector.java` | 新增 | 四维状态向量数据模型 |
| `CharacterStateService.java` | 改造 | 新增 `extractStateVector()` 方法 |
| `CharacterCard.java` | 改造 | 新增 `stateVector` 字段 |
| `ContextBuilderService.java` | 改造 | 组装上下文时注入角色状态锁 |
| `WritingAgent.java` | 改造 | `@SystemMessage` 新增 `{{characterStateLock}}` 占位符 |
| `SelfReviewNode.java` | 改造 | 复审时注入角色状态锁，检查 OOC |

### 5.2 文风漂移监控

#### 新增 `VoiceDriftService`

位置：`com.novel.system.service.style.VoiceDriftService`

```java
public class VoiceDriftService {
    // 计算章节风格评分（零LLM成本，复用 StyleTextAnalyzer）
    public ChapterStyleScore scoreChapter(String novelId, int chapterNumber, String content);

    // 计算与基准风格的相似度（加权余弦相似度）
    public double computeSimilarity(ChapterStyleScore current, StyleFingerprint baseline);

    // 检查是否触发漂移告警（连续5章相似度 < 0.75）
    public boolean checkDriftAlert(String novelId);

    // 获取漂移报告
    public DriftReport getDriftReport(String novelId);

    // 更新基准风格（每10章滑动窗口更新）
    public void updateBaseline(String novelId);
}
```

#### 评分维度

复用 `StyleTextAnalyzer` 的指标，取 5 个关键维度：

| 维度 | 指标 | 权重 | 说明 |
|------|------|------|------|
| 句式多样性 | sentenceDiversityIndex | 25% | 变异系数，AI写作句式过于均匀 |
| 对话比例 | dialogueRatio | 20% | 过高或过低都不自然 |
| 感官丰富度 | sensoryDensity (5类平均) | 20% | 缺乏感官细节是典型AI味 |
| 段落节奏 | shortParagraphRatio + alternationRate | 20% | 长短交替是好文风的标志 |
| 修辞密度 | metaphorDensity | 15% | 过多比喻是AI味特征 |

#### 基准建立与告警

- **基准建立**：前 5 章的加权平均值作为基准风格指纹
- **相似度计算**：当前章节与基准的加权余弦相似度，归一化到 [0, 1]
- **告警条件**：连续 5 章相似度 < 0.75 时触发告警
- **基准更新**：每 10 章自动更新基准（滑动窗口，取最近 10 章平均）

#### 存储

```sql
CREATE TABLE chapter_style_scores (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    novel_id VARCHAR(64) NOT NULL,
    chapter_number INT NOT NULL,
    sentence_diversity DOUBLE,
    dialogue_ratio DOUBLE,
    sensory_density DOUBLE,
    paragraph_rhythm DOUBLE,
    metaphor_density DOUBLE,
    similarity_score DOUBLE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_novel_chapter (novel_id, chapter_number)
);
```

#### 改动文件

| 文件 | 改动类型 | 说明 |
|------|---------|------|
| `VoiceDriftService.java` | 新增 | 文风漂移监控服务 |
| `ChapterStyleScore.java` | 新增 | 风格评分实体 |
| `PostProcessNode.java` | 改造 | 章节保存后异步更新风格评分 |
| `StyleTextAnalyzer.java` | 改造 | 新增 `computeStyleVector()` 方法返回5维向量 |

---

## 6. Phase 4 — 规则自适应学习

### 6.1 核心机制

从章后审计数据中自动发现新的 AI 味模式。三种学习策略：

| 策略 | 触发条件 | 输出 |
|------|---------|------|
| 频次学习 | 某个未在规则库中的表达在 3+ 章节中反复出现 | 候选模式 |
| 共现学习 | 某个表达与已知 AI 味模式频繁共现（上下文30字内） | 候选模式 |
| 严重性自适应 | 某个 warning 模式在 >50% 章节中出现 | 建议升级为 critical |

### 6.2 学习流程

```
章节生成 → 章后审计(L7) → 审计结果
                              │
                              ▼
                    AntiAILearningService.analyzeChapterAudit()
                              │
                    ┌─────────┼─────────┐
                    ▼         ▼         ▼
                频次学习    共现学习    结构化模板学习
                    │         │         │
                    └─────────┼─────────┘
                              ▼
                    候选模式（pending 状态，置信度 >= 0.5）
                              │
                              ▼
                    人工审核（approve / reject）
                              │
                              ▼
                    已审核模式 → 导入 ClichePatternRegistry
```

### 6.3 结构化模板学习

自动检测高频句式结构：

| 模板 | 正则 | 说明 |
|------|------|------|
| "X地Y" | `([一-鿿]{1,2})地([一-鿿]{1,4})` | 副词+动词结构 |
| "不禁X" | `不禁([一-鿿]{1,6})` | 不禁系列 |
| "缓缓X" | `缓缓([一-鿿]{1,4})` | 缓缓系列 |
| "微微X" | `微微([一-鿿]{1,4})` | 微微系列 |
| "暗自X" | `暗自([一-鿿]{1,4})` | 暗自系列 |
| "一X就Y" | `一([一-鿿]{1,2})就([一-鿿]{1,6})` | 一X就Y结构 |

### 6.4 新增类

位置：`com.novel.system.ai.antiai.AntiAILearningService`

```java
public class AntiAILearningService {
    // 分析章节审计结果，提取潜在新模式
    public List<LearnedPattern> analyzeChapterAudit(
        String novelId, int chapterNumber, String content, List<ScanResult> hits
    );

    // 生成学习报告
    public LearningReport generateLearningReport(String novelId);

    // 人工审核
    public void approvePattern(String patternKey);
    public void rejectPattern(String patternKey);

    // 获取已审核通过的模式（可导入 ClichePatternRegistry）
    public List<LearnedPattern> getApprovedPatterns();

    // 计算严重性调整建议
    public Map<String, String> computeSeverityAdjustments(
        String novelId, Map<String, Integer> chapterHitCounts, int totalChapters
    );
}

public class LearnedPattern {
    String pattern;           // 正则表达式
    String name;              // 模式名称
    int hitCount;             // 命中次数
    List<Integer> chapterOccurrences;  // 出现在哪些章节
    double confidence;        // 置信度 0-1
    String status;            // pending / approved / rejected
    String suggestedSeverity; // 建议严重级别
    String suggestedCategory; // 建议分类
}
```

### 6.5 存储

```sql
CREATE TABLE learned_patterns (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    novel_id VARCHAR(64),
    pattern TEXT NOT NULL,
    name VARCHAR(128),
    hit_count INT DEFAULT 0,
    chapter_occurrences JSON,
    confidence DOUBLE DEFAULT 0.0,
    status VARCHAR(16) DEFAULT 'pending',
    suggested_severity VARCHAR(16),
    suggested_category VARCHAR(32),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_novel_status (novel_id, status)
);
```

### 6.6 改动文件

| 文件 | 改动类型 | 说明 |
|------|---------|------|
| `AntiAILearningService.java` | 新增 | 自适应学习服务 |
| `LearnedPattern.java` | 新增 | 学习到的模式数据模型 |
| `PostProcessNode.java` | 改造 | 章节保存后异步调用 `analyzeChapterAudit()` |
| `ClichePatternRegistry.java` | 改造 | `getApprovedPatterns()` 返回的模式自动加入扫描器 |
| `StyleCheckerService.java` | 改造 | `quickCheck()` 动态加载已审核的新模式 |
| 新增 REST API | 新增 | `GET /api/anti-ai/patterns` + `POST /api/anti-ai/patterns/{id}/approve` |

---

## 7. 完整改动文件清单

### 新增文件

| 文件 | Phase | 说明 |
|------|-------|------|
| `ai/antiai/AntiAIClicheScanner.java` | P1 | AC 自动机扫描器 |
| `ai/antiai/ScanResult.java` | P1 | 扫描结果 |
| `ai/antiai/ChunkedBufferScanner.java` | P1 | 分块缓冲扫描器 |
| `ai/antiai/ChunkResult.java` | P1 | chunk 扫描结果 |
| `ai/antiai/MidGenerationRefresh.java` | P1 | 中段刷新指令构建器 |
| `ai/antiai/ClichePatternRegistry.java` | P2 | 35+ 正则模式注册中心 |
| `ai/antiai/ClichePatternDef.java` | P2 | 模式定义 |
| `ai/antiai/AllowlistManager.java` | P2 | 场景化白名单管理 |
| `ai/antiai/AllowlistRule.java` | P2 | 白名单规则 |
| `ai/antiai/PositiveFramingRules.java` | P2 | 正向行为映射规则库 |
| `entity/CharacterStateVector.java` | P3 | 四维状态向量 |
| `entity/VoicePrint.java` | P3 | 声线指纹 |
| `entity/NervousHabit.java` | P3 | 紧张习惯 |
| `entity/ReactionPattern.java` | P3 | 反应模式 |
| `entity/InfoBoundary.java` | P3 | 信息边界 |
| `service/style/VoiceDriftService.java` | P3 | 文风漂移监控 |
| `entity/ChapterStyleScore.java` | P3 | 风格评分实体 |
| `ai/antiai/AntiAILearningService.java` | P4 | 自适应学习 |
| `ai/antiai/LearnedPattern.java` | P4 | 学习到的模式 |

### 改造文件

| 文件 | Phase | 改动 |
|------|-------|------|
| `ChapterGenerationChain.java` | P1 | `executeWritingChainStreaming()` 嵌入分块扫描 |
| `WritingNode.java` | P1 | 处理 rollback SSE event |
| `StreamingChapterController.java` | P1 | 前端 rollback event 适配 |
| `AntiAICliches.java` | P2 | 新增 `toPositiveFramingText()` |
| `WritingAgent.java` | P2 | `@SystemMessage` 改用正向行为映射 |
| `StyleAwareChatMemory.java` | P2 | 合并正向行为映射到 system context |
| `StyleCardService.java` | P2 | 增加场景参数 |
| `StyleCheckerService.java` | P2+P4 | 白名单命中不扣分 + 动态加载新模式 |
| `CharacterStateService.java` | P3 | 新增 `extractStateVector()` |
| `CharacterCard.java` | P3 | 新增 `stateVector` 字段 |
| `ContextBuilderService.java` | P3 | 注入角色状态锁 |
| `SelfReviewNode.java` | P3 | 注入角色状态锁 + OOC 检查 |
| `PostProcessNode.java` | P3+P4 | 异步更新风格评分 + 调用学习服务 |
| `StyleTextAnalyzer.java` | P3 | 新增 `computeStyleVector()` |

### 新增数据库表

| 表名 | Phase | 说明 |
|------|-------|------|
| `chapter_style_scores` | P3 | 章节风格评分 |
| `learned_patterns` | P4 | 学习到的 AI 味模式 |

### 新增 REST API

| API | Phase | 说明 |
|-----|-------|------|
| `GET /api/anti-ai/scan` | P1 | 扫描文本返回命中结果 |
| `GET /api/anti-ai/patterns` | P4 | 查看候选/已审核模式 |
| `POST /api/anti-ai/patterns/{id}/approve` | P4 | 审核通过模式 |
| `POST /api/anti-ai/patterns/{id}/reject` | P4 | 审核拒绝模式 |
| `GET /api/anti-ai/drift/{novelId}` | P3 | 获取文风漂移报告 |

---

## 8. 预期收益

| 指标 | 当前 | 升级后 |
|------|------|--------|
| AI 味模式检测覆盖 | 44 条简单匹配 | 35+ 正则 + 自适应学习 |
| 问题发现时机 | 章后（2500+ 字后） | 生成中（200 字内） |
| 回滚成本 | 整章 2500+ 字 | 单 chunk ~200 字 |
| Prompt 注入方式 | 黑名单（反向激活） | 正向行为映射 |
| 场景感知 | 无 | 5 种场景白名单 |
| 角色差异化 | 基本信息 | 四维行为锚点 |
| 长篇风格一致性 | 无监控 | 跨章节漂移检测 |
| 规则库进化 | 静态 | 自适应学习 |
