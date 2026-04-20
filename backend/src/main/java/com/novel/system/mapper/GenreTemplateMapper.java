package com.novel.system.mapper;

import com.novel.system.entity.mysql.GenreTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface GenreTemplateMapper {
    List<GenreTemplate> selectAll();
    List<GenreTemplate> selectByGenre(@Param("genre") String genre);
    GenreTemplate selectById(@Param("templateId") Long templateId);
    int insert(GenreTemplate template);
    int updateById(GenreTemplate template);
    int deleteById(@Param("templateId") Long templateId);
}
