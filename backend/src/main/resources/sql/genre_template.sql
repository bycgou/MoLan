CREATE TABLE `genre_template` (
  `template_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '模板ID',
  `template_name` varchar(255) NOT NULL COMMENT '模板名称',
  `genre` varchar(50) NOT NULL COMMENT '题材类型',
  `description` text COMMENT '模板描述',
  `recommended_tags` varchar(500) COMMENT '推荐标签',
  `plot_structure` text COMMENT '情节结构建议',
  `character_setting` text COMMENT '人物设定建议',
  `style_features` text COMMENT '风格特点',
  `sample_opening` text COMMENT '示例开头',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime NOT NULL COMMENT '更新时间',
  `status` int(11) NOT NULL DEFAULT '1' COMMENT '状态：1-启用，0-禁用',
  PRIMARY KEY (`template_id`),
  KEY `idx_genre` (`genre`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题材模板表';