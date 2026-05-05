package com.novel.system.mapper;

import com.novel.system.entity.mysql.StyleSample;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 文风样本Mapper
 */
@Mapper
public interface StyleSampleMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<StyleSample> {
    
    /**
     * 根据卡片ID查询样本列表
     */
    List<StyleSample> selectByCardId(@Param("cardId") String cardId);
    
    /**
     * 根据卡片ID删除样本
     */
    int deleteByCardId(@Param("cardId") String cardId);
}
