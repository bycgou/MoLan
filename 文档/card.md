---

# 墨韵 AI 文风卡片系统设计文档

> **文档版本**: v1.0
> **创建时间**: 2026-04-21
> **核心目标**: 解决 AI 生成小说"AI味重"的问题，通过文风卡片系统实现风格克隆与复用

---

## 目录

- [一、问题分析](#一问题分析)
- [二、方案概述](#二方案概述)
- [三、核心概念：StyleCard（文风卡片）](#三核心概念stylecard文风卡片)
- [四、Phase 1：Anti-AI Prompt 优化（立即可用）](#四phase-1anti-ai-prompt-优化立即可用)
- [五、Phase 2：文风卡片系统（中期）](#五phase-2文风卡片系统中期)
- [六、Phase 3：风格注入写作引擎](#六phase-3风格注入写作引擎)
- [七、质量检查闭环](#七质量检查闭环)
- [八、前端交互设计](#八前端交互设计)
- [九、实施计划](#九实施计划)
- [十、风险与注意事项](#十风险与注意事项)
- [十一、方案对比总结](#十一方案对比总结)

---

## 一、问题分析

### 1.1 当前系统 Prompt 模式

```
小说总纲 + 章节大纲 + 上下文 + "请生成第X章正文"
```

### 1.2 AI 味的 6 个典型症状

| 症状       | 示例                                       | 根因                   |
| ---------- | ------------------------------------------ | ---------------------- |
| 高频套话   | "不禁""似乎""眼神中闪过一丝""嘴角微微上扬" | LLM 选最高概率词       |
| 结构雷同   | 动作→心理→对话→环境，机械循环              | 缺乏段落节奏约束       |
| 情绪公式化 | "心中涌起一股...""一股暖流涌上心头"        | 没有情绪表达多样性要求 |
| 对话工整   | 每句都语法完整，没有口语/省略/语气词       | 缺乏人物语言风格设定   |
| 比喻泛滥   | 大量明喻"像...一样""如同..."               | 缺乏禁止滥用修辞的指令 |
| 缺乏"脏感" | 文字过于干净，没有作者个人癖好             | 没有文风指纹注入       |

### 1.3 根因

LLM 在没有任何风格约束时，会输出"最大概率"的文本，这就是"AI味"——最安全、最通用、最无聊的表达。

---

## 二、方案概述

### 2.1 核心思路

**不要让 AI"学会风格"，让 AI"戴着原文的镣铐跳舞"。**

把原文片段当作锚点，让生成过程始终被原文的统计特征约束，而不是靠一条 prompt 让 AI"自由发挥"。

### 2.2 三阶段路线

```
Phase 1（1-2天）：Anti-AI Prompt 优化
  → 改 prompt 模板，立即减少 AI 味
  
Phase 2（3-5天）：文风卡片系统
  → 用户上传小说 → AI分析 → 生成可复用的 StyleCard
  
Phase 3（2-3天）：风格注入写作引擎
  → 写作时调用 StyleCard → 生成后自动检查
```

### 2.3 架构总览

```
用户上传小说文本
       │
       ▼
┌─────────────────┐
│   文风分析引擎    │
│  程序化统计 + LLM │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│   StyleCard      │  ← 独立可复用的风格资产
│  (结构化规则 +   │
│   自然语言描述 + │
│   原文片段)      │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│   写作引擎       │  ← 风格注入 + 量化约束
│  (StyleCard     │
│   注入到 Prompt) │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│   质量检查       │  ← 快速检查 + 深度检查
│  (闭环修复)      │
└─────────────────┘
```

---

## 三、核心概念：StyleCard（文风卡片）

一个 StyleCard 就是一份**可执行的风格说明书**，包含两部分：

- **结构化规则**（机器可读，用于自动检查）
- **自然语言描述 + 原文片段**（注入 prompt，引导模型）

### 3.1 卡片复用模型

```
一本《诡秘之主》上传
       │
       ▼
  AI 分析（1-2次调用）
       │
       ▼
  生成一张 StyleCard
       │
       ├── 用户A 写克苏鲁小说 → 调用这张卡
       ├── 用户B 写蒸汽朋克小说 → 也调用这张卡
       └── 用户C 觉得好用 → 复制到自己的库
```

### 3.2 卡片完整结构

```yaml
# ─── 基础信息 ───
card_id: "sc_xxxxx"
name: "诡秘之主风格"
source_novel: "诡秘之主"
author: "爱潜水的乌贼"
created_by: "user_001"
tags: ["克苏鲁", "蒸汽朋克", "悬疑", "第三人称"]
is_public: true

# ─── 节奏规则（机器可读）───
rhythm:
  avg_sentence_length: 18        # 平均句长（字）
  short_sentence_ratio: 0.35     # 短句占比（<15字）
  long_sentence_max: 45          # 长句上限
  avg_paragraph_length: 80       # 平均段落长度
  short_paragraph_ratio: 0.25    # 短段落占比（<40字）

# ─── 对话规则 ───
dialogue:
  ratio: 0.30                    # 对话占全文比例
  avg_line_length: 12            # 平均对白长度
  style: "简洁留白型，角色话少，潜台词多"
  quirks:
    - "常用反问句"
    - "对话中夹杂内心独白（破折号引导）"
    - "角色各有口头禅"

# ─── 描写规则 ───
description:
  dominant_sense: "视觉"
  sense_distribution:
    visual: 0.50
    auditory: 0.20
    tactile: 0.15
    olfactory: 0.10
    gustatory: 0.05
  metaphor_type: "偏好用神秘学/仪式/占卜相关的比喻"
  adjective_density: "低"
  detail_approach: "用1个精确细节代替3个模糊形容词"

# ─── 情绪表达方式 ───
emotion:
  anger: "通过克制的外部反应（手指收紧、声音变平）而非爆发"
  fear: "环境描写先行（光线变暗、声音消失），再写心理"
  joy: "极少直接写喜悦，通过对话节奏加快暗示"
  sadness: "用日常细节的荒诞感传递（比如悲伤时注意到墙上的裂缝）"
  forbidden:
    - "禁止'心中涌起一股...'"
    - "禁止直接写'他很愤怒/悲伤/开心'"
    - "禁止用'一股暖流'类表达"

# ─── 段落结构模式 ───
structure:
  scene_transition: "硬切，不解释，靠读者自己拼接"
  chapter_ending: "停在悬念处，不总结"
  flashback_style: "无过渡直接闪回，用时态变化区分"
  pov_discipline: "严格第三人称限知，不越界"

# ─── 禁用 & 偏好 ───
forbidden:
  words:
    - "不禁"
    - "一股暖流"
    - "嘴角微微上扬"
    - "空气中弥漫着"
  structures:
    - "XX心想：..."
    - "他感到一种...的感觉"
    - "不禁感叹道"

preferred:
  words:
    - "沉吟"
    - "念头"
    - "注视"
    - "寂静"
  structures:
    - "短句独立成段制造停顿感"
    - "用环境描写收尾（不总结情绪）"
    - "对话后紧跟一个不相关的动作描写"

# ─── 原文样本（few-shot 核弹）───
samples:
  - type: "悬疑氛围"
    text: |
      克莱恩推开橡木门的时候，听到了齿轮转动的声音。
      不是从钟表传来的。
      是从墙壁里面。
      他停在门口，数了三秒。
      声音消失了。
      门自动关上了。
    annotation: "短句堆叠制造紧张感，感官先行，不解释原因"

  - type: "日常对话"
    text: |
      "你吃了吗。"
      "吃了。"
      "吃的什么。"
      "忘了。"
      她把咖啡杯放下，看向窗外。楼下的梧桐树掉了一地叶子。
      "秋天了。"她说。
    annotation: "极简对话，用环境细节填充沉默，情绪不点破"

  - type: "战斗场景"
    text: |
      他没有躲。
      子弹穿过左肩的时候，他闻到了火药和铁锈混在一起的味道。
      然后他笑了。
      因为对方只剩一颗子弹了。
    annotation: "短句为主，感官描写穿插动作，转折出人意料"

  - type: "心理描写"
    text: |
      他知道自己应该害怕。
      但他只是盯着那扇门，脑子里想的是今天早饭吃的煎蛋好像有点咸。
      也许该换一家店。
    annotation: "用荒诞的日常念头代替直接写恐惧，更真实"

# ─── 生成指令模板 ───
generation_template: |
  你正在以「{card_name}」的风格写作。
  
  核心规则：
  - 句长控制在{avg_sentence_length}字左右，短句占比{short_sentence_ratio}
  - 对话占比约{ratio}，风格：{dialogue_style}
  - 情绪永远不直接命名，参考以下方式：{emotion_summary}
  - 段落节奏：{paragraph_rule}
  
  以下是风格参考片段，模仿其句式、节奏、质感：
  {samples_formatted}
  
  重要：是模仿风格质感，不是复制情节。故事必须100%原创。
```

---

## 四、Phase 1：Anti-AI Prompt 优化（立即可用）

### 4.1 核心原则

**用正面示范替代反面禁止。** 给模型看"好的长什么样"，比告诉它"坏的长什么样"有效 10 倍。Few-shot 示范 > 黑名单约束。

### 4.2 修改 SystemPrompt

```
【文风核心规则】

1. 情绪永远不直接命名，用以下方式传递：
   - 一个多余的动作（他把杯子转了三圈才放下）
   - 一个环境细节（窗外的蝉突然不叫了）
   - 一段不合时宜的对话（"今天天气不错。"他看着她说。）

2. 对话必须口语化：
   - 允许省略主语、语气词（"嗯""哈""切"）
   - 允许打断（用破折号）、吞字、方言
   - 禁止每句都语法完整

3. 段落节奏多样化：
   - 至少30%的段落≤2行（50字以内）
   - 禁止连续3段以上都是"一段描写+一段对话+一段心理"
   - 战斗/紧张场景：短句为主，1-3句一段
   - 日常/对话场景：可以长段落，但对话必须单独成段

4. 比喻克制：
   - 每千字不超过2个明喻（"像""如同""仿佛"）
   - 优先用具体细节代替比喻

5. 心理描写不要"XX想道"，而是通过动作/细节暗示情绪：
   ✗ "他心想：这下完了"
   ✓ "他盯着屏幕，手指停在键盘上，一个字也敲不出来"

【反面示范——以下都是AI味，绝对禁止】
✗ "不禁""似乎""仿佛""眼神中闪过""嘴角微微上扬"
✗ "心中涌起一股""一股暖流涌上心头""一种...的感觉"
✗ "空气中弥漫着""不约而同""若有所思""意味深长"
✗ "难以名状""若隐若现""与此同时""话音刚落"
```

### 4.3 文风参考片段注入

在 `ContextBuilderService.buildWritingContext()` 中，新增 `styleReference` 字段：

- 从该小说已有的前几章中，抽取最能代表作者风格的 2-3 段（500-800字）
- 注入到 prompt 中作为风格参考

```
【文风参考——请模仿此风格写作】
以下片段来自本书前文，请模仿其句式、节奏、修辞习惯：

---
{从已有章节中抽取的代表性段落}
---

写作时参考上述片段的：
- 句子长短比例
- 段落密度
- 对话风格
- 修辞偏好
```

### 4.4 文件改动

| 文件                              | 改动                                                      |
| --------------------------------- | --------------------------------------------------------- |
| `WritingAgent.java`               | SystemPrompt 新增反 AI 套话规则 + 段落节奏要求            |
| `ContextBuilderService.java`      | 新增 `buildStyleReference()` 方法，从已有章节抽取文风片段 |
| `StreamingChapterController.java` | userPrompt 中注入文风参考                                 |

### 4.5 工作量

**1-2天**

---

## 五、Phase 2：文风卡片系统（中期）

### 5.1 数据模型

```sql
-- 文风卡片主表
CREATE TABLE style_card (
  card_id         VARCHAR(32) PRIMARY KEY,
  name            VARCHAR(100) NOT NULL,
  source_novel    VARCHAR(200),
  author_name     VARCHAR(100),
  created_by      VARCHAR(32) NOT NULL,
  
  -- 结构化规则（JSON）
  rhythm_rules    JSON,
  dialogue_rules  JSON,
  description_rules JSON,
  emotion_rules   JSON,
  structure_rules JSON,
  forbidden_rules JSON,
  preferred_rules JSON,
  
  -- 自然语言描述（用于prompt注入）
  style_description TEXT,
  
  -- 生成指令模板
  generation_template TEXT,
  
  -- 元信息
  tags            VARCHAR(500),
  is_public       BOOLEAN DEFAULT false,
  usage_count     INT DEFAULT 0,
  avg_rating      DECIMAL(2,1),
  
  status          INT DEFAULT 0,    -- 0=分析中, 1=可用, 2=分析失败
  create_time     DATETIME,
  update_time     DATETIME
);

-- 原文样本片段
CREATE TABLE style_sample (
  sample_id       VARCHAR(32) PRIMARY KEY,
  card_id         VARCHAR(32) NOT NULL,
  sample_type     VARCHAR(50),      -- 悬疑/对话/战斗/心理/描写
  content         TEXT NOT NULL,
  annotation      VARCHAR(500),     -- AI对这段的风格注释
  sort_order      INT,
  
  INDEX idx_card(card_id)
);

-- 用户的文风卡片库（多对多，用户可以收藏别人的卡片）
CREATE TABLE user_style_library (
  id              VARCHAR(32) PRIMARY KEY,
  user_id         VARCHAR(32) NOT NULL,
  card_id         VARCHAR(32) NOT NULL,
  is_favorite     BOOLEAN DEFAULT false,
  add_time        DATETIME,
  
  UNIQUE INDEX idx_user_card(user_id, card_id)
);

-- 小说绑定的文风卡片
CREATE TABLE novel_style_binding (
  id              VARCHAR(32) PRIMARY KEY,
  novel_id        VARCHAR(32) NOT NULL,
  card_id         VARCHAR(32) NOT NULL,
  bind_time       DATETIME,
  
  UNIQUE INDEX idx_novel(novel_id)
);
```

### 5.2 风格分析引擎

#### 5.2.1 分析策略

- **采样**：前3章 + 中间3章 + 后3章，每章取500字，不分析全文
- **分析方式**：程序化统计（精确量化）+ LLM 定性描述（1次调用）
- **缓存**：分析结果持久化，不重复分析

#### 5.2.2 程序化提取（精确、可量化）

```java
public class StyleMetrics {
    // 句长分布
    double avgSentenceLength;
    double shortSentenceRatio;    // <15字的句子占比
    int maxSentenceLength;
    
    // 段落分布
    double avgParagraphLength;
    double shortParagraphRatio;   // <60字的段落占比
    
    // 对话
    double dialogueRatio;         // 对话占全文比例
    double avgDialogueLength;
    
    // 标点习惯
    int ellipsisCount;            // 省略号数量
    int emDashCount;              // 破折号数量
    int exclamationCount;         // 感叹号数量
    
    // 感官词密度
    double visualDensity;
    double auditoryDensity;
    double tactileDensity;
    
    // 高频特色词（TF-IDF）
    List<String> distinctiveWords;
}
```

#### 5.2.3 LLM 分析 Prompt（1次调用）

```
你是一位资深文学评论家和写作风格分析师。

请分析小说《{novelName}》的写作风格。

以下是程序化提取的统计数据：
```json
{metricsJson}
```

以下是小说的代表性片段（前中后各取部分章节）：
---
{sampledText}
---

请基于以上数据和原文，生成一份完整的「文风卡片」，返回以下JSON格式：

{
  "rhythm": { "description": "...", "rule": "..." },
  "dialogue": { "style": "...", "quirks": [...], "rule": "..." },
  "description": { "dominant_sense": "...", "metaphor_type": "...", "rule": "..." },
  "emotion": { "anger": "...", "fear": "...", "joy": "...", "sadness": "...", "forbidden": [...] },
  "structure": { "scene_transition": "...", "chapter_ending": "...", "pov": "..." },
  "forbidden": { "words": [...], "structures": [...] },
  "preferred": { "words": [...], "structures": [...] },
  "samples": [{ "type": "...", "text": "...", "annotation": "..." }],
  "generation_template": "..."
}

重要：
1. 分析必须基于原文实际特征，不要套模板
2. samples必须是原文真实片段，不要自己编
3. forbidden要具体——列出这个作者真正不会用的词和句式
4. generation_template要可执行——拿到就能直接用来指导写作
```

#### 5.2.4 核心服务

```java
@Service
public class StyleCardService {

    /**
     * 从上传的小说文本创建文风卡片
     * 核心流程：采样 → 分析 → 生成卡片
     */
    public StyleCard createFromNovel(String userId, String novelName, String fullText) {
        
        // 1. 创建卡片记录（状态=分析中）
        StyleCard card = new StyleCard();
        card.setCardId(IdUtil.fastSimpleUUID());
        card.setName(novelName + "风格");
        card.setSourceNovel(novelName);
        card.setCreatedBy(userId);
        card.setStatus(0);
        styleCardMapper.insert(card);

        // 2. 异步分析
        CompletableFuture.runAsync(() -> {
            try {
                analyzeAndFill(card.getCardId(), novelName, fullText);
            } catch (Exception e) {
                card.setStatus(2);
                styleCardMapper.updateById(card);
                log.error("风格分析失败: {}", card.getCardId(), e);
            }
        });

        return card;
    }

    /**
     * 分析核心逻辑
     */
    private void analyzeAndFill(String cardId, String novelName, String text) {
        
        // Step 1: 程序化提取量化指标
        StyleMetrics metrics = extractMetrics(text);
        
        // Step 2: 采样文本片段
        List<String> sampledTexts = sampleChapters(text, 9, 500);
        String combinedSample = String.join("\n---\n", sampledTexts);
        
        // Step 3: 一次LLM调用，生成完整卡片
        String analysisPrompt = buildAnalysisPrompt(novelName, metrics, combinedSample);
        
        StyleCardAnalysis result = llmClient.structuredCall(
            analysisPrompt, 
            StyleCardAnalysis.class,
            0.3  // 低温度保证分析稳定性
        );
        
        // Step 4: 存储
        StyleCard card = styleCardMapper.selectById(cardId);
        // ... 填充所有字段
        card.setStatus(1);
        styleCardMapper.updateById(card);
        
        // 存储样本片段
        for (int i = 0; i < result.getSamples().size(); i++) {
            StyleSample sample = new StyleSample();
            sample.setCardId(cardId);
            sample.setContent(result.getSamples().get(i).getText());
            sample.setAnnotation(result.getSamples().get(i).getAnnotation());
            styleSampleMapper.insert(sample);
        }
    }

    /**
     * 获取用于写作的风格注入内容
     */
    public String buildStyleInjection(String cardId) {
        StyleCard card = styleCardMapper.selectById(cardId);
        List<StyleSample> samples = styleSampleMapper.selectByCardId(cardId);
        
        StringBuilder sb = new StringBuilder();
        sb.append("## 你正在以「").append(card.getName()).append("」的风格写作\n\n");
        sb.append(card.getGenerationTemplate()).append("\n\n");
        
        sb.append("### 风格参考片段\n\n");
        for (StyleSample sample : samples) {
            sb.append("【").append(sample.getSampleType()).append("场景参考】\n");
            sb.append("```\n").append(sample.getContent()).append("\n```\n");
            sb.append("风格特征：").append(sample.getAnnotation()).append("\n\n");
        }
        
        return sb.toString();
    }
}
```

### 5.3 文件改动

| 层级   | 新建文件                       | 修改文件                     |
| ------ | ------------------------------ | ---------------------------- |
| 数据库 | `style_card.sql`               | —                            |
| 后端   | `StyleCardService.java`        | —                            |
| 后端   | `StyleCardController.java`     | —                            |
| 后端   | `StyleCard.java` (entity)      | —                            |
| 后端   | `StyleSample.java` (entity)    | —                            |
| 后端   | `StyleCardMapper.java` + XML   | —                            |
| 后端   | `StyleSampleMapper.java` + XML | —                            |
| 后端   | —                              | `WritingAgent.java`          |
| 后端   | —                              | `ContextBuilderService.java` |

### 5.4 工作量

**3-5天**

---

## 六、Phase 3：风格注入写作引擎

### 6.1 Prompt 架构设计

分离"风格锚点"和"创作指令"，风格参考放在 user message 而不是 system prompt。

```
┌─────────────────────────────────────────────┐
│  System Prompt（固定不变）                    │
│  "你是小说写作助手，负责根据风格参考和创作    │
│   大纲生成小说正文..."                        │
│  + 核心写作规则                               │
└─────────────────────────────────────────────┘

┌─────────────────────────────────────────────┐
│  User Message（每次生成动态构建）             │
│                                              │
│  Part 1: 风格锚点（原文片段）                 │
│  "以下是目标风格的参考片段：                  │
│   --- 片段1 ---                              │
│   {原文最有代表性的段落}                      │
│   --- 片段2 ---                              │
│   {原文另一段}"                              │
│                                              │
│  Part 2: 量化约束                             │
│  "本次生成要求：                              │
│   - 平均句长控制在15-20字                     │
│   - 对话占比约30%                             │
│   - 每3段至少有1段≤2行的短段落"              │
│                                              │
│  Part 3: 创作任务                             │
│  "故事大纲：...                               │
│   本章情节：...                               │
│   请生成第X章正文，约2000字"                  │
└─────────────────────────────────────────────┘
```

### 6.2 WritingAgent 集成

```java
@Service
public class WritingAgent {

    public Flux<String> generateChapterStream(
            String novelId,
            ChapterOutline outline,
            String styleCardId
    ) {
        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(novelId, outline, styleCardId);
        return llmClient.streamChat(systemPrompt, userPrompt);
    }

    private String buildUserPrompt(String novelId, ChapterOutline outline, String styleCardId) {
        StringBuilder sb = new StringBuilder();
        
        // Part 1: 风格锚点
        if (styleCardId != null) {
            String styleInjection = styleCardService.buildStyleInjection(styleCardId);
            sb.append(styleInjection).append("\n\n");
        }
        
        // Part 2: 故事上下文
        sb.append("## 故事信息\n");
        sb.append("总纲：").append(outline.getSynopsis()).append("\n");
        sb.append("本章：").append(outline.getChapterOutline()).append("\n");
        sb.append("人物状态：").append(outline.getCharacterStates()).append("\n");
        
        // Part 3: 上下文（前文摘要）
        sb.append("\n## 前文摘要\n");
        sb.append(outline.getPreviousContext()).append("\n");
        
        // Part 4: 生成指令
        sb.append("\n## 生成要求\n");
        sb.append("请生成第").append(outline.getChapterNum()).append("章正文，约");
        sb.append(outline.getTargetWordCount()).append("字。\n");
        
        if (styleCardId != null) {
            sb.append("\n⚠️ 严格遵循上方的风格要求写作。");
            sb.append("每写完一段，自查：是否符合风格参考片段的句式和节奏？");
        }
        
        return sb.toString();
    }

    private String buildSystemPrompt() {
        return """
        你是专业的小说写作助手。
        
        ## 核心原则
        1. 用具体的动作和细节传递情绪，永远不直接命名情绪
        2. 对话要口语化，允许省略、打断、语气词
        3. 段落长短交替，制造阅读呼吸感
        4. 环境描写服务于氛围，不堆砌无意义的景色
        5. 每个场景结束时停在最有张力的地方
        
        ## 输出格式
        直接输出正文，不要加章节标题以外的任何标记。
        不要输出大纲、分析、注释。
        """;
    }
}
```

### 6.3 工作量

**1-2天**

---

## 七、质量检查闭环

### 7.1 快速检查（程序化，零 LLM 成本）

```java
public StyleCheckResult quickCheck(String generatedText, StyleCard card) {
    StyleMetrics genMetrics = extractMetrics(generatedText);
    StyleCardRules rules = card.getRules();
    
    List<String> issues = new ArrayList<>();
    double score = 100.0;
    
    // 检查句长偏离
    double sentenceDiff = Math.abs(
        genMetrics.getAvgSentenceLength() - rules.getRhythm().getAvgSentenceLength()
    );
    if (sentenceDiff > 8) {
        issues.add("句长偏离：目标" + rules.getRhythm().getAvgSentenceLength() 
                    + "字，实际" + genMetrics.getAvgSentenceLength() + "字");
        score -= 15;
    }
    
    // 检查对话比例
    double dialogueDiff = Math.abs(
        genMetrics.getDialogueRatio() - rules.getDialogue().getRatio()
    );
    if (dialogueDiff > 0.15) {
        issues.add("对话比例偏离");
        score -= 10;
    }
    
    // 检查黑名单词汇（这个作者不会用的词）
    for (String word : rules.getForbidden().getWords()) {
        if (generatedText.contains(word)) {
            issues.add("出现禁用词：" + word);
            score -= 5;
        }
    }
    
    // 检查AI套话
    for (String cliche : AI_CLICHES) {
        if (generatedText.contains(cliche)) {
            issues.add("出现AI套话：" + cliche);
            score -= 8;
        }
    }
    
    // 检查段落节奏
    if (genMetrics.getShortParagraphRatio() < 0.1) {
        issues.add("短段落太少，缺乏节奏变化");
        score -= 10;
    }
    
    return StyleCheckResult.builder()
        .score(Math.max(0, score))
        .issues(issues)
        .pass(score >= 70)
        .build();
}
```

### 7.2 深度检查（调 LLM，用户主动触发）

```java
public StyleCheckResult deepCheck(String generatedText, StyleCard card) {
    String prompt = """
    以下是AI生成的小说片段和目标风格要求。
    请检查风格匹配度。
    
    目标风格：%s
    
    参考片段：
    %s
    
    生成文本：
    %s
    
    检查并返回JSON：
    {
      "score": 0-100,
      "issues": ["具体问题描述"],
      "suggestions": ["修改建议"],
      "best_match_paragraph": "写得最像原文风格的段落",
      "worst_match_paragraph": "偏离最大的段落"
    }
    """.formatted(
        card.getStyleDescription(),
        formatSamples(card),
        generatedText
    );
    
    return llmClient.structuredCall(prompt, StyleCheckResult.class, 0.2);
}
```

### 7.3 闭环流程

```
生成正文 → 快速检查 → 通过？
                        │
                   ┌────┴────┐
                   │ Yes     │ No
                   │         │
                   ▼         ▼
                 输出     找出偏离最大的段落
                          → 单独重写该段落
                          → 再次检查
```

---

## 八、前端交互设计

### 8.1 文风卡片库页面

```
┌──────────────────────────────────────────────────────┐
│  🎨 我的文风卡片库                          [+ 创建]  │
│                                                      │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐           │
│  │ 诡秘之主  │  │ 活着风格  │  │ 三体风格  │           │
│  │ 克苏鲁·悬疑│  │ 现实·苦难 │  │ 硬科幻   │           │
│  │ 短句·留白  │  │ 平静叙事  │  │ 冷峻·宏大│           │
│  │ ⭐4.8     │  │ ⭐4.5    │  │ ⭐4.7    │           │
│  │ 128人使用  │  │ 89人使用  │  │ 203人使用 │           │
│  │ [使用] [预览]│  │ [使用] [预览]│  │ [使用] [预览]│          │
│  └──────────┘  └──────────┘  └──────────┘           │
│                                                      │
│  ── 创建新卡片 ──────────────────────────────────     │
│  ① 上传小说文本（支持txt/粘贴）                       │
│  ② AI分析中...（进度条）                              │
│  ③ 风格预览：                                         │
│     ┌────────────────────────────────┐               │
│     │ 节奏：⚡ 短句快节奏            │               │
│     │ 对话：💬 简洁留白型            │               │
│     │ 感官：👁️ 偏视觉               │               │
│     │ 情绪：🎭 克制型，用动作暗示    │               │
│     │                                │               │
│     │ [查看完整报告] [编辑调整]       │               │
│     │ [保存到我的库] [公开分享]       │               │
│     └────────────────────────────────┘               │
└──────────────────────────────────────────────────────┘
```

### 8.2 写作时选择风格

```
┌──────────────────────────────────────────────────────┐
│  📖 创建新小说                                        │
│                                                      │
│  小说名称：[________________]                         │
│  故事类型：[玄幻 ▼]                                  │
│  写作风格：[选择文风卡片 ▼]                           │
│            ┌─────────────────────────┐               │
│            │ ○ 不使用特定风格        │               │
│            │ ● 诡秘之主风格 ⭐4.8    │               │
│            │ ○ 活着风格 ⭐4.5        │               │
│            │ ○ 三体风格 ⭐4.7        │               │
│            │ ○ 自定义风格...         │               │
│            │ ────────────────────    │               │
│            │ 📎 上传小说创建新风格   │               │
│            └─────────────────────────┘               │
│                                                      │
│  选中风格预览：                                       │
│  ┌────────────────────────────────┐               │
│  │ 短句为主，对话简洁留白，         │               │
│  │ 情绪克制，用环境和动作暗示...     │               │
│  │                                │               │
│  │ 参考片段：                      │               │
│  │ "他推开橡木门的时候，           │               │
│  │  听到了齿轮转动的声音..."       │               │
│  └────────────────────────────────┘               │
│                                                      │
│              [开始创作]                               │
└──────────────────────────────────────────────────────┘
```

---

## 九、实施计划

### 9.1 详细排期

| 阶段    | 内容                                | 时间     | 产出                         |
| ------- | ----------------------------------- | -------- | ---------------------------- |
| Phase 1 | Anti-AI Prompt 优化                 | Day 1-2  | 修改 SystemPrompt，效果验证  |
| Phase 2 | 数据库建表 + 实体/Mapper            | Day 3    | `style_card.sql`，Java实体类 |
| Phase 3 | StyleCardService（CRUD + 上传处理） | Day 4    | 上传接口，文本分段处理       |
| Phase 4 | 风格分析引擎                        | Day 5-6  | 程序化提取 + LLM分析         |
| Phase 5 | 风格注入写作引擎                    | Day 7    | WritingAgent集成             |
| Phase 6 | 质量检查                            | Day 8    | StyleChecker                 |
| Phase 7 | 前端集成                            | Day 9-10 | 卡片库页面 + 创建流程        |
| Phase 8 | 联调测试                            | Day 11   | 用3-5本小说验证效果          |

### 9.2 验证标准

Phase 1 验证：生成同一章节的前后对比，检查 AI 套话出现频率是否下降。

Phase 2-3 验证：

- 上传《诡秘之主》→ 生成 StyleCard → 用该卡片写新小说 → 对比无风格版本
- 检查项：句长分布、对话比例、AI套话数量、风格匹配度评分

---

## 十、风险与注意事项

### 10.1 LLM 调用成本

- 分析一次 StyleCard 约消耗 1-2 万 token（采样后）
- 建议：分析结果缓存，不重复分析
- 导入大文本时做采样，不分析全文

### 10.2 风格模仿的局限性

- LLM 只能模仿"表面风格"（词汇、句式、节奏），无法模仿"深层风格"（思想深度、叙事哲学）
- 模仿效果取决于导入文本的质量和数量（建议至少5万字）
- 用户期望管理：是"风格类似"，不是"水平相当"

### 10.3 版权问题

- 导入他人作品仅供"文风分析"，不存储/传播原文
- 生成内容必须 100% 原创，不得复制导入小说的情节
- 在功能说明中明确免责条款

### 10.4 文本处理性能

- 大文件（>5MB）需要分块处理
- 分析时做章节采样，不分析全文
- 使用 MongoDB 或 TEXT 字段存储导入文本，避免 MySQL 大字段问题

### 10.5 风格"漂移"

- 写着写着就回 AI 味了——通过质量检查闭环解决
- 每生成一段，做一次风格一致性检查
- 偏离阈值时自动重写不达标的段落

---

## 十一、方案对比总结

### 11.1 与 Hermes 方案的对比

| 维度     | Hermes 方案            | 本方案                 |
| -------- | ---------------------- | ---------------------- |
| 风格载体 | 绑定在小说上的 profile | 独立可复用的 StyleCard |
| 分析方式 | 3次 LLM 调用           | 1次调用 + 程序化统计   |
| 约束方式 | 黑名单禁用词           | 正面示范 + 原文片段    |
| 质量检查 | 无                     | 快速检查 + 深度检查    |
| 复用性   | 只能给一本小说用       | 卡片可分享、可复用     |
| 可进化   | 静态                   | 用户使用后可评分、迭代 |

### 11.2 核心理念

> **Hermes 的风格是"属性"，本方案的风格是"资产"。**
>
> 属性跟着对象走，资产可以独立存在、被引用、被复用。
>
> **用正面示范替代反面禁止。** 给模型看"好的长什么样"，比告诉它"坏的长什么样"有效 10 倍。
>
> **AI味的本质是"正确但无趣"。好小说是"不正确但真实"的。**
>
> 解决方案不是让 AI 写得更"好"，而是让它学会**犯对的错**——该省略时省略，该啰嗦时啰嗦，该不说清楚的时候闭嘴。

---

## 附录

### A. 程序化提取的感官词库

```java
// 视觉词
static final String[] VISUAL_WORDS = {
    "看见", "看到", "颜色", "光", "暗", "亮", "红", "白", "黑", "影",
    "闪烁", "刺眼", "昏暗", "明亮", "漆黑"
};

// 听觉词
static final String[] AUDITORY_WORDS = {
    "听到", "声音", "响", "静", "吵", "喊", "叫", "沉默",
    "轰鸣", "低语", "尖叫", "寂静", "嗡嗡"
};

// 触觉词
static final String[] TACTILE_WORDS = {
    "摸", "冷", "热", "硬", "软", "粗糙", "光滑", "痛",
    "冰凉", "滚烫", "刺痛", "麻木"
};

// 嗅觉词
static final String[] OLFACTORY_WORDS = {
    "闻", "气味", "味道", "香", "臭", "腥", "刺鼻", "芬芳"
};

// 味觉词
static final String[] GUSTATORY_WORDS = {
    "尝", "甜", "苦", "酸", "咸", "辣", "涩", "甘"
};
```

### B. AI 套话黑名单（用于快速检查）

```java
static final String[] AI_CLICHES = {
    "不禁", "似乎", "仿佛", "眼神中闪过", "嘴角微微上扬",
    "心中涌起一股", "一股暖流", "一种...的感觉", "空气中弥漫着",
    "不约而同", "若有所思", "意味深长", "难以名状", "若隐若现",
    "与此同时", "话音刚落", "沉默片刻", "微微一怔", "不禁感叹",
    "不禁涌上心头", "一股莫名的", "嘴角勾起一抹", "眼底闪过一丝"
};
```