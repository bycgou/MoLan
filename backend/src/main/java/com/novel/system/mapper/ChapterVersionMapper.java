package com.novel.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novel.system.entity.mysql.ChapterVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 章节版本快照 Mapper 接口
 */
@Mapper
public interface ChapterVersionMapper extends BaseMapper<ChapterVersion> {
    
    /**
     * 根据章节ID查询最大版本号
     * @param chapterId 章节ID
     * @return 最大版本号
     */
    @Select("SELECT MAX(version) FROM chapter_versions WHERE chapter_id = #{chapterId}")
    Integer selectMaxVersion(@Param("chapterId") String chapterId);
    
    /**
     * 根据章节ID查询版本列表
     * @param chapterId 章节ID
     * @return 版本列表
     */
    @Select("SELECT * FROM chapter_versions WHERE chapter_id = #{chapterId} ORDER BY version DESC")
    List<ChapterVersion> selectByChapterId(@Param("chapterId") String chapterId);
    
    /**
     * 删除旧版本，保留最近N个版本
     * @param chapterId 章节ID
     * @param keepCount 保留的版本数量
     * @return 删除的记录数
     */
    @Select("DELETE FROM chapter_versions WHERE chapter_id = #{chapterId} AND version NOT IN (SELECT version FROM (SELECT version FROM chapter_versions WHERE chapter_id = #{chapterId} ORDER BY version DESC LIMIT #{keepCount}) AS t)")
    int deleteOldVersions(@Param("chapterId") String chapterId, @Param("keepCount") int keepCount);
}