/*
 Navicat Premium Data Transfer

 Source Server         : bycg
 Source Server Type    : MySQL
 Source Server Version : 80042 (8.0.42)
 Source Host           : localhost:3306
 Source Schema         : moyun_novel_system

 Target Server Type    : MySQL
 Target Server Version : 80042 (8.0.42)
 File Encoding         : 65001

 Date: 29/05/2026 10:46:38
*/

CREATE DATABASE IF NOT EXISTS moyun_novel_system DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE moyun_novel_system;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for arc_summaries
-- ----------------------------
DROP TABLE IF EXISTS `arc_summaries`;
CREATE TABLE `arc_summaries`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `novel_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `chapter_from` int NOT NULL,
  `chapter_to` int NOT NULL,
  `summary` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_novel_range`(`novel_id` ASC, `chapter_from` ASC, `chapter_to` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '弧段摘要表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for chapter_synopses
-- ----------------------------
DROP TABLE IF EXISTS `chapter_synopses`;
CREATE TABLE `chapter_synopses`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `chapter_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `novel_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `chapter_num` int NOT NULL,
  `synopsis` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `scene_breakdown` json NULL COMMENT '场景分解',
  `dialogue_points` json NULL COMMENT '对话要点',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_chapter`(`chapter_id` ASC) USING BTREE,
  INDEX `idx_novel_chapter`(`novel_id` ASC, `chapter_num` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '章节简介蓝图表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for chapter_versions
-- ----------------------------
DROP TABLE IF EXISTS `chapter_versions`;
CREATE TABLE `chapter_versions`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `chapter_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `version` int NOT NULL,
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `outline` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `words` int NULL DEFAULT 0,
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_chapter_version`(`chapter_id` ASC, `version` DESC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '章节版本快照表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for clue_book
-- ----------------------------
DROP TABLE IF EXISTS `clue_book`;
CREATE TABLE `clue_book`  (
  `clue_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '伏笔ID，UUID',
  `novel_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '所属小说ID',
  `clue_content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '伏笔内容描述',
  `priority` int NULL DEFAULT 2 COMMENT '优先级：1-高，2-中，3-低',
  `bury_chapter` int NULL DEFAULT NULL COMMENT '埋设章节号',
  `recycle_chapter` int NULL DEFAULT NULL COMMENT '回收章节号（null表示未回收）',
  `status` int NULL DEFAULT 0 COMMENT '状态：0-未回收，1-已回收',
  `related_characters` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '关联人物ID，逗号分隔',
  `related_locations` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '关联地点ID，逗号分隔',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime NOT NULL COMMENT '更新时间',
  `deadline` int NULL DEFAULT NULL COMMENT '伏笔回收截止章节',
  PRIMARY KEY (`clue_id`) USING BTREE,
  INDEX `idx_clue_book_novel_id`(`novel_id` ASC) USING BTREE,
  INDEX `idx_clue_book_status`(`status` ASC) USING BTREE,
  CONSTRAINT `clue_book_ibfk_1` FOREIGN KEY (`novel_id`) REFERENCES `novel_base` (`novel_id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '伏笔账本表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for genre_template
-- ----------------------------
DROP TABLE IF EXISTS `genre_template`;
CREATE TABLE `genre_template`  (
  `template_id` bigint NOT NULL AUTO_INCREMENT COMMENT '模板ID，自增主键',
  `template_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '模板名称',
  `genre` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '题材编码（如：xuanhuan、dog-blood-romance等）',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '题材描述',
  `recommended_tags` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '推荐标签，逗号分隔',
  `plot_structure` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '剧情结构模板',
  `character_setting` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '人物设定模板',
  `style_features` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '风格特征描述',
  `sample_opening` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '开篇示例文本',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime NOT NULL COMMENT '更新时间',
  `status` int NULL DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
  PRIMARY KEY (`template_id`) USING BTREE,
  INDEX `idx_genre_template_genre`(`genre` ASC) USING BTREE,
  INDEX `idx_genre_template_status`(`status` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '题材模板表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for llm_config
-- ----------------------------
DROP TABLE IF EXISTS `llm_config`;
CREATE TABLE `llm_config`  (
  `config_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '配置ID，UUID',
  `config_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '配置名称（如：GPT-4写作模型）',
  `api_key` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'API密钥',
  `base_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'API基础地址（如：https://api.openai.com/v1）',
  `model_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '模型名称（如：gpt-4、claude-3-opus等）',
  `model_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT 'openai' COMMENT '模型类型：openai/claude/qwen等',
  `api_endpoint` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'API端点路径',
  `auth_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT 'bearer' COMMENT '认证类型：bearer/api_key/basic/anthropic',
  `max_tokens` int NULL DEFAULT 4096 COMMENT '最大生成Token数',
  `temperature` double NULL DEFAULT 0.7 COMMENT '生成温度（0.0-2.0，越高越随机）',
  `usage_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT 'all' COMMENT '用途：outline-大纲/writing-正文/summary-摘要/audit-校验/all-通用',
  `is_active` int NULL DEFAULT 1 COMMENT '是否激活：0-禁用，1-激活',
  `user_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '所属用户ID',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '配置描述',
  `embedding_model` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'Embedding模型名称（如：text-embedding-ada-002）',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`config_id`) USING BTREE,
  INDEX `idx_llm_config_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_llm_config_is_active`(`is_active` ASC) USING BTREE,
  CONSTRAINT `llm_config_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`user_id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '模型配置表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for novel_arc
-- ----------------------------
DROP TABLE IF EXISTS `novel_arc`;
CREATE TABLE `novel_arc`  (
  `arc_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '弧线ID，UUID',
  `novel_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '所属小说ID',
  `arc_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '卷名称',
  `arc_outline` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '卷大纲内容',
  `chapter_count` int NULL DEFAULT 0 COMMENT '本卷章节数量',
  `start_chapter` int NULL DEFAULT NULL COMMENT '本卷起始章节号',
  `end_chapter` int NULL DEFAULT NULL COMMENT '本卷结束章节号',
  `rhythm_tag` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '节奏标签：quest-主线/fire-感情线/constellation-世界观线',
  `rhythm_intensity` int NULL DEFAULT 5 COMMENT '节奏强度（1-10）',
  `chapter_plan` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '章节级编排计划JSON',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`arc_id`) USING BTREE,
  INDEX `idx_novel_arc_novel_id`(`novel_id` ASC) USING BTREE,
  CONSTRAINT `novel_arc_ibfk_1` FOREIGN KEY (`novel_id`) REFERENCES `novel_base` (`novel_id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '小说卷/弧线表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for novel_base
-- ----------------------------
DROP TABLE IF EXISTS `novel_base`;
CREATE TABLE `novel_base`  (
  `novel_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '小说ID，UUID',
  `novel_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '小说名称',
  `user_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '所属用户ID',
  `genre` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '小说题材（如：玄幻、言情、悬疑等）',
  `novel_type` json NULL COMMENT '小说类型标签，如[\"爽文\",\"穿越\",\"重生\"]',
  `tone` json NULL COMMENT '故事基调，如[\"轻松\",\"温馨\",\"搞笑\"]',
  `taboo_list` json NULL COMMENT '禁止雷点，如[\"NTR\",\"虐主\",\"悲剧\"]',
  `total_outline` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '小说总纲，包含主线剧情、卷划分、高潮节点、结局等',
  `world_setting` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '世界观设定，包含力量体系、地理环境、社会势力等',
  `llm_config_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '用户选择的LLM配置ID',
  `word_count` int NULL DEFAULT 0 COMMENT '总字数',
  `chapter_count` int NULL DEFAULT 0 COMMENT '总章节数',
  `status` int NULL DEFAULT 0 COMMENT '状态：0-草稿，1-连载中，2-已完结',
  `creation_mode` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT 'classic',
  `is_deleted` tinyint(1) NULL DEFAULT 0 COMMENT '是否已删除（软删除）',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime NOT NULL COMMENT '更新时间',
  `story_momentum` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '故事势能摘要',
  `pending_foreshadowing` json NULL COMMENT '待回收伏笔追踪',
  PRIMARY KEY (`novel_id`) USING BTREE,
  INDEX `idx_novel_base_user_id`(`user_id` ASC) USING BTREE,
  CONSTRAINT `novel_base_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`user_id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '小说基础信息表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for novel_chapter
-- ----------------------------
DROP TABLE IF EXISTS `novel_chapter`;
CREATE TABLE `novel_chapter`  (
  `chapter_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '章节ID，UUID',
  `novel_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '所属小说ID',
  `arc_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '所属卷/弧线ID',
  `chapter_num` int NOT NULL COMMENT '章节序号',
  `chapter_title` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '章节标题',
  `chapter_outline` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '章节大纲/章纲内容',
  `implementation_idea` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '实现思路（AI生成的写作方向）',
  `content` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '章节正文内容',
  `word_count` int NULL DEFAULT 0 COMMENT '章节字数',
  `rhythm_tag` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '节奏标签：quest-主线/fire-感情线/constellation-世界观线',
  `rhythm_intensity` int NULL DEFAULT 5 COMMENT '节奏强度（1-10）',
  `plot_fingerprint` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '剧情指纹（300字摘要，用于RAG检索）',
  `user_intent` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '用户意图/写作要求',
  `generation_status` int NULL DEFAULT 0 COMMENT '生成状态：0-待生成, 1-生成中, 2-已生成, 3-生成失败',
  `is_deleted` tinyint(1) NULL DEFAULT 0 COMMENT '是否在回收站（软删除标记）',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime NOT NULL COMMENT '更新时间',
  `hook` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '结尾钩子',
  `actual_ending_hook` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '正文实际结尾钩子，用于下一章承接',
  `key_points` json NULL COMMENT '关键情节点',
  `chapter_summary` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '章节摘要(冗余存储，加速查询)',
  `used_tropes` json NULL COMMENT '本章使用的意象/场景',
  `synopsis_id` bigint NULL DEFAULT NULL COMMENT '关联的章节简介ID',
  PRIMARY KEY (`chapter_id`) USING BTREE,
  UNIQUE INDEX `uk_novel_chapter`(`novel_id` ASC, `chapter_num` ASC) USING BTREE,
  INDEX `idx_novel_chapter_novel_id`(`novel_id` ASC) USING BTREE,
  INDEX `idx_novel_chapter_arc_id`(`arc_id` ASC) USING BTREE,
  CONSTRAINT `novel_chapter_ibfk_1` FOREIGN KEY (`novel_id`) REFERENCES `novel_base` (`novel_id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `novel_chapter_ibfk_2` FOREIGN KEY (`arc_id`) REFERENCES `novel_arc` (`arc_id`) ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '章节表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for novel_chapter_version
-- ----------------------------
DROP TABLE IF EXISTS `novel_chapter_version`;
CREATE TABLE `novel_chapter_version`  (
  `version_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '版本ID，UUID',
  `chapter_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '所属章节ID',
  `novel_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '所属小说ID',
  `chapter_num` int NOT NULL COMMENT '章节序号',
  `chapter_title` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '章节标题快照',
  `content` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '章节正文内容快照',
  `word_count` int NULL DEFAULT 0 COMMENT '版本字数',
  `version_desc` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '版本描述（如：手动修改前保存）',
  `create_time` datetime NOT NULL COMMENT '版本创建时间',
  PRIMARY KEY (`version_id`) USING BTREE,
  INDEX `chapter_id`(`chapter_id` ASC) USING BTREE,
  INDEX `novel_id`(`novel_id` ASC) USING BTREE,
  CONSTRAINT `novel_chapter_version_ibfk_1` FOREIGN KEY (`chapter_id`) REFERENCES `novel_chapter` (`chapter_id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `novel_chapter_version_ibfk_2` FOREIGN KEY (`novel_id`) REFERENCES `novel_base` (`novel_id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '章节历史版本表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for novel_style_binding
-- ----------------------------
DROP TABLE IF EXISTS `novel_style_binding`;
CREATE TABLE `novel_style_binding`  (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '记录ID',
  `novel_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '小说ID',
  `card_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文风卡片ID',
  `bind_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `idx_novel`(`novel_id` ASC) USING BTREE,
  INDEX `idx_card`(`card_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '小说文风绑定表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for prompt_template
-- ----------------------------
DROP TABLE IF EXISTS `prompt_template`;
CREATE TABLE `prompt_template`  (
  `template_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '模板ID，UUID',
  `agent_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Agent类型：outline/writing/summary/audit/ooc_validator',
  `template_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '模板名称',
  `system_instruction` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '系统指令内容（Prompt正文）',
  `few_shot_examples` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT 'JSON格式的few-shot示例',
  `version` int NULL DEFAULT 1 COMMENT '模板版本号',
  `is_active` int NULL DEFAULT 1 COMMENT '是否激活：0-禁用，1-激活',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '模板描述',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`template_id`) USING BTREE,
  INDEX `idx_prompt_template_agent_type`(`agent_type` ASC) USING BTREE,
  INDEX `idx_prompt_template_is_active`(`is_active` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Prompt模板配置表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for style_card
-- ----------------------------
DROP TABLE IF EXISTS `style_card`;
CREATE TABLE `style_card`  (
  `card_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '卡片ID',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '卡片名称（如：诡秘之主风格）',
  `source_novel` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '来源小说名称',
  `author_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '作者名称',
  `created_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '创建者用户ID',
  `rhythm_rules` json NULL COMMENT '节奏规则：{avg_sentence_length, short_sentence_ratio, ...}',
  `dialogue_rules` json NULL COMMENT '对话规则：{ratio, avg_line_length, style, quirks}',
  `description_rules` json NULL COMMENT '描写规则：{dominant_sense, sense_distribution, metaphor_type}',
  `emotion_rules` json NULL COMMENT '情绪表达规则：{anger, fear, joy, sadness, forbidden}',
  `structure_rules` json NULL COMMENT '段落结构规则：{scene_transition, chapter_ending, pov}',
  `forbidden_rules` json NULL COMMENT '禁用词汇和句式：{words: [...], structures: [...]}',
  `preferred_rules` json NULL COMMENT '偏好词汇和句式：{words: [...], structures: [...]}',
  `style_description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '文风的自然语言描述',
  `generation_template` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '生成指令模板',
  `samples_json` json NULL COMMENT '原文样本片段：[{type, text, annotation}]',
  `stats_json` json NULL COMMENT '程序化提取的统计数据：{avg_sentence_length, dialogue_ratio, ...}',
  `tags` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '标签，逗号分隔',
  `is_public` tinyint(1) NULL DEFAULT 0 COMMENT '是否公开',
  `usage_count` int NULL DEFAULT 0 COMMENT '使用次数',
  `avg_rating` decimal(2, 1) NULL DEFAULT NULL COMMENT '平均评分',
  `status` int NULL DEFAULT 0 COMMENT '状态：0=分析中, 1=可用, 2=分析失败',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`card_id`) USING BTREE,
  INDEX `idx_created_by`(`created_by` ASC) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE,
  INDEX `idx_is_public`(`is_public` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '文风卡片表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for style_sample
-- ----------------------------
DROP TABLE IF EXISTS `style_sample`;
CREATE TABLE `style_sample`  (
  `sample_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '样本ID',
  `card_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '关联的文风卡片ID',
  `sample_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '样本类型：悬疑/对话/战斗/心理/描写',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '样本内容（原文片段）',
  `annotation` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'AI对这段的风格注释',
  `sort_order` int NULL DEFAULT 0 COMMENT '排序顺序',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`sample_id`) USING BTREE,
  INDEX `idx_card`(`card_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '文风样本片段表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sys_user
-- ----------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user`  (
  `user_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '用户ID，UUID',
  `username` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '用户名',
  `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '密码（BCrypt加密）',
  `email` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '邮箱',
  `nickname` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '昵称',
  `avatar` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '头像URL',
  `role` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT 'user' COMMENT '角色：user-普通用户，admin-管理员',
  `membership_level` int NULL DEFAULT 0 COMMENT '会员等级：0-免费，1-普通会员，2-高级会员',
  `daily_generation_count` int NULL DEFAULT 0 COMMENT '当日生成次数',
  `last_generation_date` datetime NULL DEFAULT NULL COMMENT '最后一次生成日期',
  `total_novel_count` int NULL DEFAULT 0 COMMENT '累计创建小说数量',
  `status` int NULL DEFAULT 1 COMMENT '状态：0-禁用，1-正常',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`user_id`) USING BTREE,
  UNIQUE INDEX `username`(`username` ASC) USING BTREE,
  UNIQUE INDEX `email`(`email` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for user_style_library
-- ----------------------------
DROP TABLE IF EXISTS `user_style_library`;
CREATE TABLE `user_style_library`  (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '记录ID',
  `user_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户ID',
  `card_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文风卡片ID',
  `is_favorite` tinyint(1) NULL DEFAULT 0 COMMENT '是否收藏',
  `add_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '添加时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `idx_user_card`(`user_id` ASC, `card_id` ASC) USING BTREE,
  INDEX `idx_user`(`user_id` ASC) USING BTREE,
  INDEX `idx_card`(`card_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户文风卡片库' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for world_clock
-- ----------------------------
DROP TABLE IF EXISTS `world_clock`;
CREATE TABLE `world_clock`  (
  `clock_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '时钟ID，UUID',
  `novel_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '所属小说ID',
  `current_time` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '当前时间描述（如：子时、黄昏）',
  `current_date` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '当前日期描述（如：天元三年五月初五）',
  `season` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '当前季节',
  `weather` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '当前天气',
  `chapter_day` int NULL DEFAULT 0 COMMENT '当前章节对应的天数',
  `total_days` int NULL DEFAULT 0 COMMENT '小说世界总天数',
  `update_chapter` int NULL DEFAULT 0 COMMENT '最后更新该时钟的章节号',
  `update_time` datetime NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`clock_id`) USING BTREE,
  UNIQUE INDEX `uk_novel_clock`(`novel_id` ASC) USING BTREE,
  CONSTRAINT `world_clock_ibfk_1` FOREIGN KEY (`novel_id`) REFERENCES `novel_base` (`novel_id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '世界状态时钟表' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
