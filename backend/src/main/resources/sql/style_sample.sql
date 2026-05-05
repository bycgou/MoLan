-- 原文样本片段表
-- 存储从上传小说中抽取的代表性段落，用于few-shot学习
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
