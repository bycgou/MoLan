package com.novel.system.mapper;

import com.novel.system.entity.mysql.WorldClock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WorldClockMapper {
    WorldClock selectByNovelId(@Param("novelId") String novelId);
    int insert(WorldClock clock);
    int updateById(WorldClock clock);
}
