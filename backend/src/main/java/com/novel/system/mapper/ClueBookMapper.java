package com.novel.system.mapper;

import com.novel.system.entity.mysql.ClueBook;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ClueBookMapper {
    ClueBook selectById(@Param("clueId") String clueId);
    List<ClueBook> selectByNovelId(@Param("novelId") String novelId);
    List<ClueBook> selectByNovelIdAndStatus(@Param("novelId") String novelId, @Param("status") Integer status);
    List<ClueBook> selectUnrecycledByNovelId(@Param("novelId") String novelId);
    List<ClueBook> selectHighPriorityUnrecycled(@Param("novelId") String novelId);
    List<ClueBook> selectByBuryChapter(@Param("novelId") String novelId, @Param("chapterNum") Integer chapterNum);
    List<ClueBook> selectRecycledByChapter(@Param("novelId") String novelId, @Param("chapterNum") Integer chapterNum);
    int insert(ClueBook clue);
    int updateById(ClueBook clue);
    int deleteById(@Param("clueId") String clueId);
}
