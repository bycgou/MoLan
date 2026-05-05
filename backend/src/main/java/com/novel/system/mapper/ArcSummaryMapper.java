package com.novel.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novel.system.entity.mysql.ArcSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 弧段摘要 Mapper 接口
 */
@Mapper
public interface ArcSummaryMapper extends BaseMapper<ArcSummary> {
    
    /**
     * 根据小说ID和最大章节号查询弧段摘要
     * @param novelId 小说ID
     * @param maxChapter 最大章节号
     * @return 弧段摘要列表
     */
    @Select("SELECT * FROM arc_summaries WHERE novel_id = #{novelId} AND chapter_to <= #{maxChapter} ORDER BY chapter_from ASC")
    List<ArcSummary> selectByNovelIdAndRange(@Param("novelId") String novelId, 
                                             @Param("maxChapter") Integer maxChapter);
    
    /**
     * 根据小说ID查询所有弧段摘要
     * @param novelId 小说ID
     * @return 弧段摘要列表
     */
    @Select("SELECT * FROM arc_summaries WHERE novel_id = #{novelId} ORDER BY chapter_from ASC")
    List<ArcSummary> selectByNovelId(@Param("novelId") String novelId);
}