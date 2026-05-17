package com.novel.system.mapper;

import com.novel.system.entity.mysql.NovelArc;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface NovelArcMapper {
    NovelArc selectById(@Param("arcId") String arcId);
    List<NovelArc> selectByNovelId(@Param("novelId") String novelId);
    NovelArc findByChapterNum(@Param("novelId") String novelId, @Param("chapterNum") Integer chapterNum);
    int insert(NovelArc arc);
    int insertBatch(@Param("list") List<NovelArc> arcs);
    int updateById(NovelArc arc);
    int deleteById(@Param("arcId") String arcId);
    int deleteByNovelId(@Param("novelId") String novelId);

    int countByNovelId(@Param("novelId") String novelId);

    List<java.util.Map<String, Object>> countByNovelIds(@Param("novelIds") List<String> novelIds);
}
