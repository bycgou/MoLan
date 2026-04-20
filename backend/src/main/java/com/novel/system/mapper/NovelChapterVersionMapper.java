package com.novel.system.mapper;

import com.novel.system.entity.mysql.NovelChapterVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface NovelChapterVersionMapper {
    List<NovelChapterVersion> selectByChapterId(@Param("chapterId") String chapterId);
    NovelChapterVersion selectById(@Param("versionId") String versionId);
    int insert(NovelChapterVersion version);
}
