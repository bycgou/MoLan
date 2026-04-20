# Super-Ma → MoYun 移植计划说明书

> 文档版本：v1.0  
> 编写日期：2026-04-20  
> 源项目：Super-Ma (PHP + MySQL)  
> 目标项目：MoYun 墨韵 (Java Spring Boot 3 + LangChain4j)

---

## 一、移植目标

将 Super-Ma 经过实战打磨的 **Prompt 工程经验** 和 **运营功能** 移植到 MoYun 的 LangChain4j Agent 架构中，取长补短，打造一个既有强大 AI Agent 架构又有精细 Prompt 控制能力的 AI 小说创作系统。

---

## 二、能力差距分析

### 2.1 Super-Ma 有但 MoYun 缺失的能力

| 编号 | 能力 | Super-Ma 实现 | MoYun 当前状态 | 优先级 |
|------|------|---------------|---------------|--------|
| P0-1 | **三层记忆 Prompt 注入** (弧段+近章+钩子) | 完整实现 | 仅单层 "上一章摘要" | 🔴 P0 |
| P0-2 | **伏笔 deadline 紧急提示** | 临近deadline自动标⚠️ | 仅列出未回收伏笔 | 🔴 P0 |
| P0-3 | **关键事件去重** | 注入全书关键事件，严禁重复 | 无 | 🔴 P0 |
| P0-4 | **故事势能 (story_momentum)** | 追踪张力变化趋势 | 无 | 🔴 P0 |
| P0-5 | **意象/场景去重** | 禁止近5章重复使用 | 无 | 🟡 P1 |
| P1-1 | **模型 Fallback** | 主模型失败自动切换备用 | 无 | 🟡 P1 |
| P1-2 | **SSE 流式写作** | 完整SSE实现+取消+进度 | 待确认 | 🟡 P1 |
| P1-3 | **章节版本快照** | 重写前自动保存旧版本 | 无 | 🟡 P1 |
| P1-4 | **取消写作** | 支持中途取消 | 无 | 🟢 P2 |
| P1-5 | **章节简介蓝图** (Synopsis) | 介于大纲和正文的中间层 | 无 | 🟢 P2 |
| P2-1 | **温度按任务类型动态调整** | creative/structured/synopsis | 无 | 🟢 P2 |
| P2-2 | **人物状态冲突 AI 检测** | AI对比检测矛盾 | 无 | 🟢 P2 |

### 2.2 MoYun 有但 Super-Ma 缺失的能力 (需保留)

| 能力 | MoYun 实现 | 说明 |
|------|-----------|------|
| LangChain4j Agent 工具调用 | @Tool 注解，Agent 自动查询人物/伏笔 | **核心优势，必须保留** |
| 5维度自动校验 | OOC/逻辑/节奏/线索/设置 | **核心优势，必须保留** |
| RAG 语义检索 | Embedding + Redis Vector | 保留并增强 |
| 人物关系图谱 | 9种关系类型 | 保留 |
| MQ 异步生成 | RabbitMQ | 保留 |
| Redis 缓存上下文 | GenerationContext 缓存 | 保留 |

---

## 三、移植方案总览

采用 **渐进式移植** 策略，分3个阶段，共7个模块，预计工作量约 15-20 天。

```
阶段一 (P0) — Prompt 体系重建     [5天]  ← 影响最大，优先级最高
阶段二 (P1) — 运营功能补齐       [5天]
阶段三 (P2) — 高级特性增强       [5天]
```

---

## 四、阶段一：Prompt 体系重建 (P0)

### 4.1 模块一：三层记忆上下文注入

**目标**：将 MoYun 的单层 "上一章摘要" 升级为 Super-Ma 的三层记忆体系。

#### 4.1.1 数据层准备

**新增 MySQL 表：`arc_summaries`** (弧段摘要)

```sql
CREATE TABLE arc_summaries (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    novel_id VARCHAR(64) NOT NULL,
    chapter_from INT NOT NULL,
    chapter_to INT NOT NULL,
    summary TEXT NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_novel_range (novel_id, chapter_from, chapter_to)
);
```

**新增 NovelBase 字段：**
```sql
ALTER TABLE novel_base ADD COLUMN story_momentum VARCHAR(500) COMMENT '故事势能摘要';
ALTER TABLE novel_base ADD COLUMN pending_foreshadowing JSON COMMENT '待回收伏笔追踪';
```

**新增 NovelChapter 字段：**
```sql
ALTER TABLE novel_chapter ADD COLUMN hook VARCHAR(500) COMMENT '结尾钩子';
ALTER TABLE novel_chapter ADD COLUMN key_points JSON COMMENT '关键情节点';
ALTER TABLE novel_chapter ADD COLUMN chapter_summary TEXT COMMENT '章节摘要(冗余存储，加速查询)';
ALTER TABLE novel_chapter ADD COLUMN used_tropes JSON COMMENT '本章使用的意象/场景';
ALTER TABLE novel_chapter ADD COLUMN synopsis_id BIGINT COMMENT '关联的章节简介ID';
```

#### 4.1.2 实体层

新建 `ArcSummary.java` (entity/mysql)：
```java
@Data
@TableName("arc_summaries")
public class ArcSummary {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String novelId;
    private Integer chapterFrom;
    private Integer chapterTo;
    private String summary;
    private LocalDateTime createTime;
}
```

#### 4.1.3 数据访问层

新建 `ArcSummaryMapper.java` + `ArcSummaryMapper.xml`：
```java
@Mapper
public interface ArcSummaryMapper extends BaseMapper<ArcSummary> {
    List<ArcSummary> selectByNovelIdAndRange(@Param("novelId") String novelId, 
                                              @Param("maxChapter") Integer maxChapter);
}
```

#### 4.1.4 服务层 — ContextBuilderService

新建 `ContextBuilderService.java`，**替代现有的 `buildOutlineContext` 和 `buildWritingContext` 私有方法**：

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class ContextBuilderService {
    
    private final ArcSummaryMapper arcSummaryMapper;
    private final NovelChapterMapper chapterMapper;
    private final CharacterStateService characterStateService;
    private final ClueBookService clueBookService;
    
    /**
     * 构建章纲生成的上下文 — 三层记忆体系
     * 
     * 第一层：弧段摘要 (全局历史记忆，防止 AI 对早期情节失忆)
     * 第二层：近8章大纲 (微观衔接)
     * 第三层：上批钩子 (本章开头必须承接)
     */
    public String buildOutlineContext(String novelId, int chapterNum, 
                                       NovelBase novel, ArcInfo arcInfo) {
        StringBuilder sb = new StringBuilder();
        
        // === 第一层：弧段摘要 ===
        List<ArcSummary> arcSummaries = arcSummaryMapper
            .selectByNovelIdAndRange(novelId, chapterNum);
        if (!arcSummaries.isEmpty()) {
            sb.append("【全书故事线回顾（按时序，必须与此保持一致）】\n");
            for (ArcSummary arc : arcSummaries) {
                sb.append(String.format("【第%d-%d章故事线】%s\n\n", 
                    arc.getChapterFrom(), arc.getChapterTo(), arc.getSummary()));
            }
        }
        
        // === 第二层：近8章局部大纲 ===
        List<NovelChapter> recentChapters = chapterMapper
            .selectRecentOutlines(novelId, chapterNum, 8);
        if (!recentChapters.isEmpty()) {
            sb.append("【前情参考（保持剧情连贯，注意每章钩子的承接）】\n");
            for (NovelChapter ch : recentChapters) {
                String outline = truncate(ch.getOutline(), 80);
                String hook = ch.getHook() != null ? "  →钩子：" + ch.getHook() : "";
                sb.append(String.format("第%d章《%s》：%s%s\n", 
                    ch.getChapterNum(), ch.getChapterTitle(), outline, hook));
            }
            sb.append("\n");
        }
        
        // === 第三层：上一章结尾钩子 ===
        NovelChapter prevChapter = chapterMapper
            .selectByNovelIdAndChapterNum(novelId, chapterNum - 1);
        if (prevChapter != null && prevChapter.getHook() != null) {
            sb.append(String.format(
                "【上一章结尾钩子（本章开头必须直接承接这个悬念）】\n%s\n\n", 
                prevChapter.getHook()));
        }
        
        // === 关键事件去重 ===
        List<KeyEvent> keyEvents = getRecentKeyEvents(novelId, 30);
        if (!keyEvents.isEmpty()) {
            sb.append("【全书已发生关键事件（严禁重复这些事件）】\n");
            for (KeyEvent e : keyEvents) {
                sb.append(String.format("第%d章：%s\n", e.getChapter(), e.getEvent()));
            }
            sb.append("\n");
        }
        
        // === 人物当前状态 ===
        String charStates = characterStateService.formatForPrompt(novelId);
        if (charStates != null && !charStates.isEmpty()) {
            sb.append("【人物当前状态（大纲中必须与此一致）】\n")
              .append(charStates).append("\n\n");
        }
        
        // === 伏笔 deadline 紧急提示 ===
        List<ClueBook> pendingClues = clueBookService.getUnrecycledClues(novelId);
        if (!pendingClues.isEmpty()) {
            sb.append("【待回收伏笔（大纲中应适时安排回收，切勿遗忘）】\n");
            for (ClueBook clue : pendingClues) {
                String deadline = "";
                if (clue.getDeadline() != null) {
                    deadline = chapterNum >= clue.getDeadline() - 5
                        ? " ⚠️【紧急】建议" + clue.getDeadline() + "章前回收（已临近）"
                        : "（建议" + clue.getDeadline() + "章前回收）";
                }
                sb.append(String.format("第%d章埋：%s%s\n", 
                    clue.getBuryChapter(), clue.getClueContent(), deadline));
            }
            sb.append("\n");
        }
        
        // === 故事势能 ===
        if (novel.getStoryMomentum() != null && !novel.getStoryMomentum().isEmpty()) {
            sb.append("【当前故事势能（续写时保持张力）】\n")
              .append(novel.getStoryMomentum()).append("\n\n");
        }
        
        // === 基础上下文 (保留 MoYun 原有) ===
        sb.append("===小说总纲===\n")
          .append(truncate(novel.getTotalOutline(), 4000)).append("\n\n");
        sb.append("===卷信息===\n")
          .append("卷名: ").append(arcInfo.getArcName()).append("\n")
          .append("卷纲: ").append(truncate(arcInfo.getArcOutline(), 1500)).append("\n\n");
        sb.append("===世界观===\n").append(novel.getWorldSetting()).append("\n\n");
        
        return sb.toString();
    }
    
    /**
     * 构建正文写作的上下文 — 增强版
     */
    public String buildWritingContext(String novelId, int chapterNum,
                                       NovelChapter chapter, NovelBase novel,
                                       String selectedIdea) {
        StringBuilder sb = new StringBuilder();
        
        // === 三层记忆（同上，但针对写作场景微调）===
        // ... (弧段摘要 + 前情提要 + 前文衔接)
        
        // === 近5章意象去重 ===
        List<String> prevTropes = getPreviousUsedTropes(novelId, chapterNum, 5);
        if (!prevTropes.isEmpty()) {
            sb.append("【本章禁止重复使用的意象/场景（近期已用，需要新鲜感）】：")
              .append(String.join("、", prevTropes)).append("\n\n");
        }
        
        // === 伏笔 deadline 紧急提示 ===
        // ... (同上，但 threshold 更小，仅提示临近 deadline 的)
        
        // === 章节简介蓝图（如已生成）===
        if (chapter.getSynopsisId() != null) {
            ChapterSynopsis synopsis = synopsisMapper.selectById(chapter.getSynopsisId());
            if (synopsis != null) {
                sb.append("【章节简介（详细写作蓝图，必须遵循）】\n")
                  .append(formatSynopsis(synopsis)).append("\n\n");
            }
        }
        
        return sb.toString();
    }
}
```

#### 4.1.5 修改 ChapterGenerationServiceImpl

将现有的 `buildOutlineContext` 和 `buildWritingContext` 私有方法替换为调用 `ContextBuilderService`：

```java
// 修改前 (ChapterGenerationServiceImpl.java:125)
String contextPrompt = buildOutlineContext(
    novel.getTotalOutline(), arcInfo, worldClockInfo, worldSetting,
    characterStates, lastFingerprint, request.getUserIntent(), unrecycledClues);

// 修改后
String contextPrompt = contextBuilderService.buildOutlineContext(
    request.getNovelId(), request.getChapterNum(), novel, arcInfo);
```

**修改文件列表：**
- `ChapterGenerationServiceImpl.java` — 删除 `buildOutlineContext` 和 `buildWritingContext`，注入 `ContextBuilderService`
- `ChapterGenerationChain.java` — 无需修改（已经接收 contextPrompt 字符串）

---

### 4.2 模块二：Agent System Prompt 重写

**目标**：将 Super-Ma 的写作铁律植入 MoYun 的 Agent System Prompt。

#### 4.2.1 OutlineAgent System Prompt 重写

当前 `OutlineAgent.java` 的 System Prompt（通过注解）：
```
你是专业的小说大纲Agent。遵循"防幻觉三定律"...
```

替换为 Super-Ma 风格的铁律体系：

```java
public interface OutlineAgent {
    
    @SystemMessage(
        "你是一位专业的网络小说作家，擅长创作情节紧凑、引人入胜的小说大纲。\n\n" +
        "【输出铁律（违反任何一条视为无效输出）】\n" +
        "1. 只输出纯JSON，不得有任何前缀、后缀、说明文字或markdown代码块\n" +
        "2. 所有字段值中不得出现未转义的双引号，如需引用书名请用【】代替引号\n" +
        "3. summary控制在80字以内，key_points每条15字以内，hook 20字以内\n" +
        "4. 每章情节必须推进故事，严禁重复【全书已发生关键事件】中的任何事件\n" +
        "5. 所有人物的职务、身份、处境必须与【人物当前状态】完全一致\n" +
        "6. 如有【待回收伏笔】，应在合适章节安排自然回收，不得无限期悬置\n" +
        "7. 如有【上一章结尾钩子】，本章summary开头必须直接承接该悬念\n" +
        "8. 每章hook必须为下一章埋下具体可衔接的悬念，不得使用空洞表述\n" +
        "9. 如有【全书故事线回顾】，本章情节必须在其基础上自然演进\n\n" +
        "【输出格式】\n" +
        "{\"chapter_number\":整数,\"title\":\"标题\",\"summary\":\"概要\"," +
        "\"key_points\":[\"点1\",\"点2\"],\"hook\":\"钩子\"}"
    )
    String generateOutline(@UserMessage String userPrompt);
}
```

#### 4.2.2 WritingAgent System Prompt 重写

```java
public interface WritingAgent {
    
    @SystemMessage(
        "你是一位专业的网络小说作家，正在创作小说。\n\n" +
        "【写作铁律——违反任何一条均视为写作失败】\n" +
        "1. 人物一致性：所有人物的职务、身份、生死状态必须与【人物当前状态】完全一致\n" +
        "2. 情节不重复：【全书已发生事件】中出现的任何事件，严禁重演或变体重复\n" +
        "3. 逻辑自洽：事件必须是前情的自然延伸，因果链条清晰\n" +
        "4. 直接开始：从\"第X章 标题\"直接开始输出正文，不要有任何废话\n" +
        "5. 风格统一：保持与前文一致的叙事视角、语气和文风\n" +
        "6. 结尾钩子：章节结尾必须自然体现大纲中的\"结尾钩子\"\n" +
        "7. 如有【章节简介】，必须严格遵循场景分解和对话要点\n\n" +
        "【防幻觉三定律】\n" +
        "定律一：大纲即法律 — 严格遵循章纲内容\n" +
        "定律二：设定即物理 — 人物言行必须符合设定\n" +
        "定律三：发明需识别 — 创造新元素必须标注[新设定：XXX]"
    )
    String generateChapter(@UserMessage String userPrompt);
}
```

#### 4.2.3 新增：ArcSummaryAgent (弧段摘要生成)

新建 Agent 接口和 Service，用于自动生成弧段摘要：

```java
public interface ArcSummaryAgent {
    @SystemMessage(
        "你是专业的小说编辑。分析一段故事线，生成简洁的弧段摘要。\n" +
        "输出要求：200-400字，包含主要情节、人物变化、伏笔埋设和回收。\n" +
        "只输出摘要文本，不要JSON或其他格式。"
    )
    String generateArcSummary(@UserMessage String prompt);
}
```

**触发时机**：每写完 10-20 章，自动触发生成弧段摘要。

---

### 4.3 模块三：摘要输出结构增强

**目标**：将 Super-Ma 的结构化摘要输出移植到 MoYun。

#### 4.3.1 修改 SummaryAgent 输出格式

当前 MoYun 输出：
```json
{"strand_type":"quest","fingerprint":"一句话描述","core_events":[],"hook_level":"中"}
```

扩展为 Super-Ma 风格：
```json
{
  "strand_type": "quest",
  "fingerprint": "一句话描述",
  "narrative_summary": "200-300字的叙事摘要",
  "character_updates": {
    "人物名": {"职务": "当前职务", "处境": "当前处境", "关键变化": "本章变化"}
  },
  "key_event": "本章最关键的单一事件",
  "used_tropes": ["意象1", "意象2"],
  "new_foreshadowing": [{"desc": "新伏笔描述", "deadline": "建议回收章节"}],
  "resolved_foreshadowing": ["已回收伏笔描述"],
  "story_momentum": "当前故事势能: 上升/下降/转折/高潮前蓄力/高潮后余波",
  "core_events": [],
  "hook_level": "中"
}
```

#### 4.3.2 修改 SummaryAgentServiceImpl

更新 Prompt 模板（数据库 prompt_templates 表的 `summary` 记录）和解析逻辑。

**修改文件：**
- `SummaryAgentServiceImpl.java` — 更新 `parseSummaryResponse` 方法，解析新增字段
- `SummaryAgentResponse.java` — 新增字段
- `ChapterGenerationServiceImpl.java` — 在 `generateSummary` 后，将 character_updates 写入 MongoDB，将 used_tropes 存入 chapter，更新 story_momentum

---

## 五、阶段二：运营功能补齐 (P1)

### 5.1 模块四：模型 Fallback 机制

**目标**：移植 Super-Ma 的 `withModelFallback` 机制。

#### 5.1.1 设计思路

在 `ChapterGenerationChain` 中实现 Fallback：

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class ModelFallbackService {
    
    private final LlmConfigService llmConfigService;
    private final LangChainConfig langChainConfig;
    
    /**
     * 执行带 Fallback 的 AI 调用
     * @param primaryConfigId 主模型配置ID
     * @param task 执行任务 (接收 ChatLanguageModel，返回结果)
     * @param onFallback 切换模型时的回调 (用于 SSE 通知前端)
     */
    public <T> T executeWithFallback(String primaryConfigId, 
                                      Function<ChatLanguageModel, T> task,
                                      Consumer<LlmConfig> onFallback) {
        List<LlmConfig> models = getFallbackList(primaryConfigId);
        
        for (int i = 0; i < models.size(); i++) {
            LlmConfig config = models.get(i);
            try {
                if (i > 0 && onFallback != null) {
                    onFallback.accept(config);
                }
                ChatLanguageModel model = langChainConfig.createModel(config);
                return task.apply(model);
            } catch (Exception e) {
                log.warn("模型 {} 调用失败: {}, 尝试下一个", config.getName(), e.getMessage());
                if (i == models.size() - 1) {
                    throw new RuntimeException("所有模型均失败", e);
                }
            }
        }
        throw new RuntimeException("无可用模型");
    }
    
    private List<LlmConfig> getFallbackList(String primaryId) {
        // 1. 主模型
        // 2. 默认模型
        // 3. 其他可用模型（按优先级排序）
        // 返回有序列表
    }
}
```

#### 5.1.2 修改 ChapterGenerationChain

将 `getModel(llmConfigId)` 替换为 `modelFallbackService.executeWithFallback(...)`。

**修改文件：**
- 新建 `ModelFallbackService.java`
- `ChapterGenerationChain.java` — 3个 `execute*Chain` 方法均改为使用 Fallback

---

### 5.2 模块五：SSE 流式写作 + 取消机制

**目标**：实现 Super-Ma 的 SSE 流式输出和取消写作功能。

#### 5.2.1 新建 StreamingWritingController

```java
@RestController
@RequestMapping("/api/chapter")
@RequiredArgsConstructor
public class StreamingChapterController {
    
    private final ChapterGenerationChain chain;
    private final ModelFallbackService fallbackService;
    private final StringRedisTemplate redisTemplate;
    
    @PostMapping(value = "/stream-write", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamWrite(@RequestBody StreamWriteRequest request) {
        SseEmitter emitter = new SseEmitter(600_000L); // 10min 超时
        String cancelKey = "novel:cancel:" + request.getNovelId();
        
        CompletableFuture.runAsync(() -> {
            try {
                redisTemplate.delete(cancelKey); // 清除旧的取消标记
                
                fallbackService.executeWithFallback(request.getLlmConfigId(), model -> {
                    // 使用 LangChain4j 的 streaming model
                    StreamingChatLanguageModel streamingModel = 
                        langChainConfig.createStreamingModel(model.getConfig());
                    
                    StringBuilder fullContent = new StringBuilder();
                    
                    streamingModel.chat(request.getMessages(), new StreamingChatResponseHandler() {
                        @Override
                        public void onNext(String token) {
                            // 检查取消
                            if (Boolean.TRUE.equals(redisTemplate.hasKey(cancelKey))) {
                                throw new RuntimeException("用户取消了写作");
                            }
                            fullContent.append(token);
                            try {
                                emitter.send(SseEmitter.event().data(token));
                            } catch (IOException e) {
                                throw new RuntimeException("SSE发送失败", e);
                            }
                        }
                        
                        @Override
                        public void onComplete(Response response) {
                            try {
                                emitter.send(SseEmitter.event()
                                    .name("complete")
                                    .data(fullContent.toString()));
                                emitter.complete();
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        }
                        
                        @Override
                        public void onError(Throwable error) {
                            emitter.completeWithError(error);
                        }
                    });
                    
                    return fullContent.toString();
                }, fallbackConfig -> {
                    // 通知前端模型切换
                    try {
                        emitter.send(SseEmitter.event()
                            .name("model_switch")
                            .data(Map.of("next_model", fallbackConfig.getName())));
                    } catch (IOException ignored) {}
                });
                
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
        
        return emitter;
    }
    
    /**
     * 取消写作
     */
    @PostMapping("/cancel")
    public ResponseEntity<?> cancelWriting(@RequestParam String novelId) {
        redisTemplate.opsForValue().set("novel:cancel:" + novelId, "1", 10, TimeUnit.MINUTES);
        return ResponseEntity.ok().build();
    }
}
```

---

### 5.3 模块六：章节版本快照

**目标**：在重新生成章节前自动保存旧版本。

#### 5.3.1 新建表 `chapter_versions`

```sql
CREATE TABLE chapter_versions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    chapter_id BIGINT NOT NULL,
    version INT NOT NULL,
    content TEXT NOT NULL,
    outline TEXT,
    title VARCHAR(200),
    words INT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_chapter_version (chapter_id, version DESC)
);
```

#### 5.3.2 新建 ChapterVersionService

```java
@Service
@RequiredArgsConstructor
public class ChapterVersionService {
    
    private final ChapterVersionMapper versionMapper;
    private static final int MAX_VERSIONS = 10;
    
    /**
     * 保存版本快照（在重新生成前调用）
     */
    public void saveSnapshot(NovelChapter chapter) {
        if (chapter.getContent() == null || chapter.getWordCount() < 100) {
            return; // 内容太少，不保存
        }
        
        Integer maxVer = versionMapper.selectMaxVersion(chapter.getChapterId());
        int newVer = (maxVer != null ? maxVer : 0) + 1;
        
        ChapterVersion version = new ChapterVersion();
        version.setChapterId(chapter.getChapterId());
        version.setVersion(newVer);
        version.setContent(chapter.getContent());
        version.setOutline(chapter.getOutline());
        version.setTitle(chapter.getChapterTitle());
        version.setWords(chapter.getWordCount());
        versionMapper.insert(version);
        
        // 只保留最近10个版本
        versionMapper.deleteOldVersions(chapter.getChapterId(), MAX_VERSIONS);
    }
}
```

**修改文件：**
- `ChapterGenerationServiceImpl.java` — 在 `generateChapter` 开头调用 `chapterVersionService.saveSnapshot(existingChapter)`

---

## 六、阶段三：高级特性增强 (P2)

### 6.1 模块七：温度按任务类型动态调整

**目标**：移植 Super-Ma 的 `TASK_TEMPERATURES` 策略。

#### 6.1.1 修改 LangChainConfig

```java
@Configuration
public class LangChainConfig {
    
    public enum TaskType {
        CREATIVE(null),      // 正文写作：用用户配置值
        STRUCTURED(0.3f),    // JSON提取：低随机性
        SYNOPSIS(0.5f);      // 章节简介：适中
        
        private final Float temperature;
        TaskType(Float temperature) { this.temperature = temperature; }
        public Float getTemperature() { return temperature; }
    }
    
    public ChatLanguageModel createModel(LlmConfig config, TaskType taskType) {
        Float temperature = taskType.getTemperature() != null 
            ? taskType.getTemperature() 
            : config.getTemperature();
        
        return OpenAiChatModel.builder()
            .apiKey(config.getApiKey())
            .modelName(config.getModelName())
            .baseUrl(config.getApiUrl())
            .temperature(temperature.doubleValue())
            .maxTokens(config.getMaxTokens())
            .build();
    }
}
```

#### 6.1.2 修改 Agent 调用

- OutlineAgent 使用 `TaskType.STRUCTURED`
- WritingAgent 使用 `TaskType.CREATIVE`
- SummaryAgent 使用 `TaskType.STRUCTURED`
- ChapterSynopsis 使用 `TaskType.SYNOPSIS`

---

## 七、数据库变更汇总

| 表名 | 变更类型 | 说明 |
|------|---------|------|
| `arc_summaries` | 新建表 | 弧段摘要 |
| `chapter_versions` | 新建表 | 章节版本快照 |
| `chapter_synopses` | 新建表 | 章节简介蓝图 |
| `novel_base` | 新增字段 | `story_momentum`, `pending_foreshadowing` |
| `novel_chapter` | 新增字段 | `hook`, `key_points`, `chapter_summary`, `used_tropes`, `synopsis_id` |
| `clue_book` | 新增字段 | `deadline` (伏笔回收截止章节) |
| `prompt_templates` | 更新记录 | 更新 `outline`, `writing`, `summary` 模板 |

---

## 八、文件变更汇总

### 新建文件 (12个)

| 文件路径 | 说明 |
|---------|------|
| `ai/agent/ArcSummaryAgent.java` | 弧段摘要 Agent 接口 |
| `ai/agent/ArcSummaryAgentServiceImpl.java` | 弧段摘要 Agent 实现 |
| `entity/mysql/ArcSummary.java` | 弧段摘要实体 |
| `entity/mysql/ChapterVersion.java` | 章节版本实体 |
| `entity/mysql/ChapterSynopsis.java` | 章节简介实体 |
| `mapper/ArcSummaryMapper.java` + `.xml` | 弧段摘要 Mapper |
| `mapper/ChapterVersionMapper.java` + `.xml` | 章节版本 Mapper |
| `service/chapter/ContextBuilderService.java` | 三层记忆上下文构建服务 |
| `service/chapter/ModelFallbackService.java` | 模型 Fallback 服务 |
| `service/chapter/ChapterVersionService.java` | 章节版本服务 |
| `controller/StreamingChapterController.java` | SSE 流式写作控制器 |

### 修改文件 (6个)

| 文件路径 | 修改内容 |
|---------|---------|
| `ai/agent/OutlineAgent.java` | 重写 System Prompt (铁律体系) |
| `ai/agent/WritingAgent.java` | 重写 System Prompt (写作铁律) |
| `ai/agent/SummaryAgentServiceImpl.java` | 扩展输出格式 + 解析逻辑 |
| `ai/agent/SummaryAgentResponse.java` | 新增字段 |
| `service/chapter/ChapterGenerationServiceImpl.java` | 替换上下文构建、添加版本快照、添加 Fallback |
| `ai/chain/ChapterGenerationChain.java` | 集成 Fallback、TaskType |

---

## 九、测试验证计划

### 9.1 单元测试

| 测试项 | 验证内容 |
|--------|---------|
| ContextBuilderService | 三层记忆正确组装、伏笔deadline标记、意象去重 |
| ModelFallbackService | 主模型失败后自动切换、所有模型失败抛异常 |
| SummaryAgentResponse | 新增字段解析正确、容错处理 |
| ChapterVersionService | 版本快照保存、自动清理旧版本 |

### 9.2 集成测试

| 测试场景 | 验证内容 |
|---------|---------|
| 完整章纲生成 | Prompt 包含三层记忆 + 关键事件 + 伏笔deadline |
| 完整章节写作 | Prompt 包含前文衔接 + 意象去重 + 章节简介蓝图 |
| 校验失败重试 | 5维校验失败后自动重试，注入校验反馈 |
| 模型 Fallback | 主模型超时/报错后自动切换备用模型 |
| SSE 流式写作 | 实时输出 + 取消写作 + 版本快照 |

### 9.3 效果对比测试

使用同一本小说的同一章节号，对比移植前后：
1. 大纲生成的连贯性（是否承接了上一章钩子）
2. 正文生成的一致性（人物状态是否准确）
3. 伏笔回收率（是否有伏笔被遗忘）
4. 关键事件是否重复
5. 意象是否重复

---

## 十、风险与应对

| 风险 | 影响 | 应对措施 |
|------|------|---------|
| Prompt 过长导致 token 超限 | 生成失败 | 对弧段摘要/关键事件做截断限制，总 token 控制在 4K 以内 |
| 三层记忆查询性能 | 页面响应慢 | Redis 缓存 + 批量查询优化 |
| Fallback 切换导致输出不连贯 | 文风突变 | 只在首次请求失败时切换，流式过程中不切换 |
| 数据库 migration 失败 | 系统不可用 | 先在测试环境验证，提供回滚 SQL |

---

## 十一、实施时间表

```
第 1-2 天：数据库 migration + 实体层 + Mapper 层
第 3-4 天：ContextBuilderService (三层记忆 + 伏笔deadline + 关键事件)
第 5 天  ：Agent System Prompt 重写 + 摘要输出扩展
第 6-7 天：ModelFallbackService + SSE 流式写作
第 8-9 天：章节版本快照 + 取消写作
第 10 天 ：温度按任务类型动态调整
第 11-13天：集成测试 + 效果对比测试
第 14-15天：Bug修复 + 性能优化 + 文档更新
```

---

## 十二、移植后预期效果

1. **大纲连贯性**：三层记忆确保 AI 不会遗忘早期剧情，hook 传递保证章节衔接
2. **伏笔回收率**：deadline 紧急提示 + 意象去重，减少伏笔遗忘和套路化重复
3. **写作质量**：关键事件去重 + 章节简介蓝图 + 写作铁律，提升一致性
4. **系统健壮性**：模型 Fallback + 取消写作 + 版本快照，提升可用性
5. **可维护性**：ContextBuilderService 集中管理 Prompt 构建逻辑，后续迭代更方便

---

*本文档由 AI 辅助生成，需人工审核后执行。*
