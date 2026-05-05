-- 小说绑定的文风卡片（一对一关系）
-- 每本小说可以绑定一个文风卡片，生成时自动应用该风格
CREATE TABLE IF NOT EXISTS novel_style_binding (
  id              VARCHAR(32) PRIMARY KEY COMMENT '记录ID',
  novel_id        VARCHAR(32) NOT NULL COMMENT '小说ID',
  card_id         VARCHAR(32) NOT NULL COMMENT '文风卡片ID',
  bind_time       DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
  
  UNIQUE INDEX idx_novel(novel_id),
  INDEX idx_card(card_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='小说文风绑定表';
