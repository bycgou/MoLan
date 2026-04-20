package com.novel.system.mapper;

import com.novel.system.entity.mysql.NovelChapter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface NovelChapterMapper {
    NovelChapter selectById(@Param("chapterId") String chapterId);
    List<NovelChapter> selectByNovelId(@Param("novelId") String novelId);
    List<NovelChapter> selectByNovelIdDesc(@Param("novelId") String novelId);
    List<NovelChapter> selectDeletedByNovelId(@Param("novelId") String novelId);
    NovelChapter selectByNovelIdAndChapterNum(@Param("novelId") String novelId, @Param("chapterNum") Integer chapterNum);
    List<NovelChapter> selectRecentChapters(@Param("novelId") String novelId, @Param("startChapter") Integer startChapter, @Param("endChapter") Integer endChapter);
    Integer selectMaxChapterNum(@Param("novelId") String novelId);
    long countByNovelId(@Param("novelId") String novelId);
    Long sumWordCountByNovelId(@Param("novelId") String novelId);
    int insert(NovelChapter chapter);
    int updateById(NovelChapter chapter);
    int softDeleteById(@Param("chapterId") String chapterId);
    int deleteById(@Param("chapterId") String chapterId);
}
