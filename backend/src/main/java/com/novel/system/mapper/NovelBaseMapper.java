package com.novel.system.mapper;

import com.novel.system.entity.mysql.NovelBase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface NovelBaseMapper {
    NovelBase selectById(@Param("novelId") String novelId);
    List<NovelBase> selectByUserId(@Param("userId") String userId);
    List<NovelBase> selectByStatus(@Param("status") Integer status);
    List<NovelBase> selectActiveByUserId(@Param("userId") String userId);
    long countByUserId(@Param("userId") String userId);
    int insert(NovelBase novel);
    int updateById(NovelBase novel);
    int deleteById(@Param("novelId") String novelId);
    int softDeleteById(@Param("novelId") String novelId);
    int restoreById(@Param("novelId") String novelId);
}
