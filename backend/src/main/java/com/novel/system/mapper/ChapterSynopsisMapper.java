package com.novel.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novel.system.entity.mysql.ChapterSynopsis;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 章节简介蓝图 Mapper 接口
 */
@Mapper
public interface ChapterSynopsisMapper extends BaseMapper<ChapterSynopsis> {
    
    /**
     * 根据章节ID查询章节简介
     * @param chapterId 章节ID
     * @return 章节简介
     */
    @Select("SELECT * FROM chapter_synopses WHERE chapter_id = #{chapterId}")
    ChapterSynopsis selectByChapterId(@Param("chapterId") String chapterId);
    
    /**
     * 根据小说ID和章节号查询章节简介
     * @param novelId 小说ID
     * @param chapterNum 章节号
     * @return 章节简介
     */
    @Select("SELECT * FROM chapter_synopses WHERE novel_id = #{novelId} AND chapter_num = #{chapterNum}")
    ChapterSynopsis selectByNovelIdAndChapterNum(@Param("novelId") String novelId, 
                                                  @Param("chapterNum") Integer chapterNum);
    
    /**
     * 根据小说ID查询所有章节简介
     * @param novelId 小说ID
     * @return 章节简介列表
     */
    @Select("SELECT * FROM chapter_synopses WHERE novel_id = #{novelId} ORDER BY chapter_num ASC")
    List<ChapterSynopsis> selectByNovelId(@Param("novelId") String novelId);
}