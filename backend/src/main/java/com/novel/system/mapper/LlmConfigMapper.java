package com.novel.system.mapper;

import com.novel.system.entity.mysql.LlmConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface LlmConfigMapper {
    LlmConfig selectById(@Param("configId") String configId);
    List<LlmConfig> selectByUserId(@Param("userId") String userId);
    List<LlmConfig> selectByUserIdAndIsActive(@Param("userId") String userId, @Param("isActive") Integer isActive);
    LlmConfig selectActiveByUsageType(@Param("userId") String userId, @Param("usageType") String usageType);
    int insert(LlmConfig config);
    int updateById(LlmConfig config);
    int deleteById(@Param("configId") String configId);
    
    /** 查询所有激活的配置（任意用户，用于兜底） */
    List<LlmConfig> selectAllActive();
    
    /** 查询指定用途的激活配置（任意用户，用于兜底） */
    LlmConfig selectActiveByUsageTypeGlobal(@Param("usageType") String usageType);
}
