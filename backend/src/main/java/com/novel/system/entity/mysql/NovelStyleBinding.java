package com.novel.system.entity.mysql;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 小说文风绑定实体
 * 
 * 【核心作用】
 * 每本小说可以绑定一个文风卡片，生成时自动应用该风格
 * 
 * 【关联关系】
 * - 一本小说只能绑定一张文风卡片（一对一）
 * - 一张文风卡片可以被多本小说绑定（一对多）
 * 
 * @see StyleCard
 */
@Data
@TableName("novel_style_binding")
public class NovelStyleBinding {
    
    /**
     * 记录ID
     */
    @TableId
    private String id;
    
    /**
     * 小说ID
     */
    private String novelId;
    
    /**
     * 文风卡片ID
     */
    private String cardId;
    
    /**
     * 绑定时间
     */
    private LocalDateTime bindTime;
}
