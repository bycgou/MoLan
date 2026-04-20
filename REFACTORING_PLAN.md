# 墨韵 (MoYun) - AI网络小说创作系统重构计划

> **项目名称**: 墨韵 (MoYun)  
> **Slogan**: 灵感如墨, 韵律成章  
> **版本**: v2.0 重构版  
> **编写日期**: 2026-04-16

---

## 一、项目概述

### 1.1 项目定位

墨韵是一个基于多Agent协作的百万字网络小说智能创作平台,支持大纲生成、正文创作、人物管理、伏笔追踪、节奏控制等全流程AI辅助写作功能。

### 1.2 核心亮点

- **多Agent协作**: 7个专业Agent协同工作,模拟人类创作流程
- **上下文记忆**: RAG技术保持百万字长篇的一致性
- **节奏控制**: 独创的Strand节奏系统,科学管理剧情节奏
- **质量保障**: 多重校验机制,OOC检测、逻辑校验、伏笔追踪

### 1.3 技术栈

| 层级 | 技术选型 | 说明 |
|------|----------|------|
| 后端框架 | Spring Boot 3.2 | 主框架 |
| 开发语言 | Java 17 | LTS版本 |
| 数据库 | MySQL 8.0 | 主数据存储 |
| 文档数据库 | MongoDB 7.0 | 动态数据+RAG |
| 缓存 | Redis 7.0 | 缓存+向量存储 |
| 消息队列 | RabbitMQ | 异步任务 |
| ORM | MyBatis 3.x | 数据访问层 |
| AI框架 | LangChain4j 0.31 | Agent/LLM核心 |
| 前端框架 | Vue 3 + TypeScript | 用户界面 |
| UI组件 | Element Plus | 组件库 |

---

## 二、当前问题分析

### 2.1 架构问题

```
问题1: 双ORM共存
├── JPA (Hibernate): 8个Repository
├── MyBatis-Plus: 2个Mapper
└── 影响: 配置复杂,维护成本高

问题2: Service层混乱
├── service/impl/         (8个实现)
├── service/agent/impl/   (3个实现)
├── service/genre/impl/   (1个实现)
├── service/graph/impl/   (1个实现)
├── service/hook/impl/    (1个实现)
├── service/memory/impl/  (1个实现)
├── service/rhythm/impl/  (1个实现)
├── service/validator/impl/ (1个实现)
└── 影响: 接口与实现分散,难以维护

问题3: LLM调用手写
├── LlmServiceImpl: 376行手写HTTP调用
├── 手动构建请求/解析响应
└── 影响: 代码冗余,扩展困难
```

### 2.2 数据问题

```
问题1: 前后端字段不匹配 (20+处)
├── characterName vs name
├── clueContent vs title/description
├── rhythmTag vs strandType
└── 影响: 前端功能异常

问题2: MongoDB/MySQL职责不清
├── CharacterState存MongoDB
├── 但需要频繁查询和关联
└── 影响: 查询效率低

问题3: 缺少关键业务表
├── 生成日志 (无法追踪成本)
├── 用户配额 (无法控制用量)
├── 地点/势力 (世界观不完整)
└── 影响: 功能缺失
```

### 2.3 Agent问题

```
问题1: Agent定义复杂
├── 每个Agent需要3个文件 (接口+Request+Response)
├── 实现类在不同包下
└── 影响: 新增Agent成本高

问题2: 缺乏标准化
├── 每个Agent的调用方式不同
├── 没有统一的Tool调用机制
└── 影响: 协作困难
```

---

## 三、重构目标

### 3.1 架构目标

```
目标1: 统一数据层
├── MySQL: MyBatis (结构化数据)
├── MongoDB: MongoTemplate (动态数据)
└── 移除JPA

目标2: 统一Service层
├── 按业务域组织
├── 接口与实现在同一包下
└── 清晰的依赖关系

目标3: 标准化Agent
├── 基于LangChain4j AI Services
├── 统一的Tool调用机制
├── Chain编排
└── 代码量减少80%
```

### 3.2 数据目标

```
目标1: 前后端字段完全对齐
目标2: MySQL/MongoDB职责清晰
目标3: 补全缺失的业务表
```

### 3.3 质量目标

```
目标1: 代码可读性提升
目标2: 单元测试覆盖核心逻辑
目标3: API文档自动生成
```

---

## 四、技术选型说明

### 4.1 为什么选择MyBatis而不是MyBatis-Plus/JPA

| 考量因素 | MyBatis | MyBatis-Plus | JPA |
|----------|---------|--------------|-----|
| 学习成本 | 低(熟悉) | 低 | 高 |
| SQL控制 | 完全可控 | 部分可控 | 弱 |
| 复杂查询 | 简单 | 中等 | 困难 |
| 加字段 | 灵活 | 灵活 | 需改实体 |
| 项目适配 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐ |

**结论**: 开发者熟悉MyBatis,且项目表结构可能频繁变动,MyBatis最合适。

### 4.2 为什么选择LangChain4j而不是Spring AI

| 能力 | LangChain4j | Spring AI |
|------|-------------|-----------|
| Agent定义 | ⭐⭐⭐⭐⭐ AI Services | ⭐⭐ 需手动 |
| Tool调用 | ⭐⭐⭐⭐⭐ @Tool注解 | ⭐⭐⭐ 函数Bean |
| 链式编排 | ⭐⭐⭐⭐ Chain API | ⭐ 不支持 |
| 代码量 | 少(30行替代376行) | 多 |
| 项目适配 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ |

**结论**: Agent是项目核心亮点,LangChain4j的Agent能力远超Spring AI。

### 4.3 为什么选择MySQL+MongoDB混合方案

```
MySQL (结构化数据):
├── 人物基础信息
├── 地点、势力
├── 伏笔、章节
└── 优势: 查询方便,JOIN简单

MongoDB (动态数据):
├── 人物状态 (嵌套结构)
├── 人物关系图
├── 章节摘要 (RAG用)
├── 长期记忆 (RAG用)
└── 优势: 灵活扩展,适合嵌套

混合方案: 各取所长,两全其美
```

---

## 五、目标架构

### 5.1 项目结构

```
com.novel.system/
├── config/                        # 配置类
│   ├── MybatisConfig.java         # MyBatis配置
│   ├── MongoConfig.java           # MongoDB配置
│   ├── LangChainConfig.java       # LangChain4j配置
│   ├── SecurityConfig.java        # 安全配置
│   ├── RedisConfig.java           # Redis配置
│   └── RabbitConfig.java          # RabbitMQ配置
│
├── controller/                    # 控制器层
│   ├── AuthController.java        # 认证
│   ├── NovelController.java       # 小说
│   ├── ChapterController.java     # 章节
│   ├── CharacterController.java   # 人物
│   ├── ClueController.java        # 伏笔
│   ├── LocationController.java    # 地点 (新增)
│   ├── FactionController.java     # 势力 (新增)
│   ├── LlmConfigController.java   # 模型配置
│   ├── RhythmController.java      # 节奏
│   └── GenreController.java       # 题材
│
├── dto/                           # 数据传输对象
│   ├── request/                   # 请求DTO
│   ├── response/                  # 响应DTO
│   └── vo/                        # 视图对象
│
├── entity/                        # 实体类
│   ├── mysql/                     # MySQL实体 (MyBatis)
│   │   ├── NovelBase.java
│   │   ├── NovelChapter.java
│   │   ├── NovelChapterVersion.java
│   │   ├── NovelArc.java
│   │   ├── CharacterInfo.java     # 人物主数据
│   │   ├── CharacterGrowth.java   # 成长节点
│   │   ├── CharacterRelationship.java # 人物关系
│   │   ├── ClueBook.java
│   │   ├── Location.java          # 地点 (新增)
│   │   ├── Faction.java           # 势力 (新增)
│   │   ├── WorldClock.java
│   │   ├── LlmConfig.java
│   │   ├── PromptTemplate.java
│   │   ├── GenreTemplate.java
│   │   ├── User.java
│   │   ├── UserQuota.java         # 用户配额 (新增)
│   │   └── GenerationLog.java     # 生成日志 (新增)
│   └── mongo/                     # MongoDB文档
│       ├── CharacterState.java    # 人物动态状态
│       ├── ChapterSummary.java    # 章节摘要
│       ├── NovelMemory.java       # 长期记忆
│       └── NovelGraph.java        # 人物关系图
│
├── mapper/                        # MyBatis Mapper
│   ├── NovelBaseMapper.java
│   ├── NovelBaseMapper.xml
│   ├── NovelChapterMapper.java
│   ├── NovelChapterMapper.xml
│   ├── CharacterMapper.java
│   ├── CharacterMapper.xml
│   ├── CharacterGrowthMapper.java
│   ├── CharacterGrowthMapper.xml
│   ├── CharacterRelationshipMapper.java
│   ├── CharacterRelationshipMapper.xml
│   ├── ClueBookMapper.java
│   ├── ClueBookMapper.xml
│   ├── LocationMapper.java
│   ├── LocationMapper.xml
│   ├── FactionMapper.java
│   ├── FactionMapper.xml
│   ├── WorldClockMapper.java
│   ├── WorldClockMapper.xml
│   ├── LlmConfigMapper.java
│   ├── LlmConfigMapper.xml
│   ├── UserMapper.java
│   ├── UserMapper.xml
│   ├── UserQuotaMapper.java
│   ├── UserQuotaMapper.xml
│   ├── GenerationLogMapper.java
│   ├── GenerationLogMapper.xml
│   ├── GenreTemplateMapper.java
│   └── GenreTemplateMapper.xml
│
├── repository/                    # MongoDB Repository
│   ├── CharacterStateRepository.java
│   ├── ChapterSummaryRepository.java
│   ├── NovelMemoryRepository.java
│   └── NovelGraphRepository.java
│
├── service/                       # 业务服务层
│   ├── novel/                     # 小说服务
│   │   ├── NovelService.java
│   │   └── NovelServiceImpl.java
│   ├── chapter/                   # 章节服务
│   │   ├── ChapterService.java
│   │   └── ChapterServiceImpl.java
│   ├── character/                 # 人物服务
│   │   ├── CharacterService.java
│   │   └── CharacterServiceImpl.java
│   ├── clue/                      # 伏笔服务
│   │   ├── ClueService.java
│   │   └── ClueServiceImpl.java
│   ├── location/                  # 地点服务 (新增)
│   │   ├── LocationService.java
│   │   └── LocationServiceImpl.java
│   ├── faction/                   # 势力服务 (新增)
│   │   ├── FactionService.java
│   │   └── FactionServiceImpl.java
│   ├── user/                      # 用户服务
│   │   ├── UserService.java
│   │   └── UserServiceImpl.java
│   ├── quota/                     # 配额服务 (新增)
│   │   ├── QuotaService.java
│   │   └── QuotaServiceImpl.java
│   ├── llm/                       # LLM配置服务
│   │   ├── LlmConfigService.java
│   │   └── LlmConfigServiceImpl.java
│   └── genre/                     # 题材服务
│       ├── GenreTemplateService.java
│       └── GenreTemplateServiceImpl.java
│
├── ai/                            # AI核心模块 (新增)
│   ├── agent/                     # Agent定义
│   │   ├── OutlineAgent.java      # 大纲Agent
│   │   ├── WritingAgent.java      # 写作Agent
│   │   ├── SummaryAgent.java      # 摘要Agent
│   │   ├── ValidationAgent.java   # 校验Agent
│   │   └── RhythmAgent.java       # 节奏Agent
│   ├── tool/                      # Agent工具
│   │   ├── NovelToolProvider.java # 小说工具集
│   │   ├── CharacterTool.java     # 人物工具
│   │   ├── ClueTool.java          # 伏笔工具
│   │   └── LocationTool.java      # 地点工具
│   ├── chain/                     # 处理链
│   │   ├── ChapterGenerationChain.java # 章节生成链
│   │   └── ValidationChain.java   # 校验链
│   ├── memory/                    # 记忆管理
│   │   ├── NovelChatMemory.java   # 对话记忆
│   │   └── LongTermMemory.java    # 长期记忆
│   ├── rag/                       # RAG服务
│   │   ├── NovelRagService.java   # RAG主服务
│   │   └── EmbeddingService.java  # 向量化服务
│   └── prompt/                    # Prompt管理
│       └── PromptManager.java
│
├── mq/                            # 消息队列
│   ├── ChapterGenerateListener.java
│   └── SummaryGenerateListener.java
│
├── common/                        # 公共模块
│   ├── Result.java
│   ├── PageResult.java
│   └── Constants.java
│
├── exception/                     # 异常处理
│   ├── BusinessException.java
│   └── GlobalExceptionHandler.java
│
├── util/                          # 工具类
│   ├── IdGenerator.java
│   ├── JsonUtil.java
│   └── TokenizerUtil.java
│
└── NovelSystemApplication.java    # 启动类
```

### 5.2 数据库设计

#### 5.2.1 MySQL表结构

```sql
-- ============================================================
-- 1. 小说基础表
-- ============================================================
CREATE TABLE novel_base (
    novel_id VARCHAR(50) PRIMARY KEY,
    novel_name VARCHAR(100) NOT NULL,
    user_id VARCHAR(50) NOT NULL,
    genre VARCHAR(50),
    total_outline LONGTEXT,
    world_setting LONGTEXT,
    word_count INT DEFAULT 0,
    chapter_count INT DEFAULT 0,
    cover_url VARCHAR(500),
    author_name VARCHAR(50),
    word_count_goal INT DEFAULT 1000000,
    status INT DEFAULT 0,              -- 0草稿 1连载 2完结
    publish_status INT DEFAULT 0,      -- 0草稿 1审核 2发布
    view_count INT DEFAULT 0,
    like_count INT DEFAULT 0,
    collect_count INT DEFAULT 0,
    create_time DATETIME,
    update_time DATETIME,
    INDEX idx_user (user_id),
    INDEX idx_status (status),
    INDEX idx_genre (genre)
) COMMENT '小说基础信息';

-- ============================================================
-- 2. 章节表
-- ============================================================
CREATE TABLE novel_chapter (
    chapter_id VARCHAR(50) PRIMARY KEY,
    novel_id VARCHAR(50) NOT NULL,
    arc_id VARCHAR(50),
    chapter_num INT NOT NULL,
    chapter_title VARCHAR(200),
    chapter_outline TEXT,
    implementation_idea TEXT,
    content LONGTEXT,
    word_count INT DEFAULT 0,
    rhythm_tag VARCHAR(50),            -- quest/fire/constellation
    rhythm_intensity INT DEFAULT 5,
    plot_fingerprint TEXT,
    user_intent TEXT,
    is_vip BOOLEAN DEFAULT FALSE,
    is_published BOOLEAN DEFAULT FALSE,
    publish_time DATETIME,
    view_count INT DEFAULT 0,
    comment_count INT DEFAULT 0,
    ai_model_used VARCHAR(100),
    generation_time_ms INT,
    token_consumed INT,
    generation_status INT DEFAULT 0,   -- 0待生成 1生成中 2已生成 3失败
    is_deleted BOOLEAN DEFAULT FALSE,
    create_time DATETIME,
    update_time DATETIME,
    UNIQUE KEY uk_novel_chapter (novel_id, chapter_num),
    INDEX idx_novel (novel_id),
    INDEX idx_arc (arc_id)
) COMMENT '章节内容';

-- ============================================================
-- 3. 章节版本表
-- ============================================================
CREATE TABLE novel_chapter_version (
    version_id VARCHAR(50) PRIMARY KEY,
    chapter_id VARCHAR(50) NOT NULL,
    novel_id VARCHAR(50) NOT NULL,
    chapter_num INT NOT NULL,
    chapter_title VARCHAR(200),
    content LONGTEXT,
    word_count INT DEFAULT 0,
    version_desc VARCHAR(200),
    create_time DATETIME,
    INDEX idx_chapter (chapter_id),
    INDEX idx_novel_chapter (novel_id, chapter_num)
) COMMENT '章节历史版本';

-- ============================================================
-- 4. 小说弧线/卷表
-- ============================================================
CREATE TABLE novel_arc (
    arc_id VARCHAR(50) PRIMARY KEY,
    novel_id VARCHAR(50) NOT NULL,
    arc_name VARCHAR(100),
    arc_outline TEXT,
    arc_order INT,
    chapter_count INT DEFAULT 0,
    start_chapter INT,
    end_chapter INT,
    rhythm_tag VARCHAR(50),
    rhythm_intensity INT DEFAULT 5,
    create_time DATETIME,
    update_time DATETIME,
    INDEX idx_novel (novel_id)
) COMMENT '小说弧线/卷';

-- ============================================================
-- 5. 人物主表 (MySQL - 结构化数据)
-- ============================================================
CREATE TABLE character_info (
    character_id VARCHAR(50) PRIMARY KEY,
    novel_id VARCHAR(50) NOT NULL,
    character_name VARCHAR(100) NOT NULL,
    role VARCHAR(20),                  -- 主角/配角/反派/龙套
    description TEXT,                  -- 外貌、身份描述
    personality TEXT,                  -- 性格特点
    background TEXT,                   -- 出身背景
    voice_style VARCHAR(200),          -- 说话风格
    importance INT DEFAULT 1,          -- 重要程度 1-5
    first_appear_chapter INT,          -- 首次出场章节
    status INT DEFAULT 1,              -- 0删除 1正常
    create_time DATETIME,
    update_time DATETIME,
    INDEX idx_novel (novel_id),
    INDEX idx_role (novel_id, role),
    INDEX idx_name (novel_id, character_name)
) COMMENT '人物基础信息';

-- ============================================================
-- 6. 人物成长节点表
-- ============================================================
CREATE TABLE character_growth (
    growth_id VARCHAR(50) PRIMARY KEY,
    character_id VARCHAR(50) NOT NULL,
    novel_id VARCHAR(50) NOT NULL,
    chapter_num INT NOT NULL,
    title VARCHAR(200),
    growth_type VARCHAR(50),           -- 能力提升/性格转变/关系变化
    description TEXT,
    create_time DATETIME,
    INDEX idx_character (character_id),
    INDEX idx_novel_chapter (novel_id, chapter_num)
) COMMENT '人物成长节点';

-- ============================================================
-- 7. 人物关系表
-- ============================================================
CREATE TABLE character_relationship (
    id VARCHAR(50) PRIMARY KEY,
    novel_id VARCHAR(50) NOT NULL,
    character_id VARCHAR(50) NOT NULL,
    target_character_id VARCHAR(50) NOT NULL,
    relationship_type VARCHAR(50),     -- 师徒/兄弟/敌对/恋人/同门
    intimacy_value INT DEFAULT 0,      -- 亲密度 -100~100
    description TEXT,
    create_time DATETIME,
    update_time DATETIME,
    UNIQUE KEY uk_relation (character_id, target_character_id),
    INDEX idx_novel (novel_id),
    INDEX idx_character (character_id)
) COMMENT '人物关系';

-- ============================================================
-- 8. 地点表 (新增)
-- ============================================================
CREATE TABLE location (
    location_id VARCHAR(50) PRIMARY KEY,
    novel_id VARCHAR(50) NOT NULL,
    parent_id VARCHAR(50),             -- 上级地点 (树形结构)
    location_name VARCHAR(100) NOT NULL,
    location_type VARCHAR(50),         -- 大陆/城市/宗门/秘境/山脉
    description TEXT,
    importance INT DEFAULT 1,
    first_appear_chapter INT,
    status INT DEFAULT 1,
    create_time DATETIME,
    INDEX idx_novel (novel_id),
    INDEX idx_parent (parent_id),
    INDEX idx_type (novel_id, location_type)
) COMMENT '地点信息';

-- ============================================================
-- 9. 势力表 (新增)
-- ============================================================
CREATE TABLE faction (
    faction_id VARCHAR(50) PRIMARY KEY,
    novel_id VARCHAR(50) NOT NULL,
    faction_name VARCHAR(100) NOT NULL,
    faction_type VARCHAR(50),          -- 宗门/家族/帝国/商会/散修
    description TEXT,
    power_level INT DEFAULT 1,         -- 势力等级 1-10
    location_id VARCHAR(50),           -- 所在地
    leader_character_id VARCHAR(50),   -- 首领
    is_enemy BOOLEAN DEFAULT FALSE,
    first_appear_chapter INT,
    status INT DEFAULT 1,
    create_time DATETIME,
    INDEX idx_novel (novel_id),
    INDEX idx_type (novel_id, faction_type)
) COMMENT '势力信息';

-- ============================================================
-- 10. 伏笔表
-- ============================================================
CREATE TABLE clue_book (
    clue_id VARCHAR(50) PRIMARY KEY,
    novel_id VARCHAR(50) NOT NULL,
    clue_content TEXT NOT NULL,
    clue_type VARCHAR(50),             -- 主线伏笔/支线伏笔/暗线
    priority INT DEFAULT 2,            -- 1高 2中 3低
    bury_chapter INT NOT NULL,
    recycle_chapter INT,
    related_characters VARCHAR(500),   -- 关联人物ID,逗号分隔
    related_locations VARCHAR(500),    -- 关联地点ID,逗号分隔
    status INT DEFAULT 0,              -- 0未回收 1已回收
    create_time DATETIME,
    INDEX idx_novel (novel_id),
    INDEX idx_status (novel_id, status),
    INDEX idx_bury (novel_id, bury_chapter)
) COMMENT '伏笔管理';

-- ============================================================
-- 11. 世界时间线表
-- ============================================================
CREATE TABLE world_clock (
    clock_id VARCHAR(50) PRIMARY KEY,
    novel_id VARCHAR(50) NOT NULL UNIQUE,
    current_time VARCHAR(100),         -- 如: 午夜/清晨
    current_date VARCHAR(100),         -- 如: 春分第三日
    season VARCHAR(20),                -- 春/夏/秋/冬
    weather VARCHAR(50),               -- 晴/雨/雪
    chapter_day INT DEFAULT 0,         -- 本章天数
    total_days INT DEFAULT 0,          -- 累计天数
    update_chapter INT DEFAULT 0,
    update_time DATETIME,
    INDEX idx_novel (novel_id)
) COMMENT '世界时间线';

-- ============================================================
-- 12. 模型配置表
-- ============================================================
CREATE TABLE llm_config (
    config_id VARCHAR(50) PRIMARY KEY,
    config_name VARCHAR(100) NOT NULL,
    api_key VARCHAR(500) NOT NULL,
    base_url VARCHAR(1000) NOT NULL,
    model_name VARCHAR(100) NOT NULL,
    model_type VARCHAR(50) DEFAULT 'openai',
    api_endpoint VARCHAR(200),
    auth_type VARCHAR(50) DEFAULT 'bearer',
    max_tokens INT DEFAULT 4096,
    temperature DOUBLE DEFAULT 0.7,
    usage_type VARCHAR(50) DEFAULT 'all',  -- all/outline/writing/summary/audit
    is_active INT DEFAULT 1,
    user_id VARCHAR(50),
    description VARCHAR(500),
    embedding_model VARCHAR(100),
    create_time DATETIME,
    update_time DATETIME,
    INDEX idx_user (user_id),
    INDEX idx_active (user_id, usage_type, is_active)
) COMMENT 'LLM模型配置';

-- ============================================================
-- 13. Prompt模板表
-- ============================================================
CREATE TABLE prompt_template (
    template_id VARCHAR(32) PRIMARY KEY,
    agent_type VARCHAR(50) NOT NULL,   -- outline/writing/summary/audit/ooc_validator
    template_name VARCHAR(100),
    system_instruction TEXT,
    few_shot_examples JSON,
    version INT DEFAULT 1,
    is_active INT DEFAULT 1,
    description VARCHAR(500),
    create_time DATETIME,
    update_time DATETIME,
    INDEX idx_agent (agent_type, is_active)
) COMMENT 'Prompt模板';

-- ============================================================
-- 14. 用户表
-- ============================================================
CREATE TABLE sys_user (
    user_id VARCHAR(50) PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(200) NOT NULL,
    email VARCHAR(100),
    nickname VARCHAR(50),
    avatar VARCHAR(500),
    role VARCHAR(20) DEFAULT 'user',   -- user/admin/vip
    status INT DEFAULT 1,
    membership_level INT DEFAULT 0,
    daily_generation_count INT DEFAULT 0,
    total_tokens_used BIGINT DEFAULT 0,
    last_login_time DATETIME,
    last_login_ip VARCHAR(50),
    create_time DATETIME,
    update_time DATETIME,
    INDEX idx_username (username),
    INDEX idx_status (status)
) COMMENT '用户信息';

-- ============================================================
-- 15. 用户配额表 (新增)
-- ============================================================
CREATE TABLE user_quota (
    user_id VARCHAR(50) PRIMARY KEY,
    daily_limit INT DEFAULT 50,
    monthly_limit INT DEFAULT 1000,
    daily_used INT DEFAULT 0,
    monthly_used INT DEFAULT 0,
    total_tokens_used BIGINT DEFAULT 0,
    last_reset_date DATE,
    update_time DATETIME
) COMMENT '用户配额';

-- ============================================================
-- 16. 生成日志表 (新增)
-- ============================================================
CREATE TABLE generation_log (
    log_id VARCHAR(50) PRIMARY KEY,
    user_id VARCHAR(50) NOT NULL,
    novel_id VARCHAR(50),
    chapter_num INT,
    agent_type VARCHAR(50),            -- outline/writing/summary/validation
    model_name VARCHAR(100),
    prompt_tokens INT DEFAULT 0,
    completion_tokens INT DEFAULT 0,
    total_tokens INT DEFAULT 0,
    duration_ms INT DEFAULT 0,
    status VARCHAR(20),                -- success/failed
    error_message TEXT,
    create_time DATETIME,
    INDEX idx_user_time (user_id, create_time),
    INDEX idx_novel (novel_id),
    INDEX idx_agent (agent_type, create_time)
) COMMENT 'AI生成日志';

-- ============================================================
-- 17. 题材模板表
-- ============================================================
CREATE TABLE genre_template (
    template_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_name VARCHAR(100),
    genre VARCHAR(50) NOT NULL,
    description TEXT,
    recommended_tags TEXT,
    plot_structure TEXT,
    character_setting TEXT,
    style_features TEXT,
    sample_opening TEXT,
    create_time DATETIME,
    update_time DATETIME,
    INDEX idx_genre (genre)
) COMMENT '题材模板';
```

#### 5.2.2 MongoDB集合设计

```javascript
// ============================================================
// 1. 人物动态状态 (嵌套结构, 变化频繁)
// ============================================================
{
    "_id": ObjectId(),
    "characterId": "char_001",           // 关联MySQL character_info
    "novelId": "novel_001",
    "currentStatus": {
        "health": 100,                   // 健康值
        "emotion": "平静",               // 当前情绪
        "location": "青云宗",           // 当前位置
        "cultivationLevel": "筑基期",    // 修为等级 (玄幻特有)
        "cultivationProgress": 35        // 修为进度 0-100
    },
    "relationshipQuickMap": {            // 快速查询亲密度
        "char_002": 80,
        "char_003": -50
    },
    "inventory": ["青云剑", "回气丹x10"],   // 随身物品
    "skills": ["青云剑诀", "御风术"],        // 已学功法
    "buffs": [],                          // 增益效果
    "debuffs": [],                        // 减益效果
    "lastUpdateChapter": 15,
    "updateTime": ISODate()
}

// 索引:
// { "characterId": 1 } 唯一索引
// { "novelId": 1 }

// ============================================================
// 2. 章节摘要 (给RAG用)
// ============================================================
{
    "_id": ObjectId(),
    "novelId": "novel_001",
    "chapterNum": 1,
    "summary": "主角林轩重生回到少年时期,发现自己回到了十年前...",
    "keywords": ["重生", "复仇", "青云宗"],
    "characters": ["林轩", "林天"],
    "locations": ["青云宗", "落霞城"],
    "plotPoints": ["发现重生", "决定重修", "遇到故人"],
    "emotionTone": "热血",              // 情感基调
    "pacing": "快速",                   // 节奏
    "embedding": [0.12, -0.34, ...],    // 向量 (1536维)
    "createTime": ISODate()
}

// 索引:
// { "novelId": 1, "chapterNum": 1 } 复合唯一索引
// { "embedding": "vector" } 向量索引

// ============================================================
// 3. 小说长期记忆 (给RAG用)
// ============================================================
{
    "_id": ObjectId(),
    "novelId": "novel_001",
    "memoryType": "character",           // character/location/plot/world/cultivation
    "relatedId": "char_001",             // 关联ID
    "content": "林轩是青云宗外门弟子,性格坚韧不拔,前世被师兄陷害...",
    "startChapter": 1,
    "endChapter": 50,
    "importance": 5,                     // 重要程度 1-5
    "tags": ["主角", "重生", "外门弟子"],
    "embedding": [0.23, 0.45, ...],      // 向量
    "createTime": ISODate(),
    "updateTime": ISODate()
}

// 索引:
// { "novelId": 1, "memoryType": 1 }
// { "novelId": 1, "relatedId": 1 }
// { "embedding": "vector" } 向量索引

// ============================================================
// 4. 人物关系图 (图结构, MongoDB存储方便)
// ============================================================
{
    "_id": "novel_001",                  // 用novelId作为主键
    "nodes": [
        { 
            "id": "char_001", 
            "name": "林轩", 
            "type": "person",
            "role": "主角",
            "importance": 5
        },
        { 
            "id": "loc_001", 
            "name": "青云宗", 
            "type": "location",
            "locationType": "宗门"
        },
        {
            "id": "faction_001",
            "name": "青云宗",
            "type": "faction",
            "factionType": "宗门"
        }
    ],
    "edges": [
        { 
            "source": "char_001", 
            "target": "loc_001", 
            "relation": "所属",
            "weight": 1
        },
        { 
            "source": "char_001", 
            "target": "char_002", 
            "relation": "师徒",
            "weight": 3,
            "description": "林轩的师父"
        }
    ],
    "lastUpdateChapter": 50,
    "updateTime": ISODate()
}

// 索引:
// { "_id": 1 } 主键

// ============================================================
// 5. AI生成缓存 (避免重复生成)
// ============================================================
{
    "_id": ObjectId(),
    "novelId": "novel_001",
    "requestHash": "abc123...",          // 请求内容的hash
    "requestType": "outline",            // outline/writing/summary
    "request": { ... },                  // 原始请求
    "response": "...",                   // 生成结果
    "modelUsed": "gpt-4",
    "tokensUsed": 1500,
    "createTime": ISODate(),
    "expireTime": ISODate()              // 过期时间
}

// 索引:
// { "novelId": 1, "requestHash": 1 } 复合唯一索引
// { "expireTime": 1 } TTL索引
```

---

## 六、分阶段实施计划

### 阶段一: 数据层统一 (第1-2周)

**目标**: 移除JPA, 统一MyBatis, 完善数据库结构

#### 第1周: 实体类改造

| 序号 | 任务 | 影响文件 | 预计工时 |
|------|------|----------|----------|
| 1.1 | 移除JPA注解, 改为MyBatis | 10个实体类 | 2天 |
| 1.2 | 创建Mapper接口 | 15个Mapper | 1天 |
| 1.3 | 编写Mapper XML | 15个XML | 2天 |

#### 第2周: 新增表+数据迁移

| 序号 | 任务 | 影响文件 | 预计工时 |
|------|------|----------|----------|
| 2.1 | 创建新表SQL脚本 | - | 0.5天 |
| 2.2 | 实现新实体+Mapper | 6个文件 | 1天 |
| 2.3 | 迁移CharacterState到MySQL | 数据迁移脚本 | 1天 |
| 2.4 | 调整Service层代码 | 10个Service | 1.5天 |
| 2.5 | 移除JPA依赖, 清理配置 | pom.xml, 配置类 | 0.5天 |

**阶段验收标准**:
- [ ] 所有CRUD操作正常
- [ ] 前后端联调通过
- [ ] 单元测试通过

---

### 阶段二: 引入LangChain4j (第3-4周)

**目标**: 替换手写LLM调用, 定义Agent接口

#### 第3周: LangChain4j集成

| 序号 | 任务 | 影响文件 | 预计工时 |
|------|------|----------|----------|
| 3.1 | 添加LangChain4j依赖 | pom.xml | 0.5天 |
| 3.2 | 编写LangChain配置类 | LangChainConfig.java | 0.5天 |
| 3.3 | 定义Agent接口 | 5个Agent接口 | 1天 |
| 3.4 | 实现OutlineAgent | 1个文件 | 1天 |
| 3.5 | 实现WritingAgent | 1个文件 | 1天 |

#### 第4周: 完成Agent+替换LlmService

| 序号 | 任务 | 影响文件 | 预计工时 |
|------|------|----------|----------|
| 4.1 | 实现SummaryAgent | 1个文件 | 0.5天 |
| 4.2 | 实现ValidationAgent | 1个文件 | 1天 |
| 4.3 | 实现RhythmAgent | 1个文件 | 0.5天 |
| 4.4 | 重写LlmServiceImpl | 1个文件 | 1天 |
| 4.5 | 集成测试 | - | 2天 |

**阶段验收标准**:
- [ ] Agent能正常调用LLM
- [ ] 支持切换不同模型配置
- [ ] 生成质量与原版本一致或更好

---

### 阶段三: Tool Calling与Chain (第5-6周)

**目标**: 实现Agent工具调用和链式编排

#### 第5周: Tool实现

| 序号 | 任务 | 影响文件 | 预计工时 |
|------|------|----------|----------|
| 5.1 | 定义Tool接口规范 | 设计文档 | 0.5天 |
| 5.2 | 实现NovelToolProvider | 1个文件 | 1天 |
| 5.3 | 实现CharacterTool | 1个文件 | 0.5天 |
| 5.4 | 实现ClueTool | 1个文件 | 0.5天 |
| 5.5 | 实现LocationTool | 1个文件 | 0.5天 |
| 5.6 | Agent集成Tool | 5个Agent | 2天 |

#### 第6周: Chain编排

| 序号 | 任务 | 影响文件 | 预计工时 |
|------|------|----------|----------|
| 6.1 | 设计Chain流程 | 设计文档 | 0.5天 |
| 6.2 | 实现ChapterGenerationChain | 1个文件 | 2天 |
| 6.3 | 实现ValidationChain | 1个文件 | 1天 |
| 6.4 | 优化Agent协作逻辑 | 5个Agent | 1.5天 |

**阶段验收标准**:
- [ ] Agent能自动调用工具查询数据
- [ ] 多Agent协作正常
- [ ] 生成质量有明显提升

---

### 阶段四: RAG与记忆 (第7-8周)

**目标**: 实现上下文记忆和知识检索

#### 第7周: 向量存储+RAG

| 序号 | 任务 | 影响文件 | 预计工时 |
|------|------|----------|----------|
| 7.1 | 配置Redis向量存储 | RagConfig.java | 1天 |
| 7.2 | 实现EmbeddingService | 1个文件 | 1天 |
| 7.3 | 实现NovelRagService | 1个文件 | 2天 |
| 7.4 | Agent集成RAG | 5个Agent | 1天 |

#### 第8周: 记忆系统

| 序号 | 任务 | 影响文件 | 预计工时 |
|------|------|----------|----------|
| 8.1 | 实现NovelChatMemory | 1个文件 | 1天 |
| 8.2 | 实现LongTermMemory | 1个文件 | 1.5天 |
| 8.3 | 自动记忆写入 | 服务类 | 1天 |
| 8.4 | 全链路测试 | - | 1.5天 |

**阶段验收标准**:
- [ ] Agent能检索历史章节内容
- [ ] 人物/情节一致性显著提升
- [ ] 长期记忆正常工作

---

### 阶段五: 前端适配 (第9周)

**目标**: 统一前后端数据格式

| 序号 | 任务 | 影响文件 | 预计工时 |
|------|------|----------|----------|
| 5.1 | 统一API响应格式 | 后端Controller | 1天 |
| 5.2 | 更新前端types/index.ts | 1个文件 | 0.5天 |
| 5.3 | 修复字段映射 | 多个文件 | 1天 |
| 5.4 | 新增页面(地点/势力) | 2个Vue文件 | 1.5天 |
| 5.5 | 联调测试 | - | 1天 |

**阶段验收标准**:
- [ ] 前后端字段完全对齐
- [ ] 新增功能页面可用
- [ ] 端到端测试通过

---

## 七、迁移指南

### 7.1 数据库迁移

```bash
# 1. 备份现有数据
mysqldump -u root -p ai_novel > backup_before_refactor.sql

# 2. 执行新表SQL
mysql -u root -p ai_novel < new_tables.sql

# 3. 迁移MongoDB数据到MySQL (编写迁移脚本)
python migrate_character_to_mysql.py

# 4. 验证数据完整性
python verify_data.py
```

### 7.2 代码迁移检查清单

- [ ] 所有JPA @Entity 改为 MyBatis 实体
- [ ] 所有JpaRepository 改为 Mapper接口
- [ ] 创建对应的Mapper XML文件
- [ ] Service层代码更新
- [ ] 移除JPA相关依赖
- [ ] 移除@EnableJpaRepositories配置
- [ ] 更新application.yml配置

### 7.3 回滚方案

```
如果重构过程中出现问题:
1. 代码回滚: git revert到重构前的commit
2. 数据库回滚: 使用backup_before_refactor.sql恢复
3. MongoDB数据: 保留原始数据,随时可恢复
```

---

## 八、风险与应对

| 风险 | 影响 | 概率 | 应对措施 |
|------|------|------|----------|
| 数据迁移丢失 | 高 | 低 | 完整备份+验证脚本 |
| LangChain4j版本兼容 | 中 | 中 | 锁定0.31.0版本 |
| 国内模型API不兼容 | 中 | 低 | LangChain4j支持自定义baseUrl |
| 重构周期延长 | 中 | 中 | 每阶段独立可上线 |
| 团队学习成本 | 低 | 高 | 提供培训文档 |

---

## 九、验收标准

### 9.1 功能验收

- [ ] 所有现有功能正常工作
- [ ] 新增功能(地点/势力)可用
- [ ] Agent协作流程完整
- [ ] RAG记忆正常

### 9.2 性能验收

- [ ] 章节生成时间 ≤ 60秒
- [ ] API响应时间 ≤ 500ms
- [ ] 并发支持 ≥ 10用户

### 9.3 质量验收

- [ ] 代码覆盖率 ≥ 60%
- [ ] 无Critical级别Bug
- [ ] API文档完整

---

## 十、附录

### 10.1 LangChain4j Agent示例代码

```java
// 大纲生成Agent
public interface OutlineAgent {
    
    @SystemMessage("""
        你是一个网络小说大纲设计师。根据小说名称、题材和核心创意,
        生成详细的章纲, 包含剧情要点、冲突设计、爽点安排。
        """)
    @UserMessage("""
        小说名称: {{novelName}}
        题材: {{genre}}
        章节号: {{chapterNum}}
        用户意图: {{userIntent}}
        前情提要: {{previousSummary}}
        
        请生成本章的详细大纲。
        """)
    String generateOutline(
        @V("novelName") String novelName,
        @V("genre") String genre,
        @V("chapterNum") int chapterNum,
        @V("userIntent") String userIntent,
        @V("previousSummary") String previousSummary
    );
}

// 写作Agent (带Tool)
public interface WritingAgent {
    
    @SystemMessage("""
        你是一个百万字网络小说作家。写作风格: 爽文、节奏快、代入感强。
        你可以调用工具查询人物、地点、伏笔等信息来保证上下文连贯。
        """)
    @Tool("查询人物详细信息")
    CharacterInfo getCharacterInfo(
        @P("小说ID") String novelId,
        @P("人物名称") String characterName
    );
    
    @Tool("获取前一章内容")
    String getPreviousChapter(
        @P("小说ID") String novelId,
        @P("章节号") int chapterNum
    );
    
    @UserMessage("""
        请根据以下大纲创作第{{chapterNum}}章正文:
        章纲: {{outline}}
        前一章结尾: {{previousChapterEnding}}
        要求: 字数{{targetWordCount}}字左右
        """)
    String writeChapter(
        @V("chapterNum") int chapterNum,
        @V("outline") String outline,
        @V("previousChapterEnding") String previousEnding,
        @V("targetWordCount") int targetWordCount
    );
}
```

### 10.2 MyBatis Mapper示例

```xml
<!-- CharacterMapper.xml -->
<mapper namespace="com.novel.system.mapper.CharacterMapper">

    <resultMap id="CharacterMap" type="com.novel.system.entity.mysql.CharacterInfo">
        <id column="character_id" property="characterId"/>
        <result column="novel_id" property="novelId"/>
        <result column="character_name" property="characterName"/>
        <result column="role" property="role"/>
        <result column="description" property="description"/>
        <result column="personality" property="personality"/>
        <result column="background" property="background"/>
        <result column="voice_style" property="voiceStyle"/>
        <result column="importance" property="importance"/>
        <result column="first_appear_chapter" property="firstAppearChapter"/>
        <result column="status" property="status"/>
        <result column="create_time" property="createTime"/>
        <result column="update_time" property="updateTime"/>
    </resultMap>

    <select id="selectById" resultMap="CharacterMap">
        SELECT * FROM character_info 
        WHERE character_id = #{characterId} AND status = 1
    </select>

    <select id="selectByNovelId" resultMap="CharacterMap">
        SELECT * FROM character_info 
        WHERE novel_id = #{novelId} AND status = 1
        ORDER BY importance DESC, create_time ASC
    </select>

    <insert id="insert">
        INSERT INTO character_info (
            character_id, novel_id, character_name, role,
            description, personality, background, voice_style,
            importance, first_appear_chapter, status,
            create_time, update_time
        ) VALUES (
            #{characterId}, #{novelId}, #{characterName}, #{role},
            #{description}, #{personality}, #{background}, #{voiceStyle},
            #{importance}, #{firstAppearChapter}, 1,
            NOW(), NOW()
        )
    </insert>

</mapper>
```

---

## 十一、版本历史

| 版本 | 日期 | 作者 | 变更说明 |
|------|------|------|----------|
| v1.0 | 2026-04-16 | - | 初始版本 |
| v2.0 | 2026-04-16 | - | 重构计划, 基于LangChain4j+MyBatis |

---

> **项目愿景**: 让AI成为网络小说作者的得力助手, 释放创作力, 成就百万字长篇。
