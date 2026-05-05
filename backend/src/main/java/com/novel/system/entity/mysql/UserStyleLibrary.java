package com.novel.system.entity.mysql;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户文风卡片库实体
 * 
 * 【核心作用】
 * 用户可以收藏自己创建的卡片，也可以收藏别人公开的卡片
 * 
 * 【关联关系】
 * - 一个用户可以有多张卡片
 * - 一张卡片可以被多个用户收藏
 * 
 * @see StyleCard
 */
@Data
@TableName("user_style_library")
public class UserStyleLibrary {
    
    /**
     * 记录ID
     */
    @TableId
    private String id;
    
    /**
     * 用户ID
     */
    private String userId;
    
    /**
     * 文风卡片ID
     */
    private String cardId;
    
    /**
     * 是否收藏
     */
    private Boolean isFavorite;
    
    /**
     * 添加时间
     */
    private LocalDateTime addTime;
}
