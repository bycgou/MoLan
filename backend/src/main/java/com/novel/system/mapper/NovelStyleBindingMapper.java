package com.novel.system.mapper;

import com.novel.system.entity.mysql.NovelStyleBinding;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 小说文风绑定Mapper
 */
@Mapper
public interface NovelStyleBindingMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<NovelStyleBinding> {
    
    /**
     * 根据小说ID查询绑定
     */
    NovelStyleBinding selectByNovelId(@Param("novelId") String novelId);
    
    /**
     * 根据小说ID删除绑定
     */
    int deleteByNovelId(@Param("novelId") String novelId);
    
    /**
     * 根据卡片ID查询绑定数量
     */
    Long countByCardId(@Param("cardId") String cardId);
}
