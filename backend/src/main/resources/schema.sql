-- ============================================================
-- 墨韵 (MoYun) AI小说创作系统 - 数据库初始化脚本
-- 执行前请先创建数据库: CREATE DATABASE novel_system CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- ============================================================

USE novel_system;

-- ----------------------------
-- 用户表
-- ----------------------------
CREATE TABLE IF NOT EXISTS sys_user (
    user_id VARCHAR(32) PRIMARY KEY COMMENT '用户ID',
    username VARCHAR(50) NOT NULL COMMENT '用户名',
    password VARCHAR(100) NOT NULL COMMENT '密码(BCrypt加密)',
    email VARCHAR(100) COMMENT '邮箱',
    nickname VARCHAR(50) COMMENT '昵称',
    avatar VARCHAR(500) COMMENT '头像URL',
    role VARCHAR(20) DEFAULT 'user' COMMENT '角色: user/admin',
    status TINYINT DEFAULT 1 COMMENT '状态: 0-禁用, 1-正常',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_username (username),
    UNIQUE KEY uk_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ----------------------------
-- 小说基础表
-- ----------------------------
CREATE TABLE IF NOT EXISTS novel_base (
    novel_id VARCHAR(32) PRIMARY KEY COMMENT '小说ID',
    user_id VARCHAR(32) NOT NULL COMMENT '所属用户ID',
    novel_name VARCHAR(100) NOT NULL COMMENT '小说名称',
    genre VARCHAR(20) DEFAULT '玄幻' COMMENT '题材类型',
    total_outline TEXT COMMENT '小说总纲',
    world_setting TEXT COMMENT '世界观设定',
    word_count INT DEFAULT 0 COMMENT '总字数',
    chapter_count INT DEFAULT 0 COMMENT '总章节数',
    status TINYINT DEFAULT 0 COMMENT '状态: 0-草稿, 1-连载中, 2-已完结',
    is_deleted TINYINT DEFAULT 0 COMMENT '软删除标记',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_user_id (user_id),
    KEY idx_is_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='小说基础表';

-- ----------------------------
-- 章节表
-- ----------------------------
CREATE TABLE IF NOT EXISTS novel_chapter (
    chapter_id VARCHAR(32) PRIMARY KEY COMMENT '章节ID',
    novel_id VARCHAR(32) NOT NULL COMMENT '所属小说ID',
    arc_id VARCHAR(32) COMMENT '所属卷/弧线ID',
    chapter_num INT NOT NULL COMMENT '章节序号',
    chapter_title VARCHAR(200) COMMENT '章节标题',
    chapter_outline TEXT COMMENT '章节大纲',
    implementation_idea TEXT COMMENT '实现思路',
    content LONGTEXT COMMENT '章节正文',
    word_count INT DEFAULT 0 COMMENT '字数',
    rhythm_tag VARCHAR(20) COMMENT '节奏标签: quest/fire/constellation',
    rhythm_intensity INT DEFAULT 5 COMMENT '节奏强度(1-10)',
    plot_fingerprint TEXT COMMENT '剧情指纹(300字摘要)',
    user_intent TEXT COMMENT '用户意图',
    generation_status TINYINT DEFAULT 0 COMMENT '生成状态: 0-待生成, 1-生成中, 2-已生成, 3-失败',
    is_deleted TINYINT DEFAULT 0 COMMENT '软删除标记',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_novel_id (novel_id),
    KEY idx_novel_chapter (novel_id, chapter_num),
    KEY idx_is_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='章节表';

-- ----------------------------
-- 章节版本表
-- ----------------------------
CREATE TABLE IF NOT EXISTS novel_chapter_version (
    version_id VARCHAR(32) PRIMARY KEY COMMENT '版本ID',
    chapter_id VARCHAR(32) NOT NULL COMMENT '章节ID',
    novel_id VARCHAR(32) NOT NULL COMMENT '小说ID',
    chapter_num INT COMMENT '章节号',
    chapter_title VARCHAR(200) COMMENT '章节标题',
    content LONGTEXT COMMENT '章节内容',
    word_count INT DEFAULT 0 COMMENT '字数',
    version_desc VARCHAR(200) COMMENT '版本描述',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_chapter_id (chapter_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='章节版本表';

-- ----------------------------
-- 世界时间线表
-- ----------------------------
CREATE TABLE IF NOT EXISTS world_clock (
    clock_id VARCHAR(32) PRIMARY KEY COMMENT '时钟ID',
    novel_id VARCHAR(32) NOT NULL COMMENT '所属小说ID',
    `current_time` VARCHAR(50) COMMENT '当前时间描述',
    `current_date` VARCHAR(100) COMMENT '当前日期描述',
    season VARCHAR(20) COMMENT '当前季节',
    weather VARCHAR(50) COMMENT '当前天气',
    chapter_day INT DEFAULT 0 COMMENT '当前章节对应天数',
    total_days INT DEFAULT 0 COMMENT '小说世界总天数',
    update_chapter INT DEFAULT 0 COMMENT '最后更新的章节号',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_novel_id (novel_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='世界时间线表';

-- ----------------------------
-- LLM模型配置表
-- ----------------------------
CREATE TABLE IF NOT EXISTS llm_config (
    config_id VARCHAR(32) PRIMARY KEY COMMENT '配置ID',
    user_id VARCHAR(32) COMMENT '所属用户ID',
    config_name VARCHAR(50) COMMENT '配置名称',
    provider VARCHAR(20) DEFAULT 'openai' COMMENT '提供商',
    api_key VARCHAR(500) COMMENT 'API密钥',
    api_base_url VARCHAR(500) COMMENT 'API基础URL',
    model_name VARCHAR(100) COMMENT '模型名称',
    auth_type VARCHAR(20) DEFAULT 'bearer' COMMENT '认证类型: bearer/api_key/anthropic',
    temperature DECIMAL(3,2) DEFAULT 0.70 COMMENT '温度',
    max_tokens INT DEFAULT 4096 COMMENT '最大Token数',
    top_p DECIMAL(3,2) DEFAULT 1.00 COMMENT 'TopP',
    usage_type VARCHAR(20) DEFAULT 'all' COMMENT '用途: all/outline/writing/summary/audit',
    is_active TINYINT DEFAULT 1 COMMENT '是否启用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_user_id (user_id),
    KEY idx_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='LLM模型配置表';

-- ----------------------------
-- 卷/弧线表
-- ----------------------------
CREATE TABLE IF NOT EXISTS novel_arc (
    arc_id VARCHAR(32) PRIMARY KEY COMMENT '弧线ID',
    novel_id VARCHAR(32) NOT NULL COMMENT '所属小说ID',
    arc_name VARCHAR(100) COMMENT '弧线名称',
    arc_order INT DEFAULT 0 COMMENT '排序',
    description TEXT COMMENT '描述',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_novel_id (novel_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='卷/弧线表';

-- ----------------------------
-- 伏笔/线索表
-- ----------------------------
CREATE TABLE IF NOT EXISTS clue_book (
    clue_id VARCHAR(32) PRIMARY KEY COMMENT '线索ID',
    novel_id VARCHAR(32) NOT NULL COMMENT '所属小说ID',
    title VARCHAR(200) COMMENT '线索标题',
    description TEXT COMMENT '线索描述',
    clue_type VARCHAR(20) COMMENT '类型: 主线伏笔/支线伏笔/暗线',
    planted_chapter INT COMMENT '埋下章节号',
    recycle_chapter INT COMMENT '回收章节号',
    status TINYINT DEFAULT 0 COMMENT '状态: 0-未回收, 1-已回收',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_novel_id (novel_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='伏笔线索表';

-- ----------------------------
-- 题材模板表
-- ----------------------------
CREATE TABLE IF NOT EXISTS genre_template (
    template_id VARCHAR(32) PRIMARY KEY COMMENT '模板ID',
    genre VARCHAR(20) COMMENT '题材',
    tropes JSON COMMENT '常见套路',
    core_themes JSON COMMENT '核心主题',
    character_archetypes JSON COMMENT '人物原型',
    plot_patterns JSON COMMENT '情节模式',
    pitfalls JSON COMMENT '避坑指南',
    status TINYINT DEFAULT 1 COMMENT '状态',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_genre (genre)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题材模板表';

-- ----------------------------
-- 提示词模板表
-- ----------------------------
CREATE TABLE IF NOT EXISTS prompt_template (
    template_id VARCHAR(32) PRIMARY KEY COMMENT '模板ID',
    template_key VARCHAR(50) NOT NULL COMMENT '模板键名',
    template_name VARCHAR(100) COMMENT '模板名称',
    template_content TEXT NOT NULL COMMENT '模板内容',
    description VARCHAR(500) COMMENT '描述',
    version INT DEFAULT 1 COMMENT '版本号',
    is_active TINYINT DEFAULT 1 COMMENT '是否启用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_key (template_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='提示词模板表';

-- ============================================================
-- 插入默认数据
-- ============================================================

-- 默认提示词模板 (大纲)
INSERT IGNORE INTO prompt_template (template_id, template_key, template_name, template_content, description) VALUES
('default_outline', 'outline', '章纲生成模板', 
'你是一位经验丰富的小说编辑。请根据以下信息生成第{{chapterNum}}章的章纲。

小说信息：
- 名称：{{novelName}}
- 题材：{{genre}}
- 总纲：{{totalOutline}}

世界观：{{worldSetting}}

当前状态：
- 已写章节：{{currentChapterCount}}章
- 当前时间线：{{currentTime}}
- 当前季节：{{season}}

之前章节摘要：
{{previousSummaries}}

人物状态：
{{characterStates}}

节奏要求：本章为{{strandType}}线，请按照该线的特点推进剧情。

请生成：
1. 章纲（200-300字，包含主要情节走向）
2. 3个不同的实现思路（每个50-100字）

请以JSON格式返回：
{
  "outline": "章纲内容",
  "implementationIdeas": ["思路1", "思路2", "思路3"]
}', '默认章纲生成模板');

-- 默认提示词模板 (写作)
INSERT IGNORE INTO prompt_template (template_id, template_key, template_name, template_content, description) VALUES
('default_writing', 'writing', '章节写作模板',
'你是一位畅销网络小说作家。请根据以下信息创作第{{chapterNum}}章的正文。

小说信息：
- 名称：{{novelName}}
- 题材：{{genre}}
- 总纲：{{totalOutline}}

本章章纲：{{chapterOutline}}

选定的实现思路：{{selectedIdea}}

写作要求：
- 字数：{{targetWordCount}}字左右
- 节奏线：{{strandType}}
- 风格：{{writingStyle}}

请创作完整的章节正文，包含场景描写、人物对话、心理活动等。', '默认章节写作模板');

-- 默认提示词模板 (摘要)
INSERT IGNORE INTO prompt_template (template_id, template_key, template_name, template_content, description) VALUES
('default_summary', 'summary', '章节摘要模板',
'请从以下章节内容中提取结构化摘要：

章节内容：
{{chapterContent}}

请提取：
1. 剧情指纹（一句话描述本章核心事件）
2. 节奏类型（quest/fire/constellation之一）
3. 出场人物
4. 关键事件

返回JSON格式。', '默认章节摘要模板');
