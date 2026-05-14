package com.novel.system.mapper;

import com.novel.system.entity.mysql.NovelChapter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
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
    
    /**
     * 查询最近章节的大纲和钩子（第四层滑动窗口 — 子层4b：近章大纲）
     * @param novelId 小说ID
     * @param currentChapterNum 当前章节号
     * @param limit 查询数量限制
     * @return 章节列表（包含大纲和钩子）
     */
    @Select("SELECT chapter_num, chapter_title, chapter_outline, hook FROM novel_chapter " +
            "WHERE novel_id = #{novelId} AND chapter_num < #{currentChapterNum} AND is_deleted = 0 " +
            "ORDER BY chapter_num DESC LIMIT #{limit}")
    List<NovelChapter> selectRecentOutlines(@Param("novelId") String novelId, 
                                           @Param("currentChapterNum") Integer currentChapterNum,
                                           @Param("limit") Integer limit);
    
    /**
     * 查询小说的前N章（用于文风参考抽取）
     * @param novelId 小说ID
     * @param limit 查询数量限制
     * @return 章节列表（包含内容）
     */
    @Select("SELECT * FROM novel_chapter " +
            "WHERE novel_id = #{novelId} AND is_deleted = 0 " +
            "ORDER BY chapter_num ASC LIMIT #{limit}")
    List<NovelChapter> selectByNovelIdLimit(@Param("novelId") String novelId, 
                                           @Param("limit") Integer limit);
}
