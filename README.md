# 墨云 MoYun

> AI 驱动的百万字长篇小说创作引擎

墨云是一个面向长篇网络小说创作的 AI 写作系统：以「写作图 + 多 Agent」编排章节生成流程，结合长短期记忆、向量检索与多维质量校验，帮助作者持续产出设定自洽、风格统一的长篇小说。

## 功能特性

- **三档写作模式**：`FAST` 快速生成、`STREAMING` 流式生成（轻量校验）、`FULL` 多 Agent 完整流程，按场景自由切换
- **写作图编排**：灵感筛选 → 大纲 → 角色/线索规划 → 写作 → 反 AI 味门禁 → 校验 → 审核路由 → 润色 → 摘要，节点化执行、全程可追踪
- **AuthorAgent 人工干预**：在大纲、写作、审核等检查点暂停，接收作者指令后继续
- **记忆与一致性**：事实锁定、情感账本、已完成桥段、收敛检测、回声召回、角色状态与关系图谱、文风记忆
- **RAG 长上下文**：章节摘要向量化存入 Milvus，写作时按语义召回前文
- **七维加权校验**：OOC、逻辑、设定、线索、节奏、质量、线索回收，加权评分不达标自动重写
- **创作辅助**：文风卡片、套路检测、钩子分析、节奏分析、角色关系可视化
- **向导式建书**：内置 11 种题材模板（玄幻、仙侠、都市、科幻、悬疑、现实等）
- **多模型接入**：任意 OpenAI 兼容接口（DeepSeek / OpenAI / 智谱 GLM / Ollama 本地模型等）
- **可选 MCP 集成**：MCP 客户端（memory、filesystem、brave-search 等）与服务端工具双支持
- **可观测性**：Spring Actuator + Prometheus 指标

## 技术栈

| 层次 | 技术 |
| --- | --- |
| 后端 | Java 17 · Spring Boot 3.2 · Spring Security + JWT · MyBatis-Plus |
| AI | LangChain4j 1.14 · 自研写作图节点引擎 · Milvus 2.5 向量库 · MCP Java SDK |
| 存储 | MySQL 8（业务数据）· MongoDB 7（创作记忆与状态）· Redis 7（缓存）· Milvus（向量检索） |
| 前端 | Vue 3 · TypeScript · Vite 5 · Element Plus · Pinia · Vue Router · ECharts · vis-network · wangEditor |
| 运维 | Docker Compose · Spring Actuator · Prometheus |

## 目录结构

```
MoYun/
├── backend/                          # Spring Boot 后端
│   ├── src/main/java/com/novel/system/
│   │   ├── ai/                       # 写作图、Agent、RAG、记忆、Prompt、MCP 工具
│   │   │   ├── graph/                # 写作图定义与节点执行引擎
│   │   │   ├── agent/                # 大纲 / 分卷 / 写作 / 电弧编排 Agent
│   │   │   ├── memory/               # 角色图谱、风格记忆
│   │   │   └── rag/                  # Embedding、Milvus 向量检索
│   │   ├── controller/               # REST API
│   │   ├── service/                  # 业务服务（小说、章节、记忆、校验等）
│   │   ├── entity/ repository/       # MySQL（MyBatis-Plus）与 MongoDB 数据层
│   │   ├── mcp/                      # MCP 客户端与服务端
│   │   └── config/                   # 安全、LangChain、Milvus 等配置
│   ├── src/main/resources/           # application.yml、题材指南、Mapper XML
│   └── moyun_novel_system.sql        # MySQL 建表脚本（Docker 首次启动自动导入）
├── frontend/                         # Vue 3 前端
│   └── src/{views,components,api,stores,router}
└── docker-compose.yml                # MySQL / MongoDB / Redis / Milvus
```

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- Node.js 18+
- Docker（用于启动基础设施）

### 1. 启动基础设施

```bash
docker compose up -d
```

启动 MySQL（3306）、MongoDB（27017）、Redis（6379）、Milvus（19530）。MySQL 首次启动会自动导入 `backend/moyun_novel_system.sql` 建表。

### 2. 启动后端

```bash
cd backend
mvn spring-boot:run
```

后端运行在 `http://localhost:21160/api`（端口由 `SERVER_PORT` 环境变量指定，默认 21160）。

### 3. 启动前端

```bash
cd frontend
npm install
npm run dev
```

打开 `http://localhost:21060`，Vite 已将 `/api` 代理到后端（`BACKEND_URL` 可覆盖，默认 `http://127.0.0.1:21160`）。

### 4. 初始化使用

1. 注册账号并登录
2. 进入「LLM 配置」页面添加对话模型（OpenAI 兼容的 API 地址与 Key）
3. 再添加一条 `usageType=embedding` 的向量模型配置，供 RAG 检索使用（向量维度需与 `milvus.dimension` 一致，默认 1024）
4. 通过「向导建书」或新建小说开始创作

> 未配置模型时无法调用生成功能；所有模型配置保存在数据库 `llm_config` 表中，由前端管理。

## 配置说明

主要配置位于 `backend/src/main/resources/application.yml`，支持环境变量覆盖：

| 环境变量 | 说明 | 默认值 |
| --- | --- | --- |
| `MYSQL_HOST` / `MYSQL_PORT` | MySQL 地址与端口 | `localhost` / `3306` |
| `MYSQL_DATABASE` | 数据库名 | `moyun_novel_system` |
| `MYSQL_USERNAME` / `MYSQL_PASSWORD` | 数据库账号 | `root` / `123456` |
| `SERVER_PORT` | 后端监听端口 | `21160` |
| `MONGODB_HOST` / `MONGODB_PORT` | MongoDB 地址与端口 | `localhost` / `27017` |
| `MONGODB_DATABASE` | 数据库名 | `moyun_novel_system` |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | Redis 连接 | `localhost` / `6379` / 空 |
| `JWT_SECRET` | JWT 密钥（至少 32 字节，**必填**） | 无，未设置则启动失败 |
| `MCP_CLIENT_ENABLED` | 是否启用 MCP 客户端 | `false` |
| `MCP_SERVER_ENABLED` | 是否启用 MCP 服务端 | `false` |

前端侧：`FRONTEND_PORT` 指定 Vite 监听端口（默认 `21060`），`BACKEND_URL` 指定后端地址（默认 `http://127.0.0.1:21160`）。

完整示例见 `backend/.env.example`。敏感配置请放入 `application-local.yml`（已被 `.gitignore` 忽略）。

### 写作模式

| 模式 | 说明 |
| --- | --- |
| `FAST` | 跳过校验，快速出稿 |
| `STREAMING` | 流式输出，执行连续性、OOC 与逻辑的轻量校验 |
| `FULL` | 完整多 Agent 流程，包含全部校验节点与 AuthorAgent 干预 |

## 主要 API 模块

后端统一前缀 `/api`，按资源划分：

- `/auth` 注册、登录、当前用户
- `/novel` 小说、章节、角色、线索、节奏、钩子
- `/novel/{novelId}/graph` 角色关系图谱
- `/wizard` 向导式建书
- `/genre` 题材模板与指南
- `/style-card` 文风卡片
- `/llm-config` 模型配置
- 章节生成支持流式（SSE）输出

## 测试

```bash
# 后端
cd backend && mvn test

# 前端
cd frontend && npm test
```
