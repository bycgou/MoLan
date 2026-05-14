# 墨韵 AI — 外部记忆系统与反AI味系统融合方案

## 一、设计目标

**核心问题：** AI写第300章时，仍然记得第3章埋的伏笔、角色的性格底线、以及上一章结尾的悬念，同时避免AI味过重。

**融合原则：**
1. **记忆系统** — 从Push改为Push-Pull二分，让Agent主动检索
2. **反AI味系统** — 从单点检测改为四道防线，贯穿整个生成流程
3. **规则与记忆分离** — System Prompt放规则，Memory Layers放数据
4. **可维护性** — 四层记忆架构，职责清晰，易于维护

---

## 二、融合后的完整架构

```
ChapterGenerationChain.executeWritingChain(novelId, chapterNum):
  │
  ├── Step 0: 获取分布式锁 (per-novel, Redis)
  │
  ├── Step 1: EntityExtractor 保守提取已知实体
  │     └── 精确匹配已知人名/地名/物品名，不做指代消解
  │
  ├── Step 2: Planning 生成检索计划（两层）
  │     ├── Layer A: 规则引擎（零成本）— 实体→工具映射 + 预算计算
  │     └── Layer B: 轻量LLM（~300 tokens）— 补充遗漏实体 + 优先级排序
  │
  ├── Step 3: 预检索（批量工具调用 + 结果缓存）
  │     └── Layer 2 工具全部在此阶段完成，缓存到 ChapterToolCache
  │
  ├── Step 4: 组装 UserMessage
  │     ├── Layer 1 Working Memory（优先级截断）
  │     ├── 预检索结果
  │     ├── 检索建议（显式引导 Agent 使用工具）
  │     └── 上章审计警告（如有）+ 反AI味提示
  │
  ├── Step 5: WritingAgent 生成正文
  │     ├── Agent 可调用 Layer 3/4 @Tool 做补充查询
  │     └── System Prompt 包含反AI味铁律 + 文风卡片规则
  │
  ├── Step 6: StagePipeline 后处理
  │     ├── Stage1 (并行): SummaryAgent + StateUpdater
  │     ├── Stage2 (串行，依赖Stage1): EntityUpdater + ClueChecker
  │     ├── Stage3 (依赖Stage2): AuditAgent
  │     │   ├── 一致性检查（伏笔/角色/时间线）
  │     │   └── 反AI味检查（禁用词/句式/模板/细节）
  │     ├── Stage4: 三级响应（自动修复/局部重写/人工审阅）
  │     └── Stage5: 反馈回路（audit issues → 下章Working Memory.warnings）
  │
  └── Step 7: 释放锁，记录 ToolCallLog
```

---

## 三、反AI味四道防线融入方案

### 第一道防线：章纲层面（Step 2 Planning 阶段）

**融入位置：** Planning 的 Layer B 轻量LLM分析

**具体方案：**

在 Planning 阶段，除了补充遗漏实体，还增加叙事结构多样性检查：

```java
// Planning 的 Layer B 额外检查
String planningPrompt = """
    章纲：%s
    已识别实体：角色=%s, 地点=%s, 物品=%s

    请判断（用JSON回答）：
    1. 叙事视角是否与前3章重复？（禁止连续3章相同视角）
    2. 本章核心冲突类型是否与前5章重复？
       （外部战斗 / 内心挣扎 / 人际矛盾 / 道德困境 / 信息不对称）
    3. 战斗结局是否是"主角险胜"？（应多样化：平局/惨胜/被迫撤退/赢了但失去重要东西）
    4. 是否需要增加非主线感官细节？（每个新场景至少1个）
    5. 是否需要打破"每章都有钩子"？（偶尔平淡收尾制造节奏变化）

    输出JSON格式：
    {
      "perspectiveCheck": {"repeated": false, "suggestion": ""},
      "conflictCheck": {"repeated": true, "suggestion": "建议改为内心挣扎"},
      "battleEndingCheck": {"template": false, "suggestion": ""},
      "detailSuggestion": "建议在开场增加[具体细节]",
      "pacingSuggestion": "本章可平淡收尾，让下章更有冲击力"
    }
    """.formatted(outline, entities.characters, entities.locations, entities.items);
```

**作用：** 从结构层面保证故事不可预测，是最根本的一道防线。

---

### 第二道防线：正文层面（Step 5 WritingAgent System Prompt）

**融入位置：** WritingAgent 的 @SystemMessage

**具体方案：**

在 WritingAgent 的系统指令中注入反AI味规则：

```java
@SystemMessage("""
    你是一位专业的网络小说作家，正在创作小说。

    【反AI味铁律——违反任何一条均视为写作失败】
    1. 禁止使用以下过渡句：
       "时间飞逝，转眼X天过去了"
       "与此同时，另一边"
       "且不说XXX，单说XXX"
       "就在XXX即将XXX的时候"
       → 替代方案：直接切场景，不需要过渡

    2. 角色反应多样性：
       - 愤怒不一定握拳瞪眼（可以沉默、可以笑、可以转移话题）
       - 震惊不一定瞳孔微缩（可以手抖、可以反复确认、可以失语）
       - 同一情绪在不同角色身上必须有不同的外在表现

    3. 对话节奏：
       - 每5轮对话至少1次：沉默、打断、答非所问、被第三方打断
       - 对话中穿插动作描写，但动作不能全是"看着对方""点了点头"

    4. 场景细节：
       - 每个新场景至少1个与主线无关的感官细节
       （声音、气味、触感、光线、无关人物的小动作）
       - 示例：✗"林辰走进大殿，看到长老们已经到齐"
              ✓"林辰走进大殿时踩到了门槛，差点绊倒。长老们已经到齐，三长老在抠指甲"

    5. 章尾多样性：
       - 禁止每章结尾都是"主角嘴角勾起一抹笑意"
       - 偶尔平淡收尾，制造节奏变化

    【文风铁律——禁止高频AI套话】
    "不禁""似乎""仿佛""眼神中闪过""嘴角微微上扬""心中涌起一股"
    "一种...的感觉""空气中弥漫着""不约而同""若有所思"
    "意味深长""难以名状""若隐若现""一股暖流涌上心头"
    "缓缓说道""淡淡地说""微微点头""轻声说道"

    【对话口语化要求】
    - 允许省略主语、语气词（"嗯""哈""切"）、打断（破折号）、吞字、方言
    - 示例：✗"我认为这件事情需要仔细考虑"
           ✓"这事儿...得琢磨琢磨"

    【段落节奏要求】
    - 至少30%的段落≤2行（50字以内）
    - 禁止连续3段以上结构雷同

    【比喻克制要求】
    - 每千字不超过2个明喻（"像""如同""仿佛"）

    【心理描写要求】
    - 用动作/细节暗示情绪，禁止"XX想道""他心想"
    - 示例：✗"他心想：这下完了"
           ✓"他盯着屏幕，手指停在键盘上，一个字也敲不出来"

    【写作要求】
    1. 严格遵循章纲：不能偏离核心情节
    2. 人物一致性：言行必须符合人物设定
    3. 逻辑连贯：与前文保持一致
    4. 世界状态一致：时间、地点、环境等不能矛盾
    5. 追读力：章节结尾要有钩子，吸引读者继续阅读

    【防幻觉三定律】
    - 定律一：大纲即法律 - 严格遵循章纲内容
    - 定律二：设定即物理 - 人物言行必须符合设定
    - 定律三：发明需识别 - 创造新元素必须标注

    【输出格式】
    直接输出小说正文，不要添加任何说明。
    """)
String generateChapter(@UserMessage String userPrompt);
```

---

### 第三道防线：检查层面（Step 6 Stage3 AuditAgent）

**融入位置：** StagePipeline 的 Stage3

**具体方案：**

AuditAgent 增加反AI味检测维度：

```java
@Service
public class AuditAgent {

    public AuditResult audit(ChapterGenerated event, StoryState newState, String summary) {
        String content = event.getContent();

        List<Issue> issues = new ArrayList<>();

        // === 一致性检查（原有）===
        // 1. 伏笔一致性
        issues.addAll(checkClueConsistency(event, newState));
        // 2. 角色一致性
        issues.addAll(checkCharacterConsistency(event, newState));
        // 3. 时间线一致性
        issues.addAll(checkTimelineConsistency(event, newState));

        // === 反AI味检查（新增）===
        // 4. 禁用词检测
        issues.addAll(checkForbiddenWords(content));

        // 5. 句式重复检测
        issues.addAll(checkSentenceRepetition(content, event.getNovelId()));

        // 6. 对话结构检测
        issues.addAll(checkDialoguePattern(content));

        // 7. 过渡句检测
        issues.addAll(checkTransitionPhrases(content));

        // 8. 角色情绪表达重复检测
        issues.addAll(checkEmotionRepetition(content, event.getNovelId()));

        // 9. 场景细节检测
        issues.addAll(checkSceneDetails(content, event.getChapterOutline()));

        // 10. 章尾模式检测
        issues.addAll(checkChapterEnding(content));

        // 计算总分
        int score = calculateScore(issues);

        // 判断是否需要修复
        boolean requiresRemediation = score < 80;

        return new AuditResult(score, issues, requiresRemediation);
    }

    private List<Issue> checkForbiddenWords(String content) {
        List<Issue> issues = new ArrayList<>();
        String[] forbiddenWords = {
            "不禁", "似乎", "仿佛", "眼神中闪过", "嘴角微微上扬",
            "心中涌起一股", "一种...的感觉", "空气中弥漫着",
            "不约而同", "若有所思", "意味深长", "难以名状",
            "若隐若现", "一股暖流涌上心头", "缓缓说道",
            "淡淡地说", "微微点头", "轻声说道"
        };

        for (String word : forbiddenWords) {
            if (content.contains(word)) {
                issues.add(new Issue("forbidden_word", "minor",
                    "出现禁用词: '" + word + "'"));
            }
        }
        return issues;
    }

    private List<Issue> checkSentenceRepetition(String content, String novelId) {
        List<Issue> issues = new ArrayList<>();

        // 统计句式多样性
        int sentenceVariety = calculateSentenceVariety(content);

        // 获取文风卡片的基准值
        StyleCard styleCard = styleCardService.getStyleCard(novelId);
        double baselineVariety = styleCard.getStatsJson().getSentenceVarietyIndex();

        // 如果多样性低于基准值的60%，标记为AI味过重
        if (sentenceVariety < baselineVariety * 0.6) {
            issues.add(new Issue("template_reaction", "moderate",
                "句式多样性指数偏低(" + sentenceVariety + " vs 基准" + baselineVariety + ")"));
        }

        return issues;
    }

    private List<Issue> checkDialoguePattern(String content) {
        List<Issue> issues = new ArrayList<>();

        // 统计对话模式
        // 检查是否连续5轮都是"A说→B回应"
        String[] paragraphs = content.split("\n+");
        int consecutiveDialogue = 0;
        boolean lastWasDialogue = false;

        for (String para : paragraphs) {
            boolean isDialogue = para.contains(""") || para.contains(""");
            if (isDialogue) {
                if (lastWasDialogue) {
                    consecutiveDialogue++;
                    if (consecutiveDialogue >= 5) {
                        issues.add(new Issue("missing_detail", "moderate",
                            "连续5轮对话都是"A说→B回应"模式，缺乏打断/沉默/答非所问"));
                        break;
                    }
                } else {
                    consecutiveDialogue = 1;
                }
                lastWasDialogue = true;
            } else {
                consecutiveDialogue = 0;
                lastWasDialogue = false;
            }
        }

        return issues;
    }

    private List<Issue> checkTransitionPhrases(String content) {
        List<Issue> issues = new ArrayList<>();

        String[] transitionPhrases = {
            "时间飞逝", "转眼", "与此同时", "另一边",
            "且不说", "单说", "就在.*即将.*的时候"
        };

        for (String phrase : transitionPhrases) {
            if (content.matches(".*" + phrase + ".*")) {
                issues.add(new Issue("forbidden_word", "minor",
                    "出现万能过渡句: '" + phrase + "'"));
            }
        }

        return issues;
    }

    private List<Issue> checkSceneDetails(String content, String chapterOutline) {
        List<Issue> issues = new ArrayList<>();

        // 检查新场景是否有感官细节
        // 如果章纲提到新场景，但正文中缺乏感官细节
        if (chapterOutline.contains("新场景") || chapterOutline.contains("到达")) {
            String[] sensoryWords = {"声音", "气味", "触感", "光线", "温度", "颜色"};
            boolean hasSensoryDetail = false;

            for (String word : sensoryWords) {
                if (content.contains(word)) {
                    hasSensoryDetail = true;
                    break;
                }
            }

            if (!hasSensoryDetail) {
                issues.add(new Issue("missing_detail", "minor",
                    "新场景缺乏感官细节（声音/气味/触感/光线等）"));
            }
        }

        return issues;
    }

    private List<Issue> checkChapterEnding(String content) {
        List<Issue> issues = new ArrayList<>();

        // 检查章尾是否都是"主角嘴角勾起一抹笑意"
        String[] endingPatterns = {
            "嘴角.*笑意", "嘴角.*上扬", "嘴角.*勾起",
            "眼中.*闪过.*光芒", "心中.*涌起"
        };

        String lastParagraph = getLastParagraph(content);
        for (String pattern : endingPatterns) {
            if (lastParagraph.matches(".*" + pattern + ".*")) {
                issues.add(new Issue("template_reaction", "minor",
                    "章尾使用了模板化表达: '" + pattern + "'"));
                break;
            }
        }

        return issues;
    }
}
```

---

### 第四道防线：修正层面（Step 6 Stage4 Remediation）

**融入位置：** StagePipeline 的 Stage4

**具体方案：**

三级响应机制：

```java
@Service
public class RemediationHandler {

    public void handle(AuditResult audit, ChapterGenerated event) {
        List<Issue> issues = audit.getIssues();

        // 按严重程度分类
        List<Issue> minorIssues = issues.stream()
            .filter(i -> "minor".equals(i.getSeverity()))
            .collect(Collectors.toList());
        List<Issue> moderateIssues = issues.stream()
            .filter(i -> "moderate".equals(i.getSeverity()))
            .collect(Collectors.toList());
        List<Issue> severeIssues = issues.stream()
            .filter(i -> "severe".equals(i.getSeverity()))
            .collect(Collectors.toList());

        // === Minor (score > 80)：自动修复 ===
        if (!minorIssues.isEmpty()) {
            String fixedContent = event.getContent();

            // 1. 禁用词替换
            for (Issue issue : minorIssues) {
                if ("forbidden_word".equals(issue.getType())) {
                    fixedContent = replaceForbiddenWord(fixedContent, issue.getDetail());
                }
            }

            // 2. 过渡句替换
            for (Issue issue : minorIssues) {
                if ("forbidden_word".equals(issue.getType()) && issue.getDetail().contains("过渡句")) {
                    fixedContent = replaceTransitionPhrase(fixedContent, issue.getDetail());
                }
            }

            // 3. 补充感官细节
            for (Issue issue : minorIssues) {
                if ("missing_detail".equals(issue.getType()) && issue.getDetail().contains("感官细节")) {
                    fixedContent = addSensoryDetail(fixedContent, event.getChapterOutline());
                }
            }

            // 更新章节内容
            event.setContent(fixedContent);
            log.info("自动修复 {} 个minor问题", minorIssues.size());
        }

        // === Moderate (score 50-80)：局部重写 ===
        if (!moderateIssues.isEmpty()) {
            int retryCount = 0;
            int maxRetries = 2;

            while (retryCount < maxRetries && audit.getScore() < 80) {
                // 构建重写提示
                String rewritePrompt = buildRewritePrompt(event, moderateIssues);

                // 调用WritingAgent重写问题段落
                String rewrittenContent = writingAgent.rewriteChapter(rewritePrompt);

                // 更新内容
                event.setContent(rewrittenContent);

                // 重新审计
                audit = auditAgent.audit(event, null, null);
                retryCount++;
            }

            if (audit.getScore() < 80) {
                // 降级为人工审阅
                event.setStatus("manual_review");
                event.setAuditIssues(moderateIssues);
                log.warn("局部重写{}次后仍不达标，降级为人工审阅", maxRetries);
            }
        }

        // === Severe (score < 50)：标记人工审阅 ===
        if (!severeIssues.isEmpty()) {
            event.setStatus("manual_review");
            event.setAuditIssues(severeIssues);
            log.error("发现{}个severe问题，暂停自动生成", severeIssues.size());
        }

        // === 反馈回路：写入下章 Working Memory.warnings ===
        if (audit.getScore() < 80) {
            saveWarningsForNextChapter(event.getNovelId(), event.getChapterNum(), issues);
        }
    }

    private void saveWarningsForNextChapter(String novelId, int chapterNum, List<Issue> issues) {
        // 将audit issues写入下章的Working Memory
        String warnings = issues.stream()
            .map(i -> i.getDetail())
            .collect(Collectors.joining("; "));

        // 更新StoryState的narrativeFlags，添加warnings字段
        storyStateService.addWarning(novelId, chapterNum + 1, warnings);
    }

    private String buildRewritePrompt(ChapterGenerated event, List<Issue> issues) {
        StringBuilder sb = new StringBuilder();
        sb.append("请重写以下章节，修复以下问题：\n\n");

        for (Issue issue : issues) {
            sb.append("- ").append(issue.getDetail()).append("\n");
        }

        sb.append("\n原始章节内容：\n");
        sb.append(event.getContent());

        sb.append("\n\n重写要求：\n");
        sb.append("1. 保持原有情节不变\n");
        sb.append("2. 修复上述所有问题\n");
        sb.append("3. 保持文风一致性\n");

        return sb.toString();
    }
}
```

---

## 四、文风卡片增强

### 增加反模式维度

在文风卡片分析阶段增加程序化检测：

```java
@Service
public class StyleCardService {

    public StyleCard analyzeStyle(String novelId) {
        StyleCard card = new StyleCard();

        // === 原有分析 ===
        // 1. 节奏分析
        // 2. 对话分析
        // 3. 描写分析
        // 4. 禁用词分析
        // 5. 样本提取

        // === 新增：反模式维度分析 ===
        // 6. 句式多样性指数
        card.setSentenceVarietyIndex(calculateSentenceVariety(content));

        // 7. 过渡方式偏好
        card.setTransitionPreference(analyzeTransitionStyle(content));

        // 8. 章尾模式分布
        card.setEndingPatternDistribution(analyzeEndingPatterns(content));

        // 9. 情绪表达多样性
        card.setEmotionDiversity(analyzeEmotionDiversity(content));

        // 10. 对话结构分布
        card.setDialoguePatternDistribution(analyzeDialoguePatterns(content));

        return card;
    }

    private double calculateSentenceVariety(String content) {
        // 统计句式变化频率
        // 返回0-1的多样性指数
        String[] sentences = content.split("[。！？]");
        Set<String> sentencePatterns = new HashSet<>();

        for (String sentence : sentences) {
            // 提取句式模式（主谓宾结构）
            String pattern = extractSentencePattern(sentence);
            sentencePatterns.add(pattern);
        }

        return (double) sentencePatterns.size() / sentences.length;
    }

    private Map<String, Integer> analyzeTransitionStyle(String content) {
        Map<String, Integer> transitions = new HashMap<>();

        // 统计过渡方式
        // 直接切场景 vs 过渡句
        String[] transitionPhrases = {
            "时间飞逝", "与此同时", "另一边", "且不说", "单说"
        };

        for (String phrase : transitionPhrases) {
            int count = countOccurrences(content, phrase);
            if (count > 0) {
                transitions.put(phrase, count);
            }
        }

        return transitions;
    }

    private Map<String, Integer> analyzeEndingPatterns(String content) {
        Map<String, Integer> patterns = new HashMap<>();

        // 统计章尾模式
        String[] paragraphs = content.split("\n+");
        String lastParagraph = paragraphs[paragraphs.length - 1];

        if (lastParagraph.matches(".*嘴角.*笑意.*")) {
            patterns.merge("smile_ending", 1, Integer::sum);
        }
        if (lastParagraph.matches(".*眼中.*光芒.*")) {
            patterns.merge("eye_light_ending", 1, Integer::sum);
        }
        if (lastParagraph.matches(".*心中.*涌起.*")) {
            patterns.merge("heart_surge_ending", 1, Integer::sum);
        }

        return patterns;
    }
}
```

### 增加样本类型

在文风卡片中增加6类新样本：

```java
public enum SampleType {
    SUSPENSE,      // 悬疑
    DIALOGUE,      // 对话
    BATTLE,        // 战斗
    PSYCHOLOGY,    // 心理
    DESCRIPTION,   // 描写
    TRANSITION,    // 场景过渡（新增）
    SILENCE,       // 沉默/无对话场景（新增）
    DETAIL,        // 无关主线的细节（新增）
    INNER_CONFLICT,// 内心挣扎/道德困境（新增）
    HUMOR,         // 幽默/轻松片段（新增）
    ANTI_CLIMAX    // 反高潮/平淡收尾（新增）
}
```

**抽取策略：**

```java
public List<StyleSample> extractSamples(String novelId) {
    List<StyleSample> samples = new ArrayList<>();

    // 原有5类
    samples.addAll(extractSuspenseSamples(novelId));
    samples.addAll(extractDialogueSamples(novelId));
    samples.addAll(extractBattleSamples(novelId));
    samples.addAll(extractPsychologySamples(novelId));
    samples.addAll(extractDescriptionSamples(novelId));

    // 新增6类
    samples.addAll(extractTransitionSamples(novelId));    // 场景过渡片段
    samples.addAll(extractSilenceSamples(novelId));       // 沉默/无对话场景
    samples.addAll(extractDetailSamples(novelId));        // 无关主线的细节
    samples.addAll(extractInnerConflictSamples(novelId)); // 内心挣扎/道德困境
    samples.addAll(extractHumorSamples(novelId));         // 幽默/轻松片段
    samples.addAll(extractAntiClimaxSamples(novelId));    // 反高潮/平淡收尾

    return samples;
}

private List<StyleSample> extractTransitionSamples(String novelId) {
    // 扫描原文场景过渡方式
    // 找出直接切场景的片段（AI最弱的环节）
    List<StyleSample> samples = new ArrayList<>();

    List<NovelChapter> chapters = chapterMapper.selectByNovelIdLimit(novelId, 100);
    for (NovelChapter chapter : chapters) {
        String content = chapter.getContent();
        if (content == null) continue;

        // 找场景过渡点
        String[] paragraphs = content.split("\n+");
        for (int i = 0; i < paragraphs.length - 1; i++) {
            // 如果相邻段落场景明显切换
            if (isSceneTransition(paragraphs[i], paragraphs[i + 1])) {
                String sample = paragraphs[i] + "\n" + paragraphs[i + 1];
                samples.add(new StyleSample(
                    SampleType.TRANSITION,
                    truncate(sample, 300),
                    chapter.getChapterNum()
                ));
            }
        }
    }

    return samples;
}

private List<StyleSample> extractSilenceSamples(String novelId) {
    // 找沉默/无对话的场景（AI总充斥对话）
    List<StyleSample> samples = new ArrayList<>();

    List<NovelChapter> chapters = chapterMapper.selectByNovelIdLimit(novelId, 100);
    for (NovelChapter chapter : chapters) {
        String content = chapter.getContent();
        if (content == null) continue;

        // 找连续3段以上无对话的片段
        String[] paragraphs = content.split("\n+");
        int consecutiveNonDialogue = 0;
        int startIdx = -1;

        for (int i = 0; i < paragraphs.length; i++) {
            boolean hasDialogue = paragraphs[i].contains(""") || paragraphs[i].contains(""");
            if (!hasDialogue) {
                if (consecutiveNonDialogue == 0) {
                    startIdx = i;
                }
                consecutiveNonDialogue++;
                if (consecutiveNonDialogue >= 3) {
                    // 找到沉默场景
                    String sample = String.join("\n",
                        Arrays.copyOfRange(paragraphs, startIdx, Math.min(i + 1, startIdx + 5)));
                    samples.add(new StyleSample(
                        SampleType.SILENCE,
                        truncate(sample, 300),
                        chapter.getChapterNum()
                    ));
                    consecutiveNonDialogue = 0;
                }
            } else {
                consecutiveNonDialogue = 0;
            }
        }
    }

    return samples;
}
```

---

## 五、Token预算分配（融合版）

```
总上下文预算：约 6000-8000 token

┌──────────────────────────────────┬───────────┬─────────┐
│ 内容                              │ Token预算  │ 占比    │
├──────────────────────────────────┼───────────┼─────────┤
│ ① 系统指令+反AI味规则              │ 1200      │ 15%     │
│ ② 文风卡片（含反模式维度）          │ 600       │ 8%      │
│ ③ 总纲+世界观                     │ 800       │ 10%     │
│ ④ 本章章纲                        │ 200       │ 3%      │
│ ⑤ Working Memory (Layer 1)        │ 1500      │ 20%     │
│ ⑥ 预检索结果 (Layer 2)             │ 1000      │ 13%     │
│ ⑦ 审计警告+反AI味提示              │ 200       │ 3%      │
│ 预留给 Agent @Tool (Layer 3/4)     │ 800       │ 10%     │
│ 输出空间                           │ 1200      │ 15%     │
├──────────────────────────────────┼───────────┼─────────┤
│ 总计                              │ ~7500     │ 100%    │
└──────────────────────────────────┴───────────┴─────────┘

关键变化：
- 系统指令从800增加到1200（+反AI味规则）
- 文风卡片从500增加到600（+反模式维度）
- Working Memory从600增加到1500（合并滑动窗口+伏笔）
- 预检索结果从0增加到1000（Layer 2工具返回）
- 输出空间从800增加到1200（保证生成质量）
```

---

## 六、实现优先级

| 优先级 | 改动 | 说明 |
|--------|------|------|
| **P0** | Step 1-3：EntityExtractor + Planning + 预检索 | 解决Agent元认知缺失，确保工具被实际调用 |
| **P0** | Step 4：Layer 1 Working Memory（优先级截断） | 合并storyState + slidingWindow + urgentClues |
| **P0** | Step 5：WritingAgent System Prompt（含反AI味规则） | 从源头减少AI味 |
| **P1** | Step 5：RAG 从 ContextBuilder 移到 @Tool | 每次生成省~800 token，Agent按需调用 |
| **P1** | Step 6：Stage Pipeline 后处理 + AuditAgent | 替代手动更新，加入审计反馈回路 |
| **P1** | Step 6：三级响应（自动修复/局部重写/人工） | 补齐审计闭环 |
| **P2** | 文风卡片增强（反模式维度+新样本类型） | 提供更精准的风格参考 |
| **P2** | Layer 3/4：RAG embedding模型选型 + Compressed Memory | 长期记忆支持 |
| **P2** | ToolCallLog + 监控告警 | 可观测性基础 |
| **P3** | 分布式锁 + 乐观锁 + 并发控制 | 多章并发生成的安全保障 |
| **P3** | 别名解析 + 实体消歧 | 边缘场景完善 |

---

## 七、总结

**融合方案的核心：**

1. **记忆系统** — 从Push改为Push-Pull二分，让Agent主动检索，Token效率提升20%+
2. **反AI味系统** — 从单点检测改为四道防线，贯穿Planning→Writing→Audit→Remediation全流程
3. **规则与记忆分离** — System Prompt放规则（反AI味铁律），Memory Layers放数据（Working/Entity/Episodic/Compressed）
4. **可维护性** — 四层记忆架构 + 四道反AI味防线，职责清晰，易于扩展

**预期效果：**

- Token消耗降低20%（Pull模式按需检索）
- 一致性错误率降低50%（四道防线层层过滤）
- AI味评分提升10分（反模式检测+自动修复）
- 工具调用覆盖率>80%（Planning阶段确保）
