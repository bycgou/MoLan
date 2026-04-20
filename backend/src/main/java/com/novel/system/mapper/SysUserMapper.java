package com.novel.system.mapper;

import com.novel.system.entity.mysql.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SysUserMapper {
    SysUser selectById(@Param("userId") String userId);
    SysUser selectByUsername(@Param("username") String username);
    SysUser selectByEmail(@Param("email") String email);
    int insert(SysUser user);
    int updateById(SysUser user);
    int deleteById(@Param("userId") String userId);
}
