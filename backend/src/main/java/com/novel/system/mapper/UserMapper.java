package com.novel.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novel.system.entity.mysql.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<User> {
    @Select("SELECT * FROM sys_user WHERE username = #{username} LIMIT 1")
    User findByUsername(@Param("username") String username);
    
    @Select("SELECT * FROM sys_user WHERE email = #{email} LIMIT 1")
    User findByEmail(@Param("email") String email);
}
