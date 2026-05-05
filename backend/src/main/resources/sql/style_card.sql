-- 文风卡片主表
-- 存储从上传小说中提取的文风特征，可复用
CREATE TABLE IF NOT EXISTS style_card (
  card_id         VARCHAR(32) PRIMARY KEY COMMENT '卡片ID',
  name            VARCHAR(100) NOT NULL COMMENT '卡片名称（如：诡秘之主风格）',
  source_novel    VARCHAR(200) COMMENT '来源小说名称',
  author_name     VARCHAR(100) COMMENT '作者名称',
  created_by      VARCHAR(32) NOT NULL COMMENT '创建者用户ID',
  
  -- 结构化规则（JSON格式存储）
  rhythm_rules    JSON COMMENT '节奏规则：{avg_sentence_length, short_sentence_ratio, ...}',
  dialogue_rules  JSON COMMENT '对话规则：{ratio, avg_line_length, style, quirks}',
  description_rules JSON COMMENT '描写规则：{dominant_sense, sense_distribution, metaphor_type}',
  emotion_rules   JSON COMMENT '情绪表达规则：{anger, fear, joy, sadness, forbidden}',
  structure_rules JSON COMMENT '段落结构规则：{scene_transition, chapter_ending, pov}',
  forbidden_rules JSON COMMENT '禁用词汇和句式：{words: [...], structures: [...]}',
  preferred_rules JSON COMMENT '偏好词汇和句式：{words: [...], structures: [...]}',
  
  -- 自然语言描述（用于prompt注入）
  style_description TEXT COMMENT '文风的自然语言描述',
  generation_template TEXT COMMENT '生成指令模板',
  
  -- 原文样本（few-shot示例）
  samples_json    JSON COMMENT '原文样本片段：[{type, text, annotation}]',
  
  -- 程序化统计数据
  stats_json      JSON COMMENT '程序化提取的统计数据：{avg_sentence_length, dialogue_ratio, ...}',
  
  -- 元信息
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
