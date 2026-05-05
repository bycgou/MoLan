# 墨韵文风卡片系统 - 可行性分析与改进计划

> **分析日期**: 2026-04-21
> **分析范围**: card.md + STYLE_IMITATION_PLAN.md 两份文档 + MoYun现有架构
> **核心目标**: 解决AI生成小说"AI味太重"问题

---

## 一、文档分析总结

### 1.1 两份文档的关系

```
card.md (更完善，可执行)
├── 核心概念: StyleCard（可复用的风格资产）
├── 数据模型: style_card + style_sample + user_style_library
├── 分析引擎: 程序化统计(60%) + LLM定性分析(40%)
├── 写作注入: 风格锚点 + 量化约束 + 原文片段
└── 质量检查: 快速检查(零成本) + 深度检查(LLM)

STYLE_IMITATION_PLAN.md (更简洁，侧重流程)
├── 方案一: Anti-AI Prompt 优化（立即可用）
├── 方案二: 用户文风指纹系统
└── 方案三: 小说模仿功能（导入→分析→生成）
```

### 1.2 共同核心思路

两份文档的核心理念一致：

1. **"戴着原文的镣铐跳舞"** — 用原文片段锚定风格，而非让AI自由发挥
2. **正面示范 > 反面禁止** — few-shot示例比黑名单有效10倍
3. **程序化 + LLM混合** — 量化指标精确，定性分析灵活

### 1.3 AI味的6个典型症状

| 症状 | 示例 | 根因 |
|------|------|------|
| 高频套话 | "不禁""似乎""眼神中闪过一丝""嘴角微微上扬" | LLM选最高概率词 |
| 结构雷同 | 动作→心理→对话→环境，机械循环 | 缺乏段落节奏约束 |
| 情绪公式化 | "心中涌起一股...""一股暖流涌上心头" | 没有情绪表达多样性要求 |
| 对话工整 | 每句都语法完整，没有口语/省略/语气词 | 缺乏人物语言风格设定 |
| 比喻泛滥 | 大量明喻"像...一样""如同..." | 缺乏禁止滥用修辞的指令 |
| 缺乏"脏感" | 文字过于干净，没有作者个人癖好 | 没有文风指纹注入 |

---

## 二、可行性评估

### 2.1 技术可行性 ✅ 完全可行

| 维度 | 现状 | 支持程度 |
|------|------|----------|
| AI框架 | LangChain4j 0.35.0 | ✅ 支持@SystemMessage动态注入、STRUCTURED模式、工具调用 |
| 数据库 | MySQL + MongoDB | ✅ MySQL存结构化数据，MongoDB存大文本/样本 |
| 缓存 | Redis | ✅ 可缓存分析结果，避免重复分析 |
| Agent架构 | WritingAgent + ContextBuilderService | ✅ 可直接扩展，注入风格上下文 |
| 前端 | Vue 3 + Element Plus | ✅ 支持文件上传、进度条、卡片展示 |

### 2.2 架构兼容性 ✅ 高度兼容

**现有架构 vs 文风卡片系统：**

```
现有流程：
ContextBuilderService.buildWritingContext()
    → 三层记忆 + RAG检索 + 人物状态 + 伏笔
    → 返回 contextPrompt

文风卡片扩展后：
ContextBuilderService.buildWritingContext()
    → 三层记忆 + RAG检索 + 人物状态 + 伏笔  (原有)
    → + StyleCard注入 (新增)
    → 返回 contextPrompt + styleInjection
```

**改动点最小化：**
- 新增：StyleCardService、StyleCardController、数据表
- 修改：ContextBuilderService（新增buildStyleInjection方法）、WritingAgent（SystemMessage增强）

### 2.3 大文本处理 ✅ 有解

用户提到"上传的小说可能是几十万字"，文档中已有解决方案：

| 方案 | 实现 | 适用场景 |
|------|------|----------|
| 采样分析 | 前3章+中间3章+后3章，每章500字 | 分析阶段，减少LLM调用 |
| 分块上传 | 前端分块上传，后端合并 | 大文件上传 |
| MongoDB存储 | 存储导入文本，避免MySQL大字段 | 持久化存储 |
| 程序化提取 | 句长分布、对话比例等统计 | 零LLM成本的量化分析 |

**Token消耗估算：**
- 分析一次StyleCard：约1-2万token（采样后）
- 写作时注入：约2000-3000token（参考片段+规则）
- 总体可控，且分析结果可缓存复用

### 2.4 风险评估

| 风险 | 严重程度 | 缓解措施 |
|------|----------|----------|
| LLM调用成本 | 中 | 分析结果缓存，采样分析，程序化提取60% |
| 风格漂移 | 中 | 质量检查闭环，快速检查零成本 |
| 版权问题 | 高 | 不存储原文，仅分析风格特征，免责条款 |
| 用户期望过高 | 中 | 明确"风格类似"≠"水平相当"，管理预期 |

---

## 三、改进计划（基于MoYun现有架构）

### 3.1 Phase 1：Anti-AI Prompt 优化（立即可用，1-2天）

**目标：** 不改架构，只改prompt，立即减少AI味

**改动文件：**

| 文件 | 改动内容 |
|------|----------|
| `WritingAgent.java` | SystemMessage新增反AI套话规则 + 段落节奏要求 |
| `ContextBuilderService.java` | 新增buildStyleReference()，从已有章节抽取文风片段 |

**具体改动：**

```java
// WritingAgent.java - SystemMessage增强
@SystemMessage("""
    你是一位专业的网络小说作家，正在创作小说。
    
    【文风铁律——违反任何一条均视为写作失败】
    1. 禁止使用以下高频AI套话（全文出现即失败）：
       "不禁""似乎""仿佛""眼神中闪过""嘴角微微上扬""心中涌起一股"
       "一种...的感觉""空气中弥漫着""不约而同""若有所思"
    2. 对话必须口语化：允许省略主语、语气词、打断、吞字
    3. 段落节奏：至少30%的段落≤2行（50字以内）
    4. 比喻克制：每千字不超过2个明喻
    5. 心理描写：用动作/细节暗示，禁止"XX想道"
    
    【原有铁律保持不变...】
    """)
```

```java
// ContextBuilderService.java - 新增方法
public String buildStyleReference(String novelId) {
    // 从已有章节抽取2-3段代表性段落（500-800字）
    // 注入到prompt中作为风格参考
}
```

**验证方法：** 生成同一章节的前后对比，检查AI套话出现频率

---

### 3.2 Phase 2：文风卡片核心功能（3-5天）

#### 3.2.1 数据库建表

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
  annotation      VARCHAR(500),
  sort_order      INT,
  INDEX idx_card(card_id)
);

-- 用户的文风卡片库
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

#### 3.2.2 核心服务实现

**新增文件：**

| 文件 | 职责 |
|------|------|
| `StyleCard.java` | 实体类 |
| `StyleSample.java` | 样本实体 |
| `StyleCardMapper.java` | Mapper接口 |
| `StyleCardService.java` | 核心业务逻辑 |
| `StyleCardController.java` | REST API |
| `StyleMetrics.java` | 程序化统计指标 |

**StyleCardService核心逻辑：**

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
        card.setStatus(0);
        styleCardMapper.insert(card);

        // 2. 异步分析
        CompletableFuture.runAsync(() -> {
            analyzeAndFill(card.getCardId(), novelName, fullText);
        });

        return card;
    }
    
    /**
     * 分析核心逻辑
     */
    private void analyzeAndFill(String cardId, String novelName, String text) {
        // Step 1: 程序化提取量化指标
        StyleMetrics metrics = extractMetrics(text);
        
        // Step 2: 采样文本片段（前3章+中间3章+后3章）
        List<String> sampledTexts = sampleChapters(text, 9, 500);
        
        // Step 3: 一次LLM调用，生成完整卡片
        StyleCardAnalysis result = llmClient.structuredCall(
            buildAnalysisPrompt(novelName, metrics, sampledTexts),
            StyleCardAnalysis.class,
            0.3  // 低温度保证分析稳定性
        );
        
        // Step 4: 存储
        fillCardFromAnalysis(cardId, result);
    }
    
    /**
     * 程序化提取（零LLM成本）
     */
    private StyleMetrics extractMetrics(String text) {
        StyleMetrics metrics = new StyleMetrics();
        // 句长分布
        metrics.setAvgSentenceLength(calcAvgSentenceLength(text));
        metrics.setShortSentenceRatio(calcShortSentenceRatio(text));
        // 段落分布
        metrics.setAvgParagraphLength(calcAvgParagraphLength(text));
        // 对话比例
        metrics.setDialogueRatio(calcDialogueRatio(text));
        // 标点习惯
        metrics.setEllipsisCount(countOccurrences(text, "…"));
        metrics.setEmDashCount(countOccurrences(text, "——"));
        // 感官词密度
        metrics.setVisualDensity(calcSenseDensity(text, VISUAL_WORDS));
        return metrics;
    }
}
```

#### 3.2.3 风格分析Prompt

```java
private String buildAnalysisPrompt(String novelName, StyleMetrics metrics, List<String> samples) {
    return """
    你是一位资深文学评论家和写作风格分析师。
    
    请分析小说《%s》的写作风格。
    
    以下是程序化提取的统计数据：
    ```json
    %s
```

    以下是小说的代表性片段（前中后各取部分章节）：
    ---
    %s
    ---
    
    请基于以上数据和原文，生成一份完整的「文风卡片」，返回以下JSON格式：
    
    {
      "rhythm": { "description": "...", "rule": "..." },
      "dialogue": { "style": "...", "quirks": [...], "rule": "..." },
      "description": { "dominant_sense": "...", "metaphor_type": "...", "rule": "..." },
      "emotion": { "anger": "...", "fear": "...", "joy": "...", "sadness": "...", "forbidden": [...] },
      "forbidden": { "words": [...], "structures": [...] },
      "preferred": { "words": [...], "structures": [...] },
      "samples": [{ "type": "...", "text": "...", "annotation": "..." }],
      "generation_template": "..."
    }
    
    重要：
    1. 分析必须基于原文实际特征，不要套模板
    2. samples必须是原文真实片段，不要自己编
    3. forbidden要具体——列出这个作者真正不会用的词和句式
    """.formatted(novelName, JSON.toJSONString(metrics), String.join("\n---\n", samples));
}
```

---

### 3.3 Phase 3：风格注入写作引擎（2-3天）

#### 3.3.1 WritingAgent增强

**方案：使用LangChain4j的动态SystemMessage**

```java
// WritingAgent.java - 接口定义保持不变
public interface WritingAgent {
    @SystemMessage("${systemPrompt}")  // 支持动态注入
    String generateChapter(@UserMessage String userPrompt);
}

// ChapterGenerationChain.java - 调用时动态构建
public String executeWritingChain(String novelId, int chapterNum, String outline, 
                                   String contextPrompt, String styleCardId, String llmConfigId) {
    
    // 构建基础SystemPrompt
    String baseSystemPrompt = buildBaseSystemPrompt();
    
    // 如果有文风卡片，注入风格约束
    String finalSystemPrompt = baseSystemPrompt;
    if (styleCardId != null) {
        String styleInjection = styleCardService.buildStyleInjection(styleCardId);
        finalSystemPrompt = baseSystemPrompt + "\n\n" + styleInjection;
    }
    
    // 创建WritingAgent
    WritingAgent writingAgent = AiServices.builder(WritingAgent.class)
            .chatLanguageModel(langChainConfig.createChatModel(llmConfig, LangChainConfig.TaskType.CREATIVE))
            .tools(toolProvider.getAllTools())
            .build();
    
    // 构建userPrompt
    String userPrompt = buildUserPrompt(novelId, chapterNum, outline, contextPrompt);
    
    return writingAgent.generateChapter(userPrompt);
}
```

#### 3.3.2 风格注入内容格式

```java
// StyleCardService.java
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
```

---

### 3.4 Phase 4：质量检查闭环（2天）

#### 3.4.1 快速检查（零LLM成本）

```java
@Service
public class StyleChecker {
    
    // AI套话黑名单
    private static final String[] AI_CLICHES = {
        "不禁", "似乎", "仿佛", "眼神中闪过", "嘴角微微上扬",
        "心中涌起一股", "一种...的感觉", "空气中弥漫着"
    };
    
    public StyleCheckResult quickCheck(String generatedText, StyleCard card) {
        StyleMetrics genMetrics = extractMetrics(generatedText);
        List<String> issues = new ArrayList<>();
        double score = 100.0;
        
        // 检查句长偏离
        double sentenceDiff = Math.abs(
            genMetrics.getAvgSentenceLength() - card.getRhythmRules().getAvgSentenceLength()
        );
        if (sentenceDiff > 8) {
            issues.add("句长偏离：目标" + card.getRhythmRules().getAvgSentenceLength() 
                        + "字，实际" + genMetrics.getAvgSentenceLength() + "字");
            score -= 15;
        }
        
        // 检查黑名单词汇
        for (String word : card.getForbiddenRules().getWords()) {
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
        
        return StyleCheckResult.builder()
            .score(Math.max(0, score))
            .issues(issues)
            .pass(score >= 70)
            .build();
    }
}
```

#### 3.4.2 深度检查（用户主动触发）

```java
public StyleCheckResult deepCheck(String generatedText, StyleCard card) {
    String prompt = """
    以下是AI生成的小说片段和目标风格要求。请检查风格匹配度。
    
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

---

### 3.5 Phase 5：前端集成（2-3天）

#### 3.5.1 文风卡片库页面

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
└──────────────────────────────────────────────────────┘
```

#### 3.5.2 创建文风卡片流程

```
1. 点击「创建新卡片」
2. 输入卡片名称 + 上传小说文本（支持txt/粘贴，大文件分块上传）
3. 点击「分析文风」→ 显示进度条（分析中...）
4. 分析完成 → 展示风格报告卡片：
   - 节奏：⚡ 短句快节奏
   - 对话：💬 简洁留白型
   - 感官：👁️ 偏视觉
   - 情绪：🎭 克制型，用动作暗示
5. 用户确认 → 保存到我的库
```

#### 3.5.3 写作时选择风格

```
创建新小说时：
  写作风格：[选择文风卡片 ▼]
            ┌─────────────────────────┐
            │ ○ 不使用特定风格        │
            │ ● 诡秘之主风格 ⭐4.8    │
            │ ○ 活着风格 ⭐4.5        │
            │ ○ 三体风格 ⭐4.7        │
            │ ────────────────────    │
            │ 📎 上传小说创建新风格   │
            └─────────────────────────┘
```

---

## 四、文件改动清单

### 4.1 后端新增文件

| 文件路径 | 职责 |
|----------|------|
| `entity/mysql/StyleCard.java` | 文风卡片实体 |
| `entity/mysql/StyleSample.java` | 样本实体 |
| `mapper/StyleCardMapper.java` | 卡片Mapper |
| `mapper/StyleSampleMapper.java` | 样本Mapper |
| `mapper/xml/StyleCardMapper.xml` | 卡片SQL |
| `mapper/xml/StyleSampleMapper.xml` | 样本SQL |
| `service/style/StyleCardService.java` | 核心业务逻辑 |
| `service/style/StyleChecker.java` | 质量检查服务 |
| `controller/StyleCardController.java` | REST API |
| `dto/StyleCardAnalysis.java` | 分析结果DTO |
| `dto/StyleMetrics.java` | 程序化统计指标 |
| `dto/StyleCheckResult.java` | 检查结果DTO |

### 4.2 后端修改文件

| 文件路径 | 改动内容 |
|----------|----------|
| `agent/WritingAgent.java` | SystemMessage增强，新增反AI规则 |
| `service/chapter/ContextBuilderService.java` | 新增buildStyleInjection()方法 |
| `chain/ChapterGenerationChain.java` | executeWritingChain()新增styleCardId参数 |
| `controller/StreamingChapterController.java` | 传递styleCardId参数 |

### 4.3 前端新增文件

| 文件路径 | 职责 |
|----------|------|
| `views/style/StyleCardLibrary.vue` | 文风卡片库页面 |
| `views/style/CreateStyleCard.vue` | 创建卡片页面 |
| `views/style/StyleCardPreview.vue` | 卡片预览组件 |
| `components/StyleSelector.vue` | 风格选择器组件 |
| `api/style.ts` | 文风卡片API |

### 4.4 数据库脚本

| 文件路径 | 内容 |
|----------|------|
| `sql/style_card.sql` | 建表脚本 |
| `sql/style_sample.sql` | 建表脚本 |
| `sql/user_style_library.sql` | 建表脚本 |
| `sql/novel_style_binding.sql` | 建表脚本 |

---

## 五、实施计划

| 阶段 | 内容 | 时间 | 产出 |
|------|------|------|------|
| Phase 1 | Anti-AI Prompt优化 | Day 1-2 | 修改SystemPrompt，效果验证 |
| Phase 2 | 数据库建表 + 实体/Mapper | Day 3 | SQL脚本，Java实体类 |
| Phase 3 | StyleCardService（CRUD + 上传处理） | Day 4 | 上传接口，文本分段处理 |
| Phase 4 | 风格分析引擎 | Day 5-6 | 程序化提取 + LLM分析 |
| Phase 5 | 风格注入写作引擎 | Day 7 | WritingAgent集成 |
| Phase 6 | 质量检查 | Day 8 | StyleChecker |
| Phase 7 | 前端集成 | Day 9-10 | 卡片库页面 + 创建流程 |
| Phase 8 | 联调测试 | Day 11 | 用3-5本小说验证效果 |

**总工期：11天（约2周）**

---

## 六、验证标准

### 6.1 Phase 1 验证
- 生成同一章节的前后对比
- 检查AI套话出现频率是否下降
- 目标：AI套话减少80%以上

### 6.2 Phase 2-3 验证
- 上传《诡秘之主》→ 生成StyleCard → 用该卡片写新小说
- 对比无风格版本，检查：
  - 句长分布匹配度
  - 对话比例匹配度
  - AI套话数量
  - 风格匹配度评分（目标≥70分）

### 6.3 最终验证
- 邀请3-5名用户测试
- 收集反馈：风格还原度、阅读体验、AI味感知
- 根据反馈迭代优化

---

## 七、核心结论

### 7.1 可行性结论：✅ 完全可行

1. **技术可行性**：LangChain4j + Spring Boot 3 + MySQL/MongoDB完全支持
2. **架构兼容性**：只需扩展现有Agent和ContextBuilder，改动最小
3. **大文本处理**：采样分析+程序化提取，Token消耗可控
4. **成本可控**：分析一次约1-2万token，结果可缓存复用

### 7.2 核心优势

| 优势 | 说明 |
|------|------|
| 独立可复用 | StyleCard是"资产"而非"属性"，可跨小说复用 |
| 程序化+LLM混合 | 60%程序化统计（零成本）+ 40%LLM定性分析 |
| 质量闭环 | 快速检查+深度检查，自动修复偏离 |
| 渐进式改进 | Phase 1立即见效，后续阶段逐步增强 |

### 7.3 建议执行顺序

```
立即做（今天）: Phase 1 — Anti-AI Prompt优化
  ↓
效果验证：生成几章对比AI味是否减少
  ↓
短期做（1-2周）: Phase 2-8 — 完整文风卡片系统
```

**Phase 1的投入产出比最高** — 改几个prompt模板就能显著改善文风，不需要改架构、不需要新表、不需要前端改动。

---

## 附录：关键代码片段

### A. 程序化提取感官词库

```java
// 视觉词
static final String[] VISUAL_WORDS = {
    "看见", "看到", "颜色", "光", "暗", "亮", "红", "白", "黑", "影",
    "闪烁", "刺眼", "昏暗", "明亮", "漆黑"
};

// 听觉词
static final String[] AUDITORY_WORDS = {
    "听到", "声音", "响", "叫", "喊", "低语", "寂静", "吵", "安静"
};

// 触觉词
static final String[] TACTILE_WORDS = {
    "摸", "触", "冷", "热", "硬", "软", "光滑", "粗糙"
};
```

### B. StyleMetrics数据结构

```java
@Data
public class StyleMetrics {
    // 句长分布
    private double avgSentenceLength;
    private double shortSentenceRatio;    // <15字的句子占比
    private int maxSentenceLength;
    
    // 段落分布
    private double avgParagraphLength;
    private double shortParagraphRatio;   // <60字的段落占比
    
    // 对话
    private double dialogueRatio;
    private double avgDialogueLength;
    
    // 标点习惯
    private int ellipsisCount;
    private int emDashCount;
    private int exclamationCount;
    
    // 感官词密度
    private double visualDensity;
    private double auditoryDensity;
    private double tactileDensity;
    
    // 高频特色词
    private List<String> distinctiveWords;
}
```

---

**文档版本**: v1.0
**创建时间**: 2026-04-21
**作者**: Hermes Agent



