-- 用户文风卡片库（多对多关系）
-- 用户可以收藏自己创建的卡片，也可以收藏别人公开的卡片
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
