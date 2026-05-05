package com.novel.system.mapper;

import com.novel.system.entity.mysql.UserStyleLibrary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户文风卡片库Mapper
 */
@Mapper
public interface UserStyleLibraryMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<UserStyleLibrary> {
    
    /**
     * 根据用户ID查询卡片列表
     */
    List<UserStyleLibrary> selectByUserId(@Param("userId") String userId);
    
    /**
     * 根据用户ID和卡片ID查询
     */
    UserStyleLibrary selectByUserIdAndCardId(@Param("userId") String userId, @Param("cardId") String cardId);
    
    /**
     * 根据用户ID和卡片ID删除
     */
    int deleteByUserIdAndCardId(@Param("userId") String userId, @Param("cardId") String cardId);
}
