# moyun-ts Bug修复 + 核心功能补全 — 设计文档 v2

> 修复 5 个严重 Bug，补全 4 个核心差异化功能（Agent图执行器、RAG向量检索、文风卡片深度系统、SSE流式写作）。

**目标：** 将 moyun-ts 从 ~60% 功能完成度提升到 ~90%，与 Java 版核心能力对齐。

---

## Part 1: 严重 Bug 修复

### Bug 1: 字数统计正则表达式错误

**文件：** `chapter.service.ts:143`, `chapter-generation.service.ts:270`, `writing.chain.ts:71`

**问题：** `[一-鿿]` 范围不覆盖中文标点，且与 Java 版逻辑不一致（Java 版用 `replaceAll("\\s+", "").length()`）。

**修复：** 统一改为 `text.replace(/\s+/g, '').length`，三处同步修改。更新测试期望值。提取到 `utils/text.ts` 的 `countWords(text)` 公共函数。

### Bug 2: migrate.ts 和 db/index.ts 使用不同数据库连接

**文件：** `db/migrate.ts`, `db/index.ts`

**问题：** migrate 自己 `new Database()` 然后 close，与 drizzle 实例使用不同连接。

**修复：** 让 `migrate.ts` 导出 `runMigrations(db: BetterSqlite3.Database)` 函数，在 `db/index.ts` 的 `getDb()` 中首次创建时调用。移除独立的 Database 实例。

### Bug 3: 重复生成章节时 create 冲突

**文件：** `chapter-generation.service.ts:169-177`

**问题：** `chapterService.create()` 对已存在的章节会因主键冲突报错。

**修复：** 在 `ChapterService` 中添加 `upsert()` 方法（先查后 insert/update），`ChapterGenerationService` 改用 `upsert()`。

### Bug 4: WizardService 批量生成状态在 Web 模式下不可用

**文件：** `wizard.service.ts:42-43`

**问题：** 每个请求 new WizardService(db)，Map/Set 状态丢失。

**修复：** 将 `batchTasks` 和 `batchCancelled` 改为模块级单例（文件顶层 `const`），所有 WizardService 实例共享。

```typescript
// wizard.service.ts 文件顶层
const batchTasks = new Map<string, BatchProgress>();
const batchCancelled = new Set<string>();

export class WizardService {
  // 不再在 constructor 中初始化，直接使用模块级变量
}
```

这是最简方案，无需改路由层或引入依赖注入。

### Bug 5: novel export 命令参数顺序错误

**文件：** `cli/src/commands/novel.ts:92`

**问题：** Commander.js v12 中 `.action((id, opts) => {})` 签名错误。

**修复：** 改为 `.action((opts) => { const id = opts.args[0]; ... })` 或使用 Commander 的 `.argument()` + `.action((id, opts) => {})` 模式。需要验证 Commander 版本的实际行为。

### Bug 6: ValidationService 失败默认 passed: true（从 Part 6 提升）

**文件：** `validation.service.ts:145`

**修复：** LLM 调用异常时改为 `passed: false`。

### Bug 7: llm-factory 缺少 maxTokens（从 Part 6 提升）

**文件：** `llm-factory.ts`

**修复：** `createLLMWithProvider` 添加 `maxTokens` 参数，写作默认 4096，大纲默认 2048。

---

## Part 2: SSE 流式写作（提前执行）

> SSE 提前到 Agent 图之前，因为它依赖的 WritingChain 已经存在，且是用户最直接感知的功能。

### 端点

```
POST /api/chapters/stream-write  — SSE 逐 token 流式输出
POST /api/chapters/cancel        — 取消信号（内存 Map<string, AbortController>）
POST /api/chapters/stream-save   — 保存中断内容（取消后手动保存）
```

### 实现

```typescript
// server/routes/streaming.ts
const cancelSignals = new Map<string, AbortController>();

app.post('/api/chapters/stream-write', async (req, reply) => {
  const { novelId, chapterNum, outline, styleCardId } = req.body;

  reply.raw.writeHead(200, {
    'Content-Type': 'text/event-stream',
    'Cache-Control': 'no-cache',
    'Connection': 'keep-alive',
  });

  const abortController = new AbortController();
  cancelSignals.set(novelId, abortController);

  try {
    // 1. 如果是重写场景，先保存版本快照
    const existing = chapterService.getByNum(novelId, chapterNum);
    if (existing?.content) {
      chapterService.saveVersion(existing.id, existing.content);
    }

    // 2. 流式生成（复用 WritingChain.executeStream）
    let fullContent = '';
    const writingChain = new WritingChain(llm);
    for await (const token of writingChain.executeStream({ ... })) {
      if (abortController.signal.aborted) break;
      fullContent += token;
      reply.raw.write(`data: ${JSON.stringify({ token })}\n\n`);
    }

    // 3. 完成后自动校验 + 摘要 + 保存
    if (!abortController.signal.aborted) {
      const validation = await validationService.validateAll({ content: fullContent, ... });
      reply.raw.write(`event: validation\ndata: ${JSON.stringify(validation)}\n\n`);

      const summary = await summaryChain.execute({ content: fullContent, ... });
      chapterService.upsert({ novelId, chapterNum, content: fullContent, ... });

      reply.raw.write(`event: complete\ndata: ${JSON.stringify({ wordCount: countWords(fullContent) })}\n\n`);
    }
  } catch (err) {
    reply.raw.write(`event: error\ndata: ${JSON.stringify({ message: err.message })}\n\n`);
  } finally {
    cancelSignals.delete(novelId);
    reply.raw.end();
  }
});

// 取消
app.post('/api/chapters/cancel', async (req) => {
  const { novelId } = req.body;
  cancelSignals.get(novelId)?.abort();
  return { ok: true };
});

// 保存中断内容（取消后手动保存）
app.post('/api/chapters/stream-save', async (req) => {
  const { novelId, chapterNum, content, outline } = req.body;
  const summary = await summaryChain.execute({ content, ... });
  chapterService.upsert({ novelId, chapterNum, content, ... });
  return { success: true, wordCount: countWords(content) };
});
```

### SSE 事件类型

| 事件名 | 时机 | 数据格式 |
|--------|------|----------|
| (默认) | 每个 token | `{ token: "..." }` |
| `validation` | 校验完成后 | `ValidationResults` JSON |
| `complete` | 保存完成后 | `{ wordCount: N }` |
| `error` | 异常时 | `{ message: "..." }` |

### 取消机制

使用内存 `Map<string, AbortController>` 替代 Redis。`WritingChain.executeStream()` 接受 `AbortSignal`，在每个 token 回调中检查 `signal.aborted`。取消后前端可调用 `stream-save` 保存已生成的部分内容。

### 版本快照

重写已有章节时，在流式生成前先调用 `chapterService.saveVersion()` 保存当前版本到 `chapter_versions` 表。用户可回滚到之前的版本。

### 前端 SSE Hook

```typescript
// client/hooks/useSSE.ts
function useSSE(url: string) {
  const [content, setContent] = useState('');
  const [isStreaming, setIsStreaming] = useState(false);
  const [validation, setValidation] = useState<ValidationResults | null>(null);
  const abortRef = useRef<AbortController>();

  const start = async (body: object) => {
    abortRef.current = new AbortController();
    setIsStreaming(true);
    setContent('');
    setValidation(null);

    const res = await fetch(url, {
      method: 'POST',
      body: JSON.stringify(body),
      headers: { 'Content-Type': 'application/json' },
      signal: abortRef.current.signal,
    });
    const reader = res.body!.getReader();
    const decoder = new TextDecoder();

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      const text = decoder.decode(value);
      for (const line of text.split('\n')) {
        if (line.startsWith('data: ')) {
          try {
            const data = JSON.parse(line.slice(6));
            if (data.token) setContent(prev => prev + data.token);
          } catch { /* plain text fallback */ }
        }
        if (line.startsWith('event: validation')) {
          // next data line is validation result
        }
      }
    }
    setIsStreaming(false);
  };

  const cancel = () => abortRef.current?.abort();

  return { content, isStreaming, validation, start, cancel };
}
```

---

## Part 3: Agent 图执行器

### 架构

移植 Java 版的 `NovelGraphNode` + `NovelAgentState` + `MultiAgentNovelService` 到 TypeScript。使用**图执行器**（非线性流水线），支持条件跳转和循环。

### 节点接口

```typescript
// types/graph.ts
interface NovelGraphNode {
  nodeName: string;
  apply(state: NovelAgentState): Promise<NovelAgentState>;
  shouldSkip?(state: NovelAgentState): boolean;  // 条件跳过
}

type NodeRoute = string | null;  // 返回节点名跳转，null 继续线性执行

interface NodeResult {
  state: NovelAgentState;
  nextNode?: NodeRoute;  // 允许节点指定下一个执行的节点
}
```

### 数据结构

```typescript
interface NovelAgentState {
  // 请求输入
  novelId: string;
  chapterNum: number;
  userIntent?: string;
  styleCardId?: string;

  // 上下文（构建一次复用）
  outlineContext?: string;
  writingContext?: string;

  // Phase 1: 规划
  chapterPlan?: ChapterPlan;
  preWriteValidation?: PreWriteValidation;

  // Phase 2: 写作
  selectedIdea?: string;
  draftContent?: DraftContent;

  // Phase 3: 审计
  validationResults?: ValidationResults;
  qualityScore?: number;
  auditDecision?: 'PASS' | 'REWRITE' | 'REPLAN' | 'MANUAL_REVIEW';
  selfReviewNotes?: string;

  // Phase 4: 最终
  finalResult?: FinalResult;

  // 流程控制
  iterationCount: number;
  planRetryCount: number;
  writeRetryCount: number;
  rewriteCount: number;        // 防止无限循环
  currentPhase: GenerationPhase;
  executionTrace: string[];
  errors: string[];
}

type GenerationPhase =
  | 'INIT' | 'CONTEXT' | 'ARC_CHECK' | 'OUTLINE'
  | 'CLUE_PLAN' | 'CHAR_PLAN' | 'PRE_WRITE_VALID'
  | 'IDEA_SELECT' | 'WRITING' | 'SELF_REVIEW'
  | 'VALIDATION' | 'QUALITY_CHECK' | 'AUDIT_ROUTER'
  | 'SUMMARY' | 'POST_PROCESS' | 'COMPLETED' | 'FAILED';
```

不可变模式：每个节点返回新对象（`{ ...state, ...changes }`）。

### 节点清单（14个）

| 节点 | 文件 | 职责 | 条件跳转 |
|------|------|------|----------|
| ArcCheckNode | `nodes/arc-check.node.ts` | 检查卷级编排计划 | 无编排计划 → 跳到 IDEA_SELECT |
| OutlineNode | `nodes/outline.node.ts` | 生成章节大纲（复用 OutlineChain） | — |
| CluePlanningNode | `nodes/clue-planning.node.ts` | 伏笔规划（卷约束 + LLM 补充） | — |
| CharacterPlanningNode | `nodes/character-planning.node.ts` | 角色动线规划 + 程序化校验 | — |
| WorldValidatorNode | `nodes/world-validator.node.ts` | 写前世界观一致性校验 | violations 为空 → 跳到 IDEA_SELECT |
| IdeaSelectionNode | `nodes/idea-selection.node.ts` | 实现思路选择 | — |
| WritingNode | `nodes/writing.node.ts` | 正文生成（复用 WritingChain） | — |
| SelfReviewNode | `nodes/self-review.node.ts` | 自审（LOW/MEDIUM/HIGH） | HIGH → 跳回 WRITING（重写） |
| ValidationNode | `nodes/validation.node.ts` | 五维验证（复用 ValidationService） | — |
| QualityScoringNode | `nodes/quality-scoring.node.ts` | 质量评分 + 伏笔落地检查 | — |
| **AuditRouterNode** | `nodes/audit-router.node.ts` | **审计路由：决定重写/重规划/通过** | allPassed → SUMMARY; OOC/setting fail → OUTLINE; 其他 → WRITING |
| SummaryNode | `nodes/summary.node.ts` | 摘要生成（复用 SummaryChain） | — |
| PostProcessNode | `nodes/post-process.node.ts` | 保存 + 后处理 + 更新角色/事件状态 | — |

### 图执行器（GraphRunner）

```typescript
// ai/graph/graph-runner.ts
class GraphRunner {
  private nodes = new Map<string, NovelGraphNode>();

  register(node: NovelGraphNode): void {
    this.nodes.set(node.nodeName, node);
  }

  async execute(initialState: NovelAgentState): Promise<NovelAgentState> {
    const nodeOrder = [
      'arc_check', 'outline', 'clue_planning', 'character_planning',
      'world_validate', 'idea_select', 'writing', 'self_review',
      'validation', 'quality_check', 'audit_router', 'summary', 'post_process'
    ];

    let state = initialState;
    let currentIndex = 0;
    const maxIterations = 20;  // 安全阀

    for (let iter = 0; iter < maxIterations && currentIndex < nodeOrder.length; iter++) {
      const nodeName = nodeOrder[currentIndex];
      const node = this.nodes.get(nodeName)!;

      // 条件跳过检查
      if (node.shouldSkip?.(state)) {
        state = { ...state, executionTrace: [...state.executionTrace, `${nodeName}(skipped)`] };
        currentIndex++;
        continue;
      }

      // 执行节点
      const result = await node.apply(state);
      state = result;

      // 路由决策：节点可以通过 state.auditDecision 或返回值指定跳转
      const nextNode = this.resolveNextNode(nodeName, state);
      if (nextNode !== null) {
        const targetIndex = nodeOrder.indexOf(nextNode);
        if (targetIndex >= 0) {
          currentIndex = targetIndex;
          continue;
        }
      }
      currentIndex++;
    }

    return state;
  }

  private resolveNextNode(currentNode: string, state: NovelAgentState): string | null {
    // AuditRouter 的路由决策
    if (currentNode === 'audit_router') {
      if (state.auditDecision === 'PASS') return 'summary';
      if (state.auditDecision === 'REPLAN') return 'outline';
      if (state.auditDecision === 'REWRITE') return 'writing';
      if (state.auditDecision === 'MANUAL_REVIEW') return null; // 停止，交给用户
    }

    // SelfReview 的路由决策
    if (currentNode === 'self_review' && state.draftContent?.severity === 'HIGH') {
      if (state.rewriteCount >= 2) return null; // 超过重试次数，继续
      return 'writing';
    }

    // ArcCheck 的条件跳转
    if (currentNode === 'arc_check' && !state.chapterPlan) {
      return 'idea_select'; // 无编排计划，跳过规划节点
    }

    return null; // 继续线性执行
  }
}
```

### AuditRouterNode 逻辑

```typescript
class AuditRouterNode implements NovelGraphNode {
  nodeName = 'audit_router';

  async apply(state: NovelAgentState): Promise<NovelAgentState> {
    const { validationResults, qualityScore, iterationCount, rewriteCount } = state;

    if (!validationResults) {
      return { ...state, auditDecision: 'PASS', currentPhase: 'AUDIT_ROUTER' };
    }

    const allPassed = validationResults.allPassed;

    if (allPassed && (qualityScore ?? 0) >= 70) {
      return { ...state, auditDecision: 'PASS', currentPhase: 'AUDIT_ROUTER' };
    }

    // 超过最大迭代次数
    if (iterationCount >= 4 || rewriteCount >= 3) {
      return { ...state, auditDecision: 'MANUAL_REVIEW', currentPhase: 'AUDIT_ROUTER' };
    }

    // OOC 或设定失败 → 需要重新规划
    if (!validationResults.ooc.passed || !validationResults.setting.passed) {
      return {
        ...state,
        auditDecision: 'REPLAN',
        iterationCount: iterationCount + 1,
        currentPhase: 'AUDIT_ROUTER',
      };
    }

    // 其他失败 → 仅重写
    return {
      ...state,
      auditDecision: 'REWRITE',
      iterationCount: iterationCount + 1,
      rewriteCount: rewriteCount + 1,
      currentPhase: 'AUDIT_ROUTER',
    };
  }
}
```

### 集成方式

- `ChapterGenerationService` 保留为简单线性流水线（向后兼容）
- 新增 `GraphChapterGenerationService` 使用图执行器
- Web 路由和 CLI 可通过参数选择使用哪种模式
- 后续可将默认模式切换为图执行器

### 新增类型

```
types/graph.ts              — NovelAgentState, ChapterPlan, ClueAction, CharacterAction,
                              PreWriteValidation, DraftContent, AuditResult, FinalResult, GenerationPhase
ai/graph/graph-runner.ts    — GraphRunner（图执行器）
ai/nodes/*.node.ts          — 14 个节点实现
services/story-state.service.ts    — 故事状态管理
services/character-state.service.ts — 角色状态管理
services/event-graph.service.ts    — 事件图服务
```

---

## Part 4: RAG 向量检索

### 存储方案

SQLite 表存储向量（BLOB），内存计算余弦相似度。

```sql
CREATE TABLE IF NOT EXISTS vectors (
  id TEXT PRIMARY KEY,
  novel_id TEXT NOT NULL,
  chapter_num INTEGER NOT NULL,
  content TEXT,
  keywords TEXT,
  priority INTEGER DEFAULT 5,
  embedding BLOB NOT NULL,
  created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_vectors_novel ON vectors(novel_id);
```

### 架构

```
EmbeddingService       — 调用 OpenAI 兼容 API 生成向量（从 llmConfigs 读取配置）
VectorStore            — SQLite BLOB 存储 + 内存余弦搜索
RagService             — 混合检索（向量 + 关键词 + RRF 融合）+ 记忆压缩
RuleDrivenRetrieval    — 基于事件图的规则驱动检索（4 条规则）
```

### EmbeddingService

从 `llmConfigs` 表读取 `usageType='embedding'` 的配置（找不到则 fallback 到 `'general'`），使用 OpenAI 兼容的 `/embeddings` API。输入截断到 2000 字符（约 2048 token）。

```typescript
// ai/rag/embedding.service.ts
class EmbeddingService {
  constructor(private db: Db) {}

  async embed(text: string): Promise<Float32Array> {
    const config = this.getEmbeddingConfig(); // 从 llmConfigs 表读取
    const response = await fetch(config.baseUrl + '/embeddings', {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${config.apiKey}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ model: config.model, input: text.substring(0, 2000) }),
    });
    const data = await response.json();
    return new Float32Array(data.data[0].embedding);
  }

  cosineSimilarity(a: Float32Array, b: Float32Array): number { ... }
}
```

### VectorStore

```typescript
// ai/rag/vector-store.ts
class VectorStore {
  constructor(private db: Db) {}

  save(key: string, novelId: string, chapterNum: number, embedding: Float32Array, metadata: Record<string, unknown>): void {
    this.db.insert(vectors).values({
      id: key, novelId, chapterNum,
      content: metadata.content, keywords: metadata.keywords, priority: metadata.priority,
      embedding: Buffer.from(embedding.buffer),
      createdAt: new Date(),
    }).run();
  }

  search(novelId: string, queryEmbedding: Float32Array, topK: number = 5): VectorSearchResult[] {
    const rows = this.db.select().from(vectors).where(eq(vectors.novelId, novelId)).all();
    return rows
      .map(row => ({
        id: row.id,
        score: this.cosineSimilarity(queryEmbedding, new Float32Array(row.embedding)),
        metadata: { chapterNum: row.chapterNum, content: row.content, keywords: row.keywords, priority: row.priority },
      }))
      .sort((a, b) => b.score - a.score)
      .slice(0, topK);
  }
}
```

### RagService — 混合检索 + 记忆压缩

```typescript
// ai/rag/rag.service.ts
class RagService {
  // 混合检索：向量 + 关键词 + RRF 融合
  async hybridSearch(novelId: string, query: string, topK: number = 5): Promise<SearchResult[]> {
    // 1. 向量搜索
    const embedding = await this.embeddingService.embed(query);
    const vectorResults = this.vectorStore.search(novelId, embedding, topK * 2);

    // 2. BM25 关键词搜索（使用 minisearch，不依赖 FTS5）
    const keywordResults = this.miniSearch.search(query, { filter: (r) => r.novelId === novelId });

    // 3. RRF 融合（k=60）
    return this.rrfFusion(vectorResults, keywordResults, topK);
  }

  // 保存章节摘要（向量 + 关键词索引双写）
  async saveSummary(novelId: string, chapterNum: number, content: string, keywords: string[]): Promise<void> {
    const embedding = await this.embeddingService.embed(content);
    this.vectorStore.save(`${novelId}:${chapterNum}`, novelId, chapterNum, embedding, { content, keywords, priority: 5 });
    this.miniSearch.add({ id: `${novelId}:${chapterNum}`, novelId, chapterNum, content, keywords: keywords.join(' ') });
  }

  // LLM 驱动的记忆压缩：将早期章节摘要压缩为全局背景
  async compressMemory(novelId: string, startChapter: number, endChapter: number): Promise<string> {
    const summaries = this.getSummariesInRange(novelId, startChapter, endChapter);
    const combined = summaries.map(s => s.content).join('\n');
    if (combined.length < 2000) return combined; // 太短不需要压缩

    const llm = this.getLLM('general');
    const response = await llm.invoke([
      new SystemMessage('你是一位小说编辑，擅长将章节摘要压缩为精炼的全局背景记忆。'),
      new HumanMessage(`请将以下 ${endChapter - startChapter + 1} 章的摘要压缩为800字以内的"全局背景记忆"，保留关键人物、主线剧情、重要伏笔：\n\n${combined}`),
    ]);
    return typeof response.content === 'string' ? response.content : JSON.stringify(response.content);
  }

  // 六层上下文组装（L0 = 全局压缩记忆）
  assembleContext(params: {
    novelId: string;
    chapterNum: number;
    totalOutline?: string;
    currentArcOutline?: string;
    clueBook?: string;
    characterStates?: string;
    query: string;
  }): Promise<string> {
    const parts: string[] = [];

    // L0: 全局压缩记忆（如果章节数 > 20 且有压缩记忆）
    const compressedMemory = this.getCompressedMemory(params.novelId);
    if (compressedMemory) parts.push(`【全局背景记忆】\n${compressedMemory}`);

    // L1: 小说总纲（截断 2000 字）
    if (params.totalOutline) parts.push(`【总大纲】\n${params.totalOutline.substring(0, 2000)}`);

    // L2: 当前卷纲（截断 1000 字）
    if (params.currentArcOutline) parts.push(`【当前卷纲】\n${params.currentArcOutline.substring(0, 1000)}`);

    // L3: 未回收伏笔
    if (params.clueBook) parts.push(`【待回收伏笔】\n${params.clueBook}`);

    // L4: 人物状态（截断 1000 字）
    if (params.characterStates) parts.push(`【人物状态】\n${params.characterStates.substring(0, 1000)}`);

    // L5: 混合检索相关剧情
    const ragResults = await this.hybridSearch(params.novelId, params.query, 5);
    if (ragResults.length > 0) {
      parts.push(`【相关剧情回忆】\n${ragResults.map(r => r.content).join('\n')}`);
    }

    return parts.join('\n\n');
  }
}
```

### BM25 方案：minisearch（不使用 FTS5）

FTS5 对中文支持极差（无分词），改用 JavaScript 全文检索库 `minisearch`：

- `minisearch` 支持自定义 tokenize 函数
- 使用简单的中文分词：按标点 + 空格分割，再做 2-gram 滑窗
- 不需要 `jieba-wasm`（额外依赖），2-gram 对短文本（摘要级别）足够
- 小说场景数据量小（通常 <1000 条），性能完全够用

```typescript
// ai/rag/bm25-search.ts
import MiniSearch from 'minisearch';

const miniSearch = new MiniSearch({
  fields: ['content', 'keywords'],
  storeFields: ['novelId', 'chapterNum', 'content'],
  tokenize: (text) => chineseTokenize(text),
});

function chineseTokenize(text: string): string[] {
  // 按标点和空格分割
  const segments = text.split(/[，。！？：；""''（）、\s]+/).filter(Boolean);
  // 2-gram 滑窗
  const tokens: string[] = [];
  for (const seg of segments) {
    for (let i = 0; i < seg.length - 1; i++) {
      tokens.push(seg.substring(i, i + 2));
    }
    if (seg.length >= 2) tokens.push(seg); // 完整词也加入
  }
  return tokens;
}
```

### Rerank：关键词匹配 + 优先级加权

```typescript
private rerank(results: SearchResult[], query: string, keywords: string[]): SearchResult[] {
  return results.map(r => {
    let bonus = 0;
    // 关键词匹配加分
    for (const kw of keywords) {
      if (r.content.includes(kw)) bonus += 0.1;
    }
    // 查询子串匹配加分
    if (r.content.includes(query)) bonus += 0.2;
    // 优先级加权
    bonus += (r.priority ?? 5) * 0.02;
    return { ...r, score: r.score + bonus };
  }).sort((a, b) => b.score - a.score);
}
```

### 集成到 ContextBuilder

ContextBuilder 改为调用 `RagService.assembleContext()` 获取完整的六层上下文，替代自己手动拼接 L1-L5：

```typescript
// context-builder.ts
class ContextBuilder {
  async build(novelId: string, chapterNum: number): Promise<GenerationContext> {
    const novel = this.novelService.getById(novelId);
    const arc = this.getCurrentArc(novelId, chapterNum);

    // 使用 RagService 组装完整上下文
    const fullContext = await this.ragService.assembleContext({
      novelId,
      chapterNum,
      totalOutline: novel.totalOutline,
      currentArcOutline: arc?.outline,
      clueBook: this.getClueBook(novelId),
      characterStates: this.getCharacterStates(novelId),
      query: this.buildQuery(novel, chapterNum),
    });

    return { fullContext, storyState: ... };
  }
}
```

### Schema 变更

在 `db/schema.ts` 中添加 `vectors` 表，在 `db/migrate.ts` 中添加建表语句。

---

## Part 5: 文风卡片深度系统

### Schema 变更

扩展 `style_cards` 表，添加 7 个 JSON 规则列 + 辅助列：

```sql
ALTER TABLE style_cards ADD COLUMN rhythm_rules TEXT;
ALTER TABLE style_cards ADD COLUMN dialogue_rules TEXT;
ALTER TABLE style_cards ADD COLUMN description_rules TEXT;
ALTER TABLE style_cards ADD COLUMN emotion_rules TEXT;
ALTER TABLE style_cards ADD COLUMN structure_rules TEXT;
ALTER TABLE style_cards ADD COLUMN forbidden_rules TEXT;
ALTER TABLE style_cards ADD COLUMN preferred_rules TEXT;
ALTER TABLE style_cards ADD COLUMN style_description TEXT;
ALTER TABLE style_cards ADD COLUMN generation_template TEXT;
ALTER TABLE style_cards ADD COLUMN samples_json TEXT;
ALTER TABLE style_cards ADD COLUMN stats_json TEXT;
ALTER TABLE style_cards ADD COLUMN source_novel TEXT;
ALTER TABLE style_cards ADD COLUMN author_name TEXT;
ALTER TABLE style_cards ADD COLUMN tags TEXT;
ALTER TABLE style_cards ADD COLUMN status INTEGER DEFAULT 1;
```

新建 `style_samples` 表：

```sql
CREATE TABLE IF NOT EXISTS style_samples (
  id TEXT PRIMARY KEY,
  card_id TEXT NOT NULL REFERENCES style_cards(id),
  sample_type TEXT NOT NULL,
  content TEXT NOT NULL,
  annotation TEXT,
  sort_order INTEGER DEFAULT 0,
  created_at INTEGER NOT NULL
);
```

### StyleTextAnalyzer — 完整 30+ 项量化指标

完整移植 Java 版 `StyleTextAnalyzer`，分 5 类指标：

```typescript
// ai/style/style-text-analyzer.ts
class StyleTextAnalyzer {
  analyze(text: string): StyleMetrics {
    const sentences = this.splitSentences(text);
    const paragraphs = text.split(/\n\n+/).filter(p => p.trim());

    return {
      // === 基础指标（5 项）===
      avgSentenceLength: this.avg(sentences.map(s => s.length)),
      shortSentenceRatio: sentences.filter(s => s.length < 15).length / sentences.length,
      maxSentenceLength: Math.max(...sentences.map(s => s.length)),
      avgParagraphLength: this.avg(paragraphs.map(p => p.length)),
      shortParagraphRatio: paragraphs.filter(p => p.length < 60).length / paragraphs.length,

      // === 对话统计（3 项）===
      dialogueRatio: this.calcDialogueRatio(text),
      avgDialogueLength: this.avgDialogueLength(text),
      maxConsecutiveDialogue: this.maxConsecutiveDialogue(paragraphs),

      // === 标点习惯（4 项）===
      ellipsisCount: (text.match(/……/g) || []).length,
      emDashCount: (text.match(/——/g) || []).length,
      exclamationCount: (text.match(/！/g) || []).length,
      questionMarkCount: (text.match(/？/g) || []).length,

      // === 感官词密度（每千字，5 项）===
      visualDensity: this.sensoryDensity(text, SENSORY_WORDS.visual),
      auditoryDensity: this.sensoryDensity(text, SENSORY_WORDS.auditory),
      tactileDensity: this.sensoryDensity(text, SENSORY_WORDS.tactile),
      olfactoryDensity: this.sensoryDensity(text, SENSORY_WORDS.olfactory),
      gustatoryDensity: this.sensoryDensity(text, SENSORY_WORDS.gustatory),

      // === 反模式检测（7 项）===
      sentenceDiversityIndex: this.coefficientOfVariation(sentences.map(s => s.length)),
      directCutRatio: this.directCutRatio(paragraphs),
      transitionClicheRatio: this.transitionClicheRatio(paragraphs),
      actionEndingRatio: this.endingRatio(paragraphs, 'action'),
      dialogueEndingRatio: this.endingRatio(paragraphs, 'dialogue'),
      suspenseEndingRatio: this.endingRatio(paragraphs, 'suspense'),
      emptySceneRatio: this.emptySceneRatio(paragraphs),

      // === 风格指标（3 项）===
      metaphorDensity: this.metaphorDensity(text),
      alternationRate: this.alternationRate(sentences),
      positiveEmotionDensity: this.emotionDensity(text, EMOTION_WORDS.positive),
      negativeEmotionDensity: this.emotionDensity(text, EMOTION_WORDS.negative),

      // === 特色词 ===
      distinctiveWords: this.topDistinctiveWords(text, 20),
    };
  }
}
```

感官词库、情绪词库、转场陈词滥调列表定义在 `ai/style/word-lists.ts` 中。

### StyleCheckerService — 双模式检查

```typescript
// ai/style/style-checker.service.ts
class StyleCheckerService {
  constructor(private analyzer: StyleTextAnalyzer) {}

  // 零 LLM 成本的快速检查（9 项规则）
  quickCheck(text: string, card: StyleCard): StyleCheckResult {
    const metrics = this.analyzer.analyze(text);
    let score = 100;
    const issues: string[] = [];

    // 1. AI 陈词滥调
    const cliches = scanAllCliches(text);
    if (cliches.length > 0) { score -= cliches.length * 8; issues.push(`AI陈词滥调: ${cliches.join('、')}`); }

    // 2. 转场陈词滥调
    const transitionCliches = findTransitionCliches(text);
    if (transitionCliches.length > 0) { score -= transitionCliches.length * 5; }

    // 3. 转场陈词滥调比例
    if (metrics.transitionClicheRatio > 0.15) { score -= 10; issues.push('转场陈词滥调比例过高'); }

    // 4. 句式多样性
    if (metrics.sentenceDiversityIndex < 0.3) { score -= 12; issues.push('句式过于单一，AI味重'); }

    // 5. 连续对话
    if (metrics.maxConsecutiveDialogue > 8) { score -= 10; issues.push('连续对话过长'); }

    // 6. 短句比例
    if (metrics.shortSentenceRatio < 0.15) { score -= 10; issues.push('短句比例过低'); }

    // 7. 对话比例
    if (metrics.dialogueRatio < 0.05) { score -= 10; issues.push('对话比例过低'); }

    // 8. 短段落比例
    if (metrics.shortParagraphRatio < 0.1) { score -= 10; issues.push('段落过长'); }

    // 9. 空场景率
    if (metrics.emptySceneRatio > 0.5) { score -= 8; issues.push('空场景比例过高'); }

    return { passed: score >= 70, score: Math.max(0, score), issues, metrics };
  }

  // LLM 深度检查
  async deepCheck(text: string, card: StyleCard): Promise<StyleCheckResult> {
    const truncated = text.substring(0, 1500) + '\n...\n' + text.substring(text.length - 1500);
    const llm = this.getLLM('general');
    const response = await llm.invoke([
      new SystemMessage('你是文风检查专家。'),
      new HumanMessage(`请对比以下文本与目标文风，返回JSON：{score, issues, suggestions}\n\n【目标文风】\n${card.styleDescription}\n\n【生成文本】\n${truncated}`),
    ]);
    // 解析 LLM 返回的 JSON，fallback 到 quickCheck
  }

  // 修复建议
  async suggestFix(text: string, card: StyleCard): Promise<string> {
    const check = this.quickCheck(text, card);
    if (check.passed) return text;
    // 调用 LLM 根据 issues 修复文本
  }
}
```

### 写入时两级注入

修改 `WritingChain` 的 prompt 构建，分为 System 级（硬约束）和 User 级（软引导）：

```typescript
// System 级（硬约束）— 注入到 SystemMessage
buildSystemStyleRules(card: StyleCard): string {
  const parts: string[] = [];
  if (card.forbiddenRules?.words?.length) {
    parts.push(`【绝对禁止出现的词语】\n${card.forbiddenRules.words.join('、')}`);
  }
  if (card.forbiddenRules?.structures?.length) {
    parts.push(`【绝对禁止的句式】\n${card.forbiddenRules.structures.join('\n')}`);
  }
  if (card.dialogueRules) {
    parts.push(`【对话风格规则】\n${card.dialogueRules.style}`);
    if (card.dialogueRules.quirks?.length) parts.push(`对话特色: ${card.dialogueRules.quirks.join('、')}`);
  }
  return parts.join('\n\n');
}

// User 级（软引导）— 注入到 UserMessage，截断 3000 字
buildUserStyleSamples(card: StyleCard): string {
  const parts: string[] = [];
  parts.push(`【文风：${card.name}】`);
  parts.push('文风规则与系统指令冲突时，以系统指令为准');

  if (card.styleDescription) parts.push(`\n${card.styleDescription}`);
  if (card.generationTemplate) parts.push(`\n${card.generationTemplate}`);

  if (card.preferredRules?.words?.length) {
    parts.push(`\n【偏好用词】\n${card.preferredRules.words.join('、')}`);
  }
  if (card.preferredRules?.structures?.length) {
    parts.push(`\n【偏好句式】\n${card.preferredRules.structures.join('\n')}`);
  }

  // 参考片段（最多 5 个，按维度覆盖选择）
  const samples = this.selectSamples(card, 5);
  if (samples.length > 0) {
    parts.push('\n【参考片段】');
    for (const s of samples) {
      parts.push(`(${s.type}) "${s.text.substring(0, 300)}"\n  → ${s.annotation}`);
    }
  }

  return parts.join('\n').substring(0, 3000);
}
```

### 文风分析 API

```
POST /api/style-cards/analyze        — 上传原文，异步分析生成 7 类规则 + 指标
GET  /api/style-cards/:id/status     — 轮询分析状态（analyzing/ready/failed）
```

分析流程：
1. `StyleTextAnalyzer.analyze(text)` 纯算法计算 30+ 项指标（零 LLM 成本）
2. 将指标 JSON + 原文采样发送给 LLM，生成 7 类结构化规则 + styleDescription + samples
3. 存储到 `style_cards` 表的对应 JSON 列

---

## Part 6: 关联中等问题修复

随核心功能一起修复的中等问题：

| # | 问题 | 修复方式 | 归属 |
|---|------|----------|------|
| 12 | 缺少 StoryStateService | 随 Agent 图一起创建 | Part 3 |
| 13 | 缺少 EventGraphService | 随 Agent 图一起创建 | Part 3 |
| 14 | 缺少 CharacterStateService | 随 Agent 图一起创建 | Part 3 |
| 15 | WizardService 章节范围硬编码 | 从 VolumeOutlineResult 的 startChapter/endChapter 读取 | Part 1 |
| 16 | HookAnalysisService 纯规则 | 保留规则版，添加可选 LLM 深度分析模式 | Part 6 |
| 17 | safeJsonParse 重复定义 | 提取到 `utils/json.ts` | Part 1 |
| 18 | getLLM 重复实现 | 提取到 `ai/llm-factory.ts` 的 `getLLMByUsage(db, usageType)` | Part 1 |
| 20 | llm-factory 缺少 maxTokens | 添加 maxTokens 参数 | Part 1 (Bug 7) |
| 21 | ValidationService 失败默认 passed: true | 改为 `passed: false` | Part 1 (Bug 6) |
| 22 | QualityScorer.checkClueLanding 空实现 | 实现真正的伏笔落地检查 | Part 3 |
| 24 | chapter.outline 序列化不一致 | 统一为 JSON.stringify/parse | Part 6 |
| 25 | 缺少 configName 字段 | 在 llmConfigs 表添加 config_name 列 | Part 6 |
| 26 | CreateNovelInput 缺 creationMode | 添加可选 creationMode 字段 | Part 6 |
| 28 | characterGrowth 缺 description | 在表和类型中添加 description 列 | Part 6 |

不在本次范围：
- #10 MCP 集成（独立功能，后续单独做）
- #11 认证系统（用户明确不需要）
- #19 moonshot/wenxin provider（低优先级）
- #23 Web 客户端 any 类型（前端重构范围）
- #27 Web 前端向导页面（前端重构范围）

---

## 文件清单

### 新建文件

```
packages/core/src/utils/json.ts                       — safeJsonParse 公共函数
packages/core/src/utils/text.ts                       — countWords 公共函数
packages/core/src/ai/rag/embedding.service.ts          — EmbeddingService
packages/core/src/ai/rag/vector-store.ts               — VectorStore
packages/core/src/ai/rag/rag.service.ts                — RagService（混合检索 + 记忆压缩 + 上下文组装）
packages/core/src/ai/rag/bm25-search.ts                — minisearch 中文 BM25
packages/core/src/ai/style/style-text-analyzer.ts      — StyleTextAnalyzer（30+ 项指标）
packages/core/src/ai/style/style-checker.service.ts    — StyleCheckerService（quickCheck + deepCheck）
packages/core/src/ai/style/word-lists.ts               — 感官词库、情绪词库、转场词库
packages/core/src/types/graph.ts                       — 图执行器类型定义
packages/core/src/ai/graph/graph-runner.ts             — GraphRunner（图执行器，支持条件跳转）
packages/core/src/ai/nodes/arc-check.node.ts
packages/core/src/ai/nodes/outline.node.ts
packages/core/src/ai/nodes/clue-planning.node.ts
packages/core/src/ai/nodes/character-planning.node.ts
packages/core/src/ai/nodes/world-validator.node.ts
packages/core/src/ai/nodes/idea-selection.node.ts
packages/core/src/ai/nodes/writing.node.ts
packages/core/src/ai/nodes/self-review.node.ts
packages/core/src/ai/nodes/validation.node.ts
packages/core/src/ai/nodes/quality-scoring.node.ts
packages/core/src/ai/nodes/audit-router.node.ts
packages/core/src/ai/nodes/summary.node.ts
packages/core/src/ai/nodes/post-process.node.ts
packages/core/src/services/story-state.service.ts
packages/core/src/services/character-state.service.ts
packages/core/src/services/event-graph.service.ts
packages/web/src/server/routes/streaming.ts
```

### 修改文件

```
packages/core/src/db/schema.ts              — 添加 vectors, style_samples 表；扩展 style_cards；添加 config_name, description 列
packages/core/src/db/migrate.ts             — 添加新表迁移；统一连接
packages/core/src/db/index.ts               — 统一迁移调用
packages/core/src/services/chapter.service.ts     — 添加 upsert；修复字数统计；添加 saveVersion
packages/core/src/services/novel.service.ts       — 提取 safeJsonParse；添加 creationMode
packages/core/src/services/wizard.service.ts      — 模块级状态；修复章节范围
packages/core/src/services/style-card.service.ts  — 扩展分析和检查功能
packages/core/src/ai/chapter-generation.service.ts — 修复字数统计；添加 upsert 调用
packages/core/src/ai/chains/writing.chain.ts      — 修复字数统计；添加 AbortSignal 支持；文风注入
packages/core/src/ai/validation.service.ts        — 失败时 passed: false
packages/core/src/ai/quality-scorer.ts            — 实现 checkClueLanding
packages/core/src/ai/llm-factory.ts               — 添加 getLLMByUsage；添加 maxTokens
packages/core/src/ai/context-builder.ts           — 调用 RagService.assembleContext()
packages/core/src/ai/prompts/style.ts             — 文风注入 prompt
packages/core/src/types/style-card.ts             — 扩展类型
packages/core/src/types/novel.ts                  — 添加 creationMode
packages/core/src/types/chapter.ts                — 修复 outline 类型；添加 description
packages/core/src/index.ts                         — 添加新导出
packages/web/src/server/index.ts                   — 注册流式路由
packages/cli/src/commands/novel.ts                 — 修复 export 参数
```

---

## 执行顺序

1. **Bug 修复 + 基础设施**（无依赖，可并行）
   - Bug 1-7 修复
   - `utils/json.ts`, `utils/text.ts`, `getLLMByUsage`, `maxTokens`
   - Schema 变更（vectors, style_samples, 扩展 style_cards）

2. **SSE 流式写作**（依赖 WritingChain，已存在）
   - 流式路由 + 取消 + 版本快照
   - 前端 useSSE Hook

3. **文风卡片深度系统**（依赖 schema 变更）
   - StyleTextAnalyzer（30+ 项指标）
   - StyleCheckerService（quickCheck + deepCheck）
   - 两级注入
   - 文风分析 API

4. **RAG 向量检索**（依赖 schema 变更，与文风并行）
   - EmbeddingService + VectorStore
   - RagService（混合检索 + 记忆压缩 + 上下文组装）
   - 集成到 ContextBuilder

5. **Agent 图执行器**（依赖 RAG + 文风 + 基础设施）
   - 14 个节点实现
   - GraphRunner（条件跳转 + 循环）
   - AuditRouterNode
   - GraphChapterGenerationService

6. **剩余中等问题**（穿插在各阶段）
   - #16 HookAnalysisService LLM 模式
   - #24 outline 序列化统一
   - #25 configName, #26 creationMode, #28 description
