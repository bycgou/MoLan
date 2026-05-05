-- Super-Ma → MoYun 移植 - 阶段一：数据库迁移
-- 创建日期: 2026-04-20

-- 1. 创建弧段摘要表
CREATE TABLE IF NOT EXISTS arc_summaries (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    novel_id VARCHAR(64) NOT NULL,
    chapter_from INT NOT NULL,
    chapter_to INT NOT NULL,
    summary TEXT NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_novel_range (novel_id, chapter_from, chapter_to)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='弧段摘要表';

-- 2. 创建章节版本快照表
CREATE TABLE IF NOT EXISTS chapter_versions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    chapter_id VARCHAR(64) NOT NULL,
    version INT NOT NULL,
    content TEXT NOT NULL,
    outline TEXT,
    title VARCHAR(200),
    words INT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_chapter_version (chapter_id, version DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='章节版本快照表';

-- 3. 创建章节简介蓝图表
CREATE TABLE IF NOT EXISTS chapter_synopses (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    chapter_id VARCHAR(64) NOT NULL,
    novel_id VARCHAR(64) NOT NULL,
    chapter_num INT NOT NULL,
    synopsis TEXT NOT NULL,
    scene_breakdown JSON COMMENT '场景分解',
    dialogue_points JSON COMMENT '对话要点',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_chapter (chapter_id),
    INDEX idx_novel_chapter (novel_id, chapter_num)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='章节简介蓝图表';

-- 4. 为 novel_base 表新增字段
ALTER TABLE novel_base 
ADD COLUMN story_momentum VARCHAR(500) COMMENT '故事势能摘要',
ADD COLUMN pending_foreshadowing JSON COMMENT '待回收伏笔追踪';

-- 5. 为 novel_chapter 表新增字段
ALTER TABLE novel_chapter 
ADD COLUMN hook VARCHAR(500) COMMENT '结尾钩子',
ADD COLUMN key_points JSON COMMENT '关键情节点',
ADD COLUMN chapter_summary TEXT COMMENT '章节摘要(冗余存储，加速查询)',
ADD COLUMN used_tropes JSON COMMENT '本章使用的意象/场景',
ADD COLUMN synopsis_id BIGINT COMMENT '关联的章节简介ID';

-- 6. 为 clue_book 表新增 deadline 字段
ALTER TABLE clue_book 
ADD COLUMN deadline INT COMMENT '伏笔回收截止章节';

-- 7. 更新 prompt_templates 表的模板内容
-- 注意：这里只是示例，实际更新需要根据现有数据进行
UPDATE prompt_templates SET prompt_content = '新的章纲生成Prompt模板' WHERE template_type = 'outline';
UPDATE prompt_templates SET prompt_content = '新的章节写作Prompt模板' WHERE template_type = 'writing';
UPDATE prompt_templates SET prompt_content = '新的摘要生成Prompt模板' WHERE template_type = 'summary';