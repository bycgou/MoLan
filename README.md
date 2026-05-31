<p align="center">
  <h1 align="center">墨澜 MoLan</h1>
  <p align="center">
    <strong>AI 驱动的百万字长篇小说创作引擎</strong>
  </p>
  <p align="center">
    <em>万线归一织，墨落自成章</em>
  </p>
</p>

---

## 墨澜是什么

墨澜是一个面向**百万字长篇网文**的 AI 创作系统。它不是简单的"prompt → LLM → 输出"单链路工具，而是一套**多 Agent 图编排引擎 + 六层记忆架构 + 五重校验体系**的完整创作流水线，让 AI 在写第 300 章时仍然精确记得第 3 章埋的伏笔、角色的性格底线、以及上一章结尾的悬念。

## 快速启动

### 前置条件
- Docker & Docker Compose
- Java 17+（或使用 Maven wrapper，无需全局安装）
- Node.js 18+（前端开发）

### 1. 启动基础设施
```bash
cp .env.example .env    # 编辑填入密码和 JWT_SECRET
docker-compose up -d    # 启动 MySQL + MongoDB + Redis
```

### 2. 启动后端
```bash
cd backend
./mvnw spring-boot:run  # Linux/macOS
# 或
mvnw.cmd spring-boot:run  # Windows
```

### 3. 启动前端
```bash
cd frontend-v2
npm install
npm run dev
```

访问 http://localhost:5173

## 核心亮点

### 多 Agent 图编排引擎 (GraphRunner)

自研的 DAG 执行引擎，不是简单的链式调用：

- **条件路由** — 根据状态动态决定下一步走哪
- **扇出并行** — 线索规划 + 人物规划同时执行，再合并结果
- **检查点拦截** — AuthorAgent 在大纲、写作、审校三个关键节点拦截
- **重试容错** — 每个节点可配 SKIP / RETRY / FAIL 策略，节点级重试计数防死循环
- **多模式执行** — FULL / QUICK 等模式，同一图定义不同路径

```
arc_check → outline → clue_planning ──┐
                                  ──→ writing → self_review → validation
           character_planning ─────┘         → quality_check → audit_router → summary → post_process
```

### AuthorAgent — "人类作者"在环

在关键决策点模拟人类作者的判断力：

| 决策 | 行为 |
|------|------|
| **APPROVE** | 符合意图，继续 |
| **REVISE** | 方向对但需微调，注入创作笔记到后续流程 |
| **REJECT** | 方向错误，清空状态重做 |
| **REDIRECT** | 跳转到任意节点重新执行 |

预筛优化：结构完整就跳过 LLM 调用，质量分够高就自动放行，**省 token 又不失把控**。

### 五重并行校验 + 加权评分

| 维度 | 检查内容 |
|------|----------|
| **OOC** | 角色是否脱离人设（语言风格 / 行为动机 / 知识水平 / 成长适应，4 项打分） |
| **逻辑** | 因果链、一致性、冲突检测 |
| **伏笔** | 埋设数、回收数、遗漏、不合理回收 |
| **设定** | 世界规则、世界时钟、设定冲突 |
| **节奏** | 预期节奏 vs 实际节奏 |

加权评分体系：每个维度可配权重和严重度（FATAL / WARNING / SKIP），FATAL 维度不过直接 REPLAN，加权分不够则 REWRITE。

### 反 AI 味三道防线

**第一道：生成时约束** — WritingAgent System Prompt 注入反 AI 味铁律

**第二道：生成后检测** — `AntiAICliches` + `StyleCheckerService` 量化检测

- 18 个高频 AI 套话黑名单（不禁、似乎、仿佛、眼神中闪过……）
- 过渡句万能胶黑名单（时间飞逝、转眼间、与此同时……）
- 升级流水账黑名单（盘膝而坐、运转功法、灵力奔涌……）
- 句式多样性指数、对话结构检测、场景感官细节检测

**第三道：自动修正** — 不通过时自动修复或局部重写，最多 2 次，超过则标记人工审阅

### 六层记忆架构

```
┌─────────────────────────────────────────────────────┐
│  第一层：故事快照 (StoryState)     ← 核心枢纽，全量注入 │
│  第二层：伏笔账本 (ClueBook)       ← SQL 精确查询      │
│  第三层：角色档案 (CharacterState)  ← 按出场角色注入    │
│  第四层：滑动窗口                  ← 近章大纲+摘要+钩子  │
│  第五层：事件图谱 + 规则驱动检索    ← 自动触发历史事件   │
│  第六层：RAG 向量检索              ← Agent @Tool 按需   │
└─────────────────────────────────────────────────────┘
```

设计哲学：

- **精确查询优先，语义检索兜底** — 结构化数据用 SQL / MongoDB 精确查，只有"回忆早期细节"才用 RAG
- **写时更新，读时组装** — 每章生成后自动更新所有状态
- **渐进式记忆** — 近期精确（滑动窗口）、远期模糊（弧段摘要）、超远期压缩（每 20 章自动背景压缩）
- **Push-Pull 二分** — 前四层 Push（自动注入），后两层 Pull（Agent 按需调用）

### 十二种体裁深度模板

| 体裁 | 代码 | 特色 |
|------|------|------|
| 玄幻 | xuanhuan | 修炼体系、爽点设计、情节模式 |
| 仙侠 | xianxia | 修仙体系、仙界设定、法宝丹药 |
| 科幻 | kehuan | 科技设定、世界观构建、太空歌剧 |
| 游戏 | youxi | 游戏系统设计、电竞描写、副本设计 |
| 末世 | moshi | 末世生存、丧尸设定、基地建设 |
| 都市 | dushi | 职场描写、商业博弈、都市异能 |
| 狗血言情 | dog-blood-romance | 虐点、甜点、套路、节奏、情感张力 |
| 古言 | period-drama | 宫斗、古风对话、历史设定 |
| 悬疑推理 | rules-mystery | 诡计设计、线索设计、嫌疑人管理 |
| 知乎短篇 | zhihu-short | 钩子技巧、情绪峰值、结尾模式 |
| 现实主义 | realistic | 社会议题、对话真实感、现实锚定 |
| 历史 | lishi | 争霸流、权谋流、种田流 |

每个体裁包含 `index.md`（元数据 + 节奏配置）+ 5 个写作指南 Markdown 文件，支持热加载和智能截断。

### 文风卡系统

- 分析参考文本的节奏、对话、描写、禁用词、偏好词
- 提取 11 类原文样本（悬疑 / 对话 / 战斗 / 心理 / 描写 / 场景过渡 / 沉默 / 细节 / 内心挣扎 / 幽默 / 反高潮）
- 生成时注入文风规则，生成后量化检测偏离度

### 卷级编排 (Arc Orchestration)

- 收集未回收伏笔 + 人物状态
- LLM 规划整卷的章节大纲、伏笔节奏、人物动线
- 幂等设计（已有编排不重复生成）

### MCP 协议集成

同时支持 MCP Client 和 MCP Server：

- **Client**：接入 memory、filesystem、brave-search 等外部工具
- **Server**：通过 SSE 暴露小说操作能力给外部 Agent

---

## 项目结构

```
MoLan/
├── backend/                    # Spring Boot 后端（Java 17）
│   └── src/main/java/com/novel/system/
│       ├── ai/
│       │   ├── agent/          # LangChain4j Agent（Outline / Writing / Summary）
│       │   ├── chain/          # 章节生成链
│       │   ├── graph/          # GraphRunner 图编排引擎
│       │   ├── memory/         # 记忆服务（角色图谱、风格感知记忆）
│       │   ├── rag/            # RAG 检索（向量 + BM25 + RRF 融合）
│       │   └── tool/           # Agent @Tool（角色 / 伏笔 / 小说查询）
│       ├── config/             # 配置（安全 / JWT / LangChain / MCP / Redis 向量）
│       ├── controller/         # REST API
│       ├── entity/             # 数据实体（MySQL + MongoDB）
│       ├── mcp/                # MCP Client + Server
│       ├── service/            # 业务服务
│       │   ├── chapter/        # 章节生成 + 上下文构建 + Token 预算
│       │   ├── character/      # 角色状态管理
│       │   ├── clue/           # 伏笔追踪
│       │   ├── event/          # 事件图谱 + 规则驱动检索
│       │   ├── genre/          # 体裁模板（12 种体裁 Markdown 指南）
│       │   ├── llm/            # LLM 配置管理
│       │   ├── novel/          # 小说管理 + 节奏分析
│       │   ├── orchestration/  # 卷级编排
│       │   ├── story/          # 故事状态（核心枢纽）
│       │   ├── style/          # 文风卡 + AI 味检测
│       │   └── wizard/         # 向导式创作流程
│       └── resources/
│           └── genres/         # 12 种体裁 Markdown 模板
│
├── frontend/                   # Vue 3 前端（Element Plus）
│   └── src/
│       ├── views/              # 页面（Dashboard / 小说 / 章节 / 角色 / 伏笔 / 文风卡）
│       ├── components/         # 组件（章节列表 / 角色面板 / 节奏面板 / 生成对话框）
│       ├── stores/             # Pinia 状态管理
│       └── api/                # API 层
│
├── frontend-v2/                # Next.js 16 前端（shadcn/ui + Tailwind CSS）
│   ├── app/                    # App Router 页面
│   ├── components/             # 组件（StreamViewer / WizardSteps / RhythmPanel）
│   ├── hooks/                  # useAuth / useSSE
│   └── lib/                    # API 层 + 工具函数
│
└── moyun-ts/                   # TypeScript 重写（monorepo）
    └── packages/
        ├── core/               # 核心引擎（LangChain.js + GraphRunner + RAG + 风格检查）
        │   └── src/
        │       ├── ai/         # 图节点 + Chain + Prompt + RAG + 风格检查
        │       ├── db/         # Drizzle ORM + SQLite
        │       ├── services/   # 业务服务
        │       └── types/      # 类型定义
        ├── cli/                # CLI 工具（commander）
        └── web/                # Web 前端（React + Vite）
```

---

## 技术栈

| 层 | 技术 |
|----|------|
| **后端** | Java 17, Spring Boot 3.2, LangChain4j, MyBatis-Plus, Spring Security, JWT |
| **前端 (v1)** | Vue 3, TypeScript, Element Plus, Pinia, Vite |
| **前端 (v2)** | Next.js 16, React 19, shadcn/ui, Tailwind CSS, Zustand, SWR |
| **TypeScript 引擎** | LangChain.js, Drizzle ORM, better-sqlite3, MiniSearch |
| **数据库** | MySQL 8, MongoDB, Redis Stack (向量索引) |
| **AI** | OpenAI 兼容 API, LangChain4j / LangChain.js, MCP SDK |
| **部署** | Docker, Nginx |

---

## 快速开始

### 环境要求

- Java 17+
- Node.js 20+
- MySQL 8.0+
- MongoDB 7.0+
- Redis Stack (含向量搜索模块)
- Maven 3.9+

### 1. 启动基础设施

```bash
# MySQL
docker run -d --name mysql \
  -e MYSQL_ROOT_PASSWORD=123456 \
  -e MYSQL_DATABASE=moyun_novel_system \
  -p 3306:3306 \
  mysql:8

# MongoDB
docker run -d --name mongodb \
  -p 27017:27017 \
  mongo:7

# Redis Stack (含向量搜索)
docker run -d --name redis-stack \
  -p 6379:6379 \
  -p 8001:8001 \
  redis/redis-stack-server:latest
```

### 2. 启动后端

```bash
cd backend
mvn clean package -DskipTests
java -jar target/ai-novel-system-1.0.0.jar
```

后端启动在 `http://localhost:8080/api`

### 3. 启动前端

**Vue 3 版本 (frontend/)**

```bash
cd frontend
npm install
npm run dev
```

**Next.js 版本 (frontend-v2/)**

```bash
cd frontend-v2
pnpm install
pnpm dev
```

访问 `http://localhost:3000`

### 4. 配置 LLM

启动后访问前端，进入 **LLM 配置** 页面，添加你的模型配置：

- **API 地址**：支持任何 OpenAI 兼容 API（OpenAI / DeepSeek / 通义千问 / 本地 Ollama 等）
- **模型名称**：如 `gpt-4o`、`deepseek-chat`、`qwen-plus`
- **API Key**：你的密钥
- **用途**：可配置不同模型用于不同任务（写作 / 审校 / 摘要）

### 5. 开始创作

1. 点击 **创建小说** → 使用向导流程
2. 填写小说名称、体裁、世界观、核心设定
3. 系统自动生成卷纲 → 确认/修改
4. 逐卷生成章纲 → 确认/修改
5. 逐章生成正文（支持 SSE 流式输出）
6. 查看角色面板、伏笔面板、节奏分析

---

## 章节生成流程

```
用户点击"生成章节"
       │
       ▼
  ┌──────────────────────────────────────────────────────────┐
  │                  GraphRunner 图编排                        │
  │                                                          │
  │  arc_check ──→ outline ──→ clue_planning ──┐             │
  │                character_planning ──────────┘             │
  │                     │                                    │
  │                     ▼                                    │
  │              idea_selection                               │
  │                     │                                    │
  │                     ▼                                    │
  │                writing ──→ self_review                   │
  │                     │                                    │
  │                     ▼                                    │
  │    validation(5维并行) ──→ quality_scoring                │
  │                     │                                    │
  │                     ▼                                    │
  │              audit_router ──→ PASS / REWRITE / REPLAN    │
  │                     │                                    │
  │                     ▼                                    │
  │              summary ──→ post_process                    │
  │                                                          │
  │  AuthorAgent 在 outline / writing / audit_router 拦截     │
  └──────────────────────────────────────────────────────────┘
       │
       ▼
  ┌──────────────────────────────────────────────────────────┐
  │                  后处理流水线（异步）                       │
  │                                                          │
  │  1. 提取人物 → CharacterState + NovelGraph               │
  │  2. 提取伏笔 → ClueBook                                  │
  │  3. 提取时间线 → StoryState                               │
  │  4. 提取事件 → EventGraph                                │
  │  5. 生成摘要 + 向量嵌入 → ChapterSummary + Redis          │
  │  6. 更新故事快照（LLM 提取 + 程序化兜底）                  │
  └──────────────────────────────────────────────────────────┘
```

---

## 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `MYSQL_HOST` | localhost | MySQL 主机 |
| `MYSQL_PORT` | 3306 | MySQL 端口 |
| `MYSQL_DATABASE` | moyun_novel_system | 数据库名 |
| `MYSQL_USERNAME` | root | MySQL 用户名 |
| `MYSQL_PASSWORD` | 123456 | MySQL 密码 |
| `MONGODB_HOST` | localhost | MongoDB 主机 |
| `MONGODB_PORT` | 27017 | MongoDB 端口 |
| `MONGODB_DATABASE` | moyun_novel_system | MongoDB 数据库名 |
| `REDIS_HOST` | localhost | Redis 主机 |
| `REDIS_PORT` | 6379 | Redis 端口 |
| `REDIS_PASSWORD` | (空) | Redis 密码 |
| `JWT_SECRET` | changeme_... | JWT 签名密钥（**生产环境必须修改**） |
| `MCP_CLIENT_ENABLED` | false | 启用 MCP Client |
| `MCP_SERVER_ENABLED` | false | 启用 MCP Server |
| `EMBEDDING_MAX_INPUT_CHARS` | 2000 | Embedding 输入截断长度 |

---

## API 概览

| 模块 | 端点 | 说明 |
|------|------|------|
| 认证 | `POST /api/auth/login` | 登录 |
| | `POST /api/auth/register` | 注册 |
| 小说 | `POST /api/novel` | 创建小说 |
| | `GET /api/novel/list` | 小说列表 |
| | `POST /api/novel/{id}/outline` | 生成章纲 |
| | `POST /api/novel/{id}/chapter` | 生成章节 |
| | `GET /api/novel/{id}/export` | 导出 TXT |
| 章节 | `GET /api/chapter/stream-write` | SSE 流式生成 |
| | `GET /api/novel/{id}/chapter/{num}/versions` | 章节版本历史 |
| 角色 | `GET /api/novel/{id}/character/list` | 角色列表 |
| 伏笔 | `GET /api/novel/{id}/clue/unrecycled` | 未回收伏笔 |
| 节奏 | `GET /api/novel/{id}/rhythm/status` | 节奏状态 |
| 文风 | `GET /api/style-card/list` | 文风卡列表 |
| | `POST /api/style-card/analyze` | 分析文本风格 |
| 体裁 | `GET /api/genre/list` | 体裁列表 |
| | `GET /api/genre/{code}` | 体裁详情 |
| | `GET /api/genre/{code}/guides` | 体裁指南文件列表 |
| | `GET /api/genre/{code}/guides/{filename}` | 单个指南文件 |
| | `POST /api/genre/reload` | 热加载体裁数据 |
| LLM | `GET /api/llm-config/list` | 模型配置列表 |
| 向导 | `POST /api/wizard/generate-volume-outlines` | 生成卷纲 |
| MCP | `GET /api/mcp/sse` | MCP Server SSE 端点 |

---

## TypeScript 版本 (moyun-ts)

项目正在用 TypeScript 重写核心引擎，目标是提供更轻量的部署方案（无需 Java / MySQL / MongoDB）：

```bash
cd moyun-ts
pnpm install
pnpm build
pnpm cli dev
pnpm core test
```

TypeScript 版本使用 SQLite (better-sqlite3) + MiniSearch (BM25) 替代 MySQL + MongoDB + Redis Stack，适合本地使用和快速体验。

---

## License

MIT License

---

## 致谢

- [LangChain4j](https://github.com/langchain4j/langchain4j) — Java AI Agent 框架
- [LangChain.js](https://github.com/langchain-ai/langchainjs) — TypeScript AI 框架
- [Spring Boot](https://spring.io/projects/spring-boot) — 后端框架
- [Next.js](https://nextjs.org/) — 前端框架
- [shadcn/ui](https://ui.shadcn.com/) — UI 组件库
- [MCP SDK](https://modelcontextprotocol.io/) — Model Context Protocol
