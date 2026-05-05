package com.novel.system.mapper;

import com.novel.system.entity.mysql.StyleCard;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 文风卡片Mapper
 */
@Mapper
public interface StyleCardMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<StyleCard> {
    
    /**
     * 根据创建者查询卡片列表
     */
    List<StyleCard> selectByCreatedBy(@Param("userId") String userId);
    
    /**
     * 查询公开的卡片列表
     */
    List<StyleCard> selectPublicCards(@Param("limit") Integer limit, @Param("offset") Integer offset);
    
    /**
     * 根据标签搜索
     */
    List<StyleCard> searchByTag(@Param("tag") String tag);
}
