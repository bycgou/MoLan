-- =============================================
-- MoYun AI小说写作系统 - 数据库初始化脚本
-- 数据库：moyun_novel_system
-- 版本：1.0.0
-- =============================================

-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS moyun_novel_system CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE moyun_novel_system;

-- 1. 用户表 (sys_user)
CREATE TABLE IF NOT EXISTS sys_user (
    user_id VARCHAR(36) PRIMARY KEY COMMENT '用户ID，UUID',
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    password VARCHAR(255) NOT NULL COMMENT '密码（BCrypt加密）',
    email VARCHAR(100) NOT NULL UNIQUE COMMENT '邮箱',
    nickname VARCHAR(50) COMMENT '昵称',
    avatar VARCHAR(255) COMMENT '头像URL',
    role VARCHAR(20) DEFAULT 'user' COMMENT '角色：user-普通用户，admin-管理员',
    membership_level INT DEFAULT 0 COMMENT '会员等级：0-免费，1-普通会员，2-高级会员',
    daily_generation_count INT DEFAULT 0 COMMENT '当日生成次数',
    last_generation_date DATETIME COMMENT '最后一次生成日期',
    total_novel_count INT DEFAULT 0 COMMENT '累计创建小说数量',
    status INT DEFAULT 1 COMMENT '状态：0-禁用，1-正常',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 2. 小说基础信息表 (novel_base)
CREATE TABLE IF NOT EXISTS novel_base (
    novel_id VARCHAR(36) PRIMARY KEY COMMENT '小说ID，UUID',
    novel_name VARCHAR(100) NOT NULL COMMENT '小说名称',
    user_id VARCHAR(36) NOT NULL COMMENT '所属用户ID',
    genre VARCHAR(50) NOT NULL COMMENT '小说题材（如：玄幻、言情、悬疑等）',
    novel_type JSON COMMENT '小说类型标签，如["爽文","穿越","重生"]',
    tone JSON COMMENT '故事基调，如["轻松","温馨","搞笑"]',
    taboo_list JSON COMMENT '禁止雷点，如["NTR","虐主","悲剧"]',
    total_outline TEXT COMMENT '小说总纲，包含主线剧情、卷划分、高潮节点、结局等',
    world_setting TEXT COMMENT '世界观设定，包含力量体系、地理环境、社会势力等',
    word_count INT DEFAULT 0 COMMENT '总字数',
    chapter_count INT DEFAULT 0 COMMENT '总章节数',
    status INT DEFAULT 0 COMMENT '状态：0-草稿，1-连载中，2-已完结',
    is_deleted BOOLEAN DEFAULT FALSE COMMENT '是否已删除（软删除）',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    FOREIGN KEY (user_id) REFERENCES sys_user(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='小说基础信息表';

-- 3. 小说卷/弧线表 (novel_arc)
CREATE TABLE IF NOT EXISTS novel_arc (
    arc_id VARCHAR(36) PRIMARY KEY COMMENT '弧线ID，UUID',
    novel_id VARCHAR(36) NOT NULL COMMENT '所属小说ID',
    arc_name VARCHAR(100) NOT NULL COMMENT '卷名称',
    arc_outline TEXT COMMENT '卷大纲内容',
    chapter_count INT DEFAULT 0 COMMENT '本卷章节数量',
    start_chapter INT COMMENT '本卷起始章节号',
    end_chapter INT COMMENT '本卷结束章节号',
    rhythm_tag VARCHAR(20) COMMENT '节奏标签：quest-主线/fire-感情线/constellation-世界观线',
    rhythm_intensity INT DEFAULT 5 COMMENT '节奏强度（1-10）',
    FOREIGN KEY (novel_id) REFERENCES novel_base(novel_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='小说卷/弧线表';

-- 4. 章节表 (novel_chapter)
CREATE TABLE IF NOT EXISTS novel_chapter (
    chapter_id VARCHAR(36) PRIMARY KEY COMMENT '章节ID，UUID',
    novel_id VARCHAR(36) NOT NULL COMMENT '所属小说ID',
    arc_id VARCHAR(36) COMMENT '所属卷/弧线ID',
    chapter_num INT NOT NULL COMMENT '章节序号',
    chapter_title VARCHAR(100) NOT NULL COMMENT '章节标题',
    chapter_outline TEXT COMMENT '章节大纲/章纲内容',
    implementation_idea TEXT COMMENT '实现思路（AI生成的写作方向）',
    content LONGTEXT COMMENT '章节正文内容',
    word_count INT DEFAULT 0 COMMENT '章节字数',
    rhythm_tag VARCHAR(20) COMMENT '节奏标签：quest-主线/fire-感情线/constellation-世界观线',
    rhythm_intensity INT DEFAULT 5 COMMENT '节奏强度（1-10）',
    plot_fingerprint TEXT COMMENT '剧情指纹（300字摘要，用于RAG检索）',
    user_intent TEXT COMMENT '用户意图/写作要求',
    generation_status INT DEFAULT 0 COMMENT '生成状态：0-待生成, 1-生成中, 2-已生成, 3-生成失败',
    is_deleted BOOLEAN DEFAULT FALSE COMMENT '是否在回收站（软删除标记）',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    FOREIGN KEY (novel_id) REFERENCES novel_base(novel_id) ON DELETE CASCADE,
    FOREIGN KEY (arc_id) REFERENCES novel_arc(arc_id) ON DELETE SET NULL,
    UNIQUE KEY uk_novel_chapter (novel_id, chapter_num)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='章节表';

-- 5. 章节历史版本表 (novel_chapter_version)
CREATE TABLE IF NOT EXISTS novel_chapter_version (
    version_id VARCHAR(36) PRIMARY KEY COMMENT '版本ID，UUID',
    chapter_id VARCHAR(36) NOT NULL COMMENT '所属章节ID',
    novel_id VARCHAR(36) NOT NULL COMMENT '所属小说ID',
    chapter_num INT NOT NULL COMMENT '章节序号',
    chapter_title VARCHAR(100) NOT NULL COMMENT '章节标题快照',
    content LONGTEXT COMMENT '章节正文内容快照',
    word_count INT DEFAULT 0 COMMENT '版本字数',
    version_desc VARCHAR(200) COMMENT '版本描述（如：手动修改前保存）',
    create_time DATETIME NOT NULL COMMENT '版本创建时间',
    FOREIGN KEY (chapter_id) REFERENCES novel_chapter(chapter_id) ON DELETE CASCADE,
    FOREIGN KEY (novel_id) REFERENCES novel_base(novel_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='章节历史版本表';

-- 6. 伏笔账本表 (clue_book)
CREATE TABLE IF NOT EXISTS clue_book (
    clue_id VARCHAR(36) PRIMARY KEY COMMENT '伏笔ID，UUID',
    novel_id VARCHAR(36) NOT NULL COMMENT '所属小说ID',
    clue_content TEXT NOT NULL COMMENT '伏笔内容描述',
    priority INT DEFAULT 2 COMMENT '优先级：1-高，2-中，3-低',
    bury_chapter INT COMMENT '埋设章节号',
    recycle_chapter INT COMMENT '回收章节号（null表示未回收）',
    status INT DEFAULT 0 COMMENT '状态：0-未回收，1-已回收',
    related_characters VARCHAR(255) COMMENT '关联人物ID，逗号分隔',
    related_locations VARCHAR(255) COMMENT '关联地点ID，逗号分隔',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    FOREIGN KEY (novel_id) REFERENCES novel_base(novel_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='伏笔账本表';

-- 7. 世界状态时钟表 (world_clock)
CREATE TABLE IF NOT EXISTS world_clock (
    clock_id VARCHAR(36) PRIMARY KEY COMMENT '时钟ID，UUID',
    novel_id VARCHAR(36) NOT NULL COMMENT '所属小说ID',
    `current_time` VARCHAR(50) COMMENT '当前时间描述（如：子时、黄昏）',
    `current_date` VARCHAR(100) COMMENT '当前日期描述（如：天元三年五月初五）',
    season VARCHAR(20) COMMENT '当前季节',
    weather VARCHAR(20) COMMENT '当前天气',
    chapter_day INT DEFAULT 0 COMMENT '当前章节对应的天数',
    total_days INT DEFAULT 0 COMMENT '小说世界总天数',
    update_chapter INT DEFAULT 0 COMMENT '最后更新该时钟的章节号',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    FOREIGN KEY (novel_id) REFERENCES novel_base(novel_id) ON DELETE CASCADE,
    UNIQUE KEY uk_novel_clock (novel_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='世界状态时钟表';

-- 8. 模型配置表 (llm_config)
CREATE TABLE IF NOT EXISTS llm_config (
    config_id VARCHAR(36) PRIMARY KEY COMMENT '配置ID，UUID',
    config_name VARCHAR(100) NOT NULL COMMENT '配置名称（如：GPT-4写作模型）',
    api_key VARCHAR(255) NOT NULL COMMENT 'API密钥',
    base_url VARCHAR(255) NOT NULL COMMENT 'API基础地址（如：https://api.openai.com/v1）',
    model_name VARCHAR(100) NOT NULL COMMENT '模型名称（如：gpt-4、claude-3-opus等）',
    model_type VARCHAR(50) DEFAULT 'openai' COMMENT '模型类型：openai/claude/qwen等',
    api_endpoint VARCHAR(255) COMMENT 'API端点路径',
    auth_type VARCHAR(20) DEFAULT 'bearer' COMMENT '认证类型：bearer/api_key/basic/anthropic',
    max_tokens INT DEFAULT 4096 COMMENT '最大生成Token数',
    temperature DOUBLE DEFAULT 0.7 COMMENT '生成温度（0.0-2.0，越高越随机）',
    usage_type VARCHAR(20) DEFAULT 'all' COMMENT '用途：outline-大纲/writing-正文/summary-摘要/audit-校验/all-通用',
    is_active INT DEFAULT 1 COMMENT '是否激活：0-禁用，1-激活',
    user_id VARCHAR(36) NOT NULL COMMENT '所属用户ID',
    description TEXT COMMENT '配置描述',
    embedding_model VARCHAR(100) COMMENT 'Embedding模型名称（如：text-embedding-ada-002）',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    FOREIGN KEY (user_id) REFERENCES sys_user(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型配置表';

-- 9. 题材模板表 (genre_template)
CREATE TABLE IF NOT EXISTS genre_template (
    template_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '模板ID，自增主键',
    template_name VARCHAR(100) NOT NULL COMMENT '模板名称',
    genre VARCHAR(50) NOT NULL COMMENT '题材编码（如：xuanhuan、dog-blood-romance等）',
    description TEXT COMMENT '题材描述',
    recommended_tags VARCHAR(255) COMMENT '推荐标签，逗号分隔',
    plot_structure TEXT COMMENT '剧情结构模板',
    character_setting TEXT COMMENT '人物设定模板',
    style_features TEXT COMMENT '风格特征描述',
    sample_opening TEXT COMMENT '开篇示例文本',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    status INT DEFAULT 1 COMMENT '状态：0-禁用，1-启用'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题材模板表';

-- 10. Prompt模板配置表 (prompt_template)
CREATE TABLE IF NOT EXISTS prompt_template (
    template_id VARCHAR(36) PRIMARY KEY COMMENT '模板ID，UUID',
    agent_type VARCHAR(50) NOT NULL COMMENT 'Agent类型：outline/writing/summary/audit/ooc_validator',
    template_name VARCHAR(100) NOT NULL COMMENT '模板名称',
    system_instruction TEXT NOT NULL COMMENT '系统指令内容（Prompt正文）',
    few_shot_examples TEXT COMMENT 'JSON格式的few-shot示例',
    version INT DEFAULT 1 COMMENT '模板版本号',
    is_active INT DEFAULT 1 COMMENT '是否激活：0-禁用，1-激活',
    description TEXT COMMENT '模板描述',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Prompt模板配置表';

-- 创建索引
CREATE INDEX idx_novel_base_user_id ON novel_base(user_id);
CREATE INDEX idx_novel_chapter_novel_id ON novel_chapter(novel_id);
CREATE INDEX idx_novel_chapter_arc_id ON novel_chapter(arc_id);
CREATE INDEX idx_novel_arc_novel_id ON novel_arc(novel_id);
CREATE INDEX idx_clue_book_novel_id ON clue_book(novel_id);
CREATE INDEX idx_clue_book_status ON clue_book(status);
CREATE INDEX idx_llm_config_user_id ON llm_config(user_id);
CREATE INDEX idx_llm_config_is_active ON llm_config(is_active);
CREATE INDEX idx_genre_template_genre ON genre_template(genre);
CREATE INDEX idx_genre_template_status ON genre_template(status);
CREATE INDEX idx_prompt_template_agent_type ON prompt_template(agent_type);
CREATE INDEX idx_prompt_template_is_active ON prompt_template(is_active);

-- 插入默认数据

-- 1. 插入默认管理员用户
INSERT INTO sys_user (user_id, username, password, email, nickname, role, status, create_time, update_time)
VALUES (
    UUID(),
    'admin',
    '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW', -- 密码: admin123
    'admin@example.com',
    '系统管理员',
    'admin',
    1,
    NOW(),
    NOW()
);

-- 2. 插入默认题材模板
INSERT INTO genre_template (template_name, genre, description, recommended_tags, status, create_time, update_time)
VALUES 
('玄幻修真', 'xuanhuan', '传统玄幻修真题材，包含修炼体系、门派斗争、仙器法宝等元素', '修真,修仙,玄幻,升级流,打脸', 1, NOW(), NOW()),
('都市言情', 'urban-romance', '现代都市背景的爱情故事，包含职场、家庭、情感纠葛等元素', '都市,言情,现代,职场,甜宠', 1, NOW(), NOW()),
('悬疑推理', 'mystery', '以解谜、推理为核心的故事，包含案件、侦探、线索等元素', '悬疑,推理,破案,烧脑,刑侦', 1, NOW(), NOW()),
('科幻未来', 'sci-fi', '以科学幻想为背景的故事，包含未来科技、太空探索、人工智能等元素', '科幻,未来,太空,科技,赛博朋克', 1, NOW(), NOW());

-- 3. 插入默认Prompt模板
INSERT INTO prompt_template (template_id, agent_type, template_name, system_instruction, is_active, create_time, update_time)
VALUES 
(
    UUID(),
    'outline',
    '默认大纲模板',
    '你是专业的小说大纲规划Agent。你的任务是根据小说总纲和当前状态生成章纲。\n\n===防幻觉三定律===\n【定律一：大纲即法律】必须严格按照总纲和卷纲内容生成，不能擅自发挥\n【定律二：设定即物理】世界观设定不可违反，人物特质固定不变\n【定律三：发明需识别】创造新元素必须标注 [新设定：XXX]\n\n===生成规则===\n1. 首先分析总纲，确定本章应该发生什么剧情\n2. 参考当前卷的剧情规划，确保本章符合卷的发展节奏\n3. 如果总纲明确说明了本章内容，必须严格按照总纲生成\n4. 注意承上启下，与上一章剧情连贯\n5. 合理安排伏笔的埋下和回收',
    1,
    NOW(),
    NOW()
),
(
    UUID(),
    'writing',
    '默认写作模板',
    '你是专业的小说写作Agent。你的任务是根据章纲和上下文生成高质量的小说正文。\n\n===写作要求===\n1. 语言流畅，描写生动，符合小说题材风格\n2. 人物对话自然，符合人物性格设定\n3. 场景描写具体，让读者有代入感\n4. 情节发展合理，与章纲保持一致\n5. 避免重复描写，保持内容新鲜\n6. 控制节奏，张弛有度',
    1,
    NOW(),
    NOW()
),
(
    UUID(),
    'summary',
    '默认摘要模板',
    '你是专业的剧情摘要Agent。你的任务是从章节内容中提取关键信息，生成剧情指纹。\n\n===提取要求===\n1. 提取300字以内的核心剧情摘要\n2. 识别关键人物、地点、事件\n3. 捕捉人物关系变化\n4. 提取重要伏笔和线索\n5. 保持客观中立的叙述风格',
    1,
    NOW(),
    NOW()
);

-- 4. 插入默认LLM配置（仅供测试，生产环境请修改）
INSERT INTO llm_config (config_id, config_name, api_key, base_url, model_name, model_type, is_active, user_id, create_time, update_time)
SELECT 
    UUID(),
    '测试模型配置',
    'sk-test123456',
    'https://api.openai.com/v1',
    'gpt-3.5-turbo',
    'openai',
    1,
    user_id,
    NOW(),
    NOW()
FROM sys_user WHERE username = 'admin';

-- 11. 文风卡片表 (style_card)
CREATE TABLE IF NOT EXISTS style_card (
  card_id         VARCHAR(32) PRIMARY KEY COMMENT '卡片ID',
  name            VARCHAR(100) NOT NULL COMMENT '卡片名称（如：诡秘之主风格）',
  source_novel    VARCHAR(200) COMMENT '来源小说名称',
  author_name     VARCHAR(100) COMMENT '作者名称',
  created_by      VARCHAR(32) NOT NULL COMMENT '创建者用户ID',
  rhythm_rules    JSON COMMENT '节奏规则',
  dialogue_rules  JSON COMMENT '对话规则',
  description_rules JSON COMMENT '描写规则',
  emotion_rules   JSON COMMENT '情绪表达规则',
  structure_rules JSON COMMENT '段落结构规则',
  forbidden_rules JSON COMMENT '禁用词汇和句式',
  preferred_rules JSON COMMENT '偏好词汇和句式',
  style_description TEXT COMMENT '文风的自然语言描述',
  generation_template TEXT COMMENT '生成指令模板',
  samples_json    JSON COMMENT '原文样本片段',
  stats_json      JSON COMMENT '程序化提取的统计数据',
  tags            VARCHAR(500) COMMENT '标签，逗号分隔',
  is_public       BOOLEAN DEFAULT false COMMENT '是否公开',
  usage_count     INT DEFAULT 0 COMMENT '使用次数',
  avg_rating      DECIMAL(2,1) COMMENT '平均评分',
  status          INT DEFAULT 0 COMMENT '状态：0=分析中, 1=可用, 2=分析失败',
  create_time     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX idx_created_by(created_by),
  INDEX idx_status(status),
  INDEX idx_is_public(is_public)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文风卡片表';

-- 12. 文风样本片段表 (style_sample)
CREATE TABLE IF NOT EXISTS style_sample (
  sample_id       VARCHAR(32) PRIMARY KEY COMMENT '样本ID',
  card_id         VARCHAR(32) NOT NULL COMMENT '关联的文风卡片ID',
  sample_type     VARCHAR(50) COMMENT '样本类型：悬疑/对话/战斗/心理/描写',
  content         TEXT NOT NULL COMMENT '样本内容（原文片段）',
  annotation      VARCHAR(500) COMMENT 'AI对这段的风格注释',
  sort_order      INT DEFAULT 0 COMMENT '排序顺序',
  create_time     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  INDEX idx_card(card_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文风样本片段表';

-- 13. 用户文风卡片库 (user_style_library)
CREATE TABLE IF NOT EXISTS user_style_library (
  id              VARCHAR(32) PRIMARY KEY COMMENT '记录ID',
  user_id         VARCHAR(32) NOT NULL COMMENT '用户ID',
  card_id         VARCHAR(32) NOT NULL COMMENT '文风卡片ID',
  is_favorite     BOOLEAN DEFAULT false COMMENT '是否收藏',
  add_time        DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '添加时间',
  UNIQUE INDEX idx_user_card(user_id, card_id),
  INDEX idx_user(user_id),
  INDEX idx_card(card_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户文风卡片库';

-- 14. 小说文风绑定表 (novel_style_binding)
CREATE TABLE IF NOT EXISTS novel_style_binding (
  id              VARCHAR(32) PRIMARY KEY COMMENT '记录ID',
  novel_id        VARCHAR(32) NOT NULL COMMENT '小说ID',
  card_id         VARCHAR(32) NOT NULL COMMENT '文风卡片ID',
  bind_time       DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
  UNIQUE INDEX idx_novel(novel_id),
  INDEX idx_card(card_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='小说文风绑定表';

-- 创建文风相关索引
CREATE INDEX idx_style_card_created_by ON style_card(created_by);
CREATE INDEX idx_style_sample_card_id ON style_sample(card_id);

-- 提交事务
COMMIT;

-- =============================================
-- 增量更新：novel_base 添加类型/基调/雷点字段
-- 如果是已有数据库，请手动执行以下语句
-- =============================================
ALTER TABLE novel_base
    ADD COLUMN novel_type JSON COMMENT '小说类型标签，如["爽文","穿越","重生"]' AFTER genre,
    ADD COLUMN tone JSON COMMENT '故事基调，如["轻松","温馨","搞笑"]' AFTER novel_type,
    ADD COLUMN taboo_list JSON COMMENT '禁止雷点，如["NTR","虐主","悲剧"]' AFTER tone;

-- 显示创建结果
SELECT '数据库初始化完成' AS result;
SELECT table_name, table_comment FROM information_schema.tables WHERE table_schema = 'moyun_novel_system';