# Python AI Service 设计文档

> 将小说生成系统的 AI/Agent 部分从 Java LangChain4j 迁移到 Python LangGraph 微服务。

## 1. 背景与目标

### 当前架构

Java Spring Boot 后端，使用 LangChain4j 实现 AI Agent：

- **Agent 层**：`WritingAgent`、`OutlineAgent`、`VolumeOutlineAgent`（LangChain4j AI Services）
- **Graph 引擎**：自定义 `GraphRunner` + `GraphDefinition` + 各种 Node
- **Chain 编排**：`ChapterGenerationChain` 串联 Agent 调用
- **工具系统**：`CharacterTool`、`ClueTool`、`NovelTool`、`RagTool`
- **校验链**：五重校验（OOC/逻辑/伏笔/设定/节奏）
- **记忆系统**：向量检索、角色图谱、事实锁

### 迁移动机

Python AI 生态更丰富（LangChain、LlamaIndex、LangGraph 等），社区活跃，LLM provider SDK 支持更及时。

### 目标

- AI 服务完全独立成 Python 微服务
- Java 通过 HTTP 调用 Python AI 服务
- 使用 LangGraph 复刻当前图引擎架构
- 保持 SSE 流式输出能力

## 2. 整体架构

```
                    ┌─────────────┐
                    │   Nginx     │
                    └──────┬──────┘
                           │
              ┌────────────┼────────────┐
              │            │            │
        ┌─────▼─────┐ ┌───▼───┐ ┌─────▼─────┐
        │  Frontend  │ │  Java │ │  Python   │
        │  (Vue)     │ │  :8080│ │  :8000    │
        └───────────┘ └───────┘ └───────────┘
                           │            │
                           └────────────┘
                          Java API 调用
```

### 职责划分

| 层 | Java (保留) | Python (新增) |
|---|---|---|
| Controller | 小说/人物/章节 CRUD | AI 生成端点 |
| Service | 数据库操作、后处理、用户认证 | Agent 编排、LLM 调用 |
| AI | ~~移除 ai 包~~ | LangGraph 图引擎 |
| 数据 | Entity/Mapper/Repository | 通过 Java API 获取 |

## 3. Python 服务目录结构

```
moyun-ai/
├── app/
│   ├── main.py                  # FastAPI 入口
│   ├── config.py                # 配置管理
│   ├── graph/
│   │   ├── state.py             # NovelAgentState
│   │   ├── definition.py        # 图定义
│   │   ├── runner.py            # LangGraph 编译 + 执行
│   │   └── nodes/
│   │       ├── arc_check.py
│   │       ├── outline.py
│   │       ├── writing.py
│   │       ├── validation.py
│   │       ├── self_review.py
│   │       ├── quality.py
│   │       ├── summary.py
│   │       └── post_process.py
│   ├── agents/
│   │   ├── base.py              # 基础 Agent 类
│   │   ├── outline_agent.py
│   │   ├── writing_agent.py
│   │   └── summary_agent.py
│   ├── tools/
│   │   ├── java_api.py          # Java API 客户端
│   │   ├── character_tool.py
│   │   ├── clue_tool.py
│   │   └── novel_tool.py
│   ├── api/
│   │   ├── generate.py          # /generate SSE 端点
│   │   └── health.py
│   └── prompts/
│       ├── outline.py
│       ├── writing.py
│       └── summary.py
├── tests/
├── pyproject.toml
├── Dockerfile
└── README.md
```

### 设计原则

- `state.py` 完全对齐 Java `NovelAgentState` 字段
- `tools/java_api.py` 封装所有对 Java 后端的 HTTP 调用
- `prompts/` 独立目录，方便调优提示词
- Agent 和 Node 分离：Agent 负责 LLM 调用，Node 负责图节点逻辑

## 4. 状态管理

### NovelAgentState

```python
@dataclass
class NovelAgentState:
    # 请求输入
    novel_id: str = ""
    chapter_num: int = 0
    user_intent: str = ""
    style_card_id: str = ""
    llm_config_id: str = ""
    mode: Mode = Mode.FAST

    # 上下文
    outline_context: str = ""
    writing_context: str = ""

    # Phase 输出
    chapter_plan: Optional[ChapterPlan] = None
    selected_idea: str = ""
    draft_content: Optional[DraftContent] = None
    audit_result: Optional[dict] = None
    final_result: Optional[dict] = None

    # 流程控制
    iteration_count: int = 0
    current_phase: str = ""
    errors: list[dict] = field(default_factory=list)
    execution_trace: list[dict] = field(default_factory=list)
```

### 三种执行模式

| 模式 | 节点 | 说明 |
|------|------|------|
| FAST | arc_check → outline → writing → summary → post_process | 跳过所有校验 |
| STREAMING | + validation（轻量） | 只做连续性+OOC |
| FULL | + validation + self_review + quality_check | 完整流程 |

## 5. LangGraph 图定义

```python
def build_graph(mode: Mode = Mode.FAST) -> StateGraph:
    graph = StateGraph(NovelAgentState)

    graph.add_node("arc_check", arc_check_node)
    graph.add_node("outline", outline_node)
    graph.add_node("writing", writing_node)
    graph.add_node("validation", validation_node)
    graph.add_node("self_review", self_review_node)
    graph.add_node("quality_check", quality_scoring_node)
    graph.add_node("summary", summary_node)
    graph.add_node("post_process", post_process_node)

    graph.set_entry_point("arc_check")
    graph.add_edge("arc_check", "outline")
    graph.add_edge("outline", "writing")

    if mode == Mode.FULL:
        graph.add_edge("writing", "validation")
        graph.add_conditional_edges("validation", route_after_validation)
        graph.add_edge("self_review", "quality_check")
        graph.add_conditional_edges("quality_check", route_after_quality)
    elif mode == Mode.STREAMING:
        graph.add_edge("writing", "validation")  # 轻量校验：连续性+OOC
        graph.add_conditional_edges("validation", route_after_validation)
    else:  # FAST
        graph.add_edge("writing", "summary")

    graph.add_edge("summary", "post_process")
    graph.add_edge("post_process", END)

    return graph
```

### 条件路由

```python
def route_after_validation(state: NovelAgentState) -> str:
    if state.audit_result and state.audit_result.get("all_passed"):
        return "summary"
    if state.iteration_count >= 2:
        return "summary"
    return "self_review"
```

## 6. 通信协议

### Java → Python

```
POST /api/generate
Content-Type: application/json

{
  "novel_id": "xxx",
  "chapter_num": 1,
  "outline": "章纲内容...",
  "style_card_id": "xxx",
  "llm_config_id": "xxx",
  "mode": "FAST",
  "context_prompt": "上下文..."
}
```

### Python → 前端（SSE）

```
event: token
data: {"content": "逐字输出..."}

event: checkpoint
data: {"node": "writing", "phase": "WRITING"}

event: complete
data: {"content": "完整内容", "word_count": 3000, "summary": "..."}

event: error
data: {"message": "错误信息"}
```

### SSE 转发策略

前端直连 Python 服务（`http://python:8000/api/generate`），不经过 Java 透传。原因：
- 减少一跳延迟，流式体验更好
- Java 不需要维护 SSE 连接状态
- Nginx 统一代理，前端无需感知后端拆分

## 7. 工具系统

### Java 侧新增 API

| 端点 | 说明 | 复用 Service |
|------|------|-------------|
| `GET /api/ai/characters` | 人物状态 | CharacterStateService |
| `GET /api/ai/clues` | 未回收伏笔 | ClueBookService |
| `GET /api/ai/novel` | 小说信息 | NovelBaseMapper |
| `GET /api/ai/arc` | 卷信息 | NovelArcMapper |
| `GET /api/ai/world-clock` | 世界时钟 | WorldClockMapper |
| `GET /api/ai/history` | 历史检索 | RagService |
| `POST /api/ai/save-chapter` | 保存章节 | ChapterSaveService |

### Python 工具注册

```python
@tool
async def recall_history(novel_id: str, query: str) -> str:
    """回忆历史事件。当章纲提到久未出场的人物、有历史的地点、需要回收的伏笔时调用。"""
    return await java_client.recall_history(novel_id, query)

@tool
async def get_character_info(novel_id: str, character_name: str) -> str:
    """查询人物设定。当需要确认人物的职务、身份、性格时调用。"""
    return await java_client.get_character_info(novel_id, character_name)
```

## 8. LLM 配置

Python 服务启动时从 Java 后端拉取 LLM 配置，或通过环境变量配置。

```python
def get_llm(config_id: str = None):
    config = settings.llm_configs.get(config_id)
    if "anthropic" in config.base_url:
        return ChatAnthropic(model=config.model_name, ...)
    return ChatOpenAI(model=config.model_name, streaming=True, ...)
```

## 9. 迁移计划

| 阶段 | 内容 | 验证标准 |
|------|------|----------|
| Phase 1 | Python 空壳服务 + FastAPI + 健康检查 | `curl /health` 返回 200 |
| Phase 2 | LangGraph 空壳图（节点返回 mock 数据） | Java 调 Python 跑通全链路 |
| Phase 3 | 实现 WritingNode + WritingAgent（真实 LLM） | 流式生成章节内容 |
| Phase 4 | 实现 OutlineNode + SummaryNode | 章纲+摘要生成 |
| Phase 5 | 实现 Validation + SelfReview + QualityCheck | 完整 FULL 模式 |
| Phase 6 | 实现工具系统（recall_history 等） | Agent 能调用工具 |
| Phase 7 | Java 侧移除 ai 包 | 全量回归测试 |

## 10. Java 侧改造清单

### 新增

- `AiDataController.java` — 暴露 `/api/ai/*` 端点给 Python 调用

### 改造

- `StreamingChapterController.java` — SSE 端点改为调用 Python 或直连
- `ChapterGenerationServiceImpl.java` — 生成逻辑改为调用 Python API

### 保留不变

- 所有 Entity / Mapper / Repository
- 用户认证、权限
- 小说/人物/章节 CRUD
- 后处理服务（FactLock、EmotionLedger、CompletedBeats）

## 11. 部署

```yaml
# docker-compose.yml
services:
  backend:
    build: ./backend
    ports: ["8080:8080"]

  ai-service:
    build: ./moyun-ai
    ports: ["8000:8000"]
    environment:
      JAVA_API_URL: http://backend:8080

  frontend:
    build: ./frontend
    ports: ["3000:3000"]
```
