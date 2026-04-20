package com.novel.system.entity.mysql;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户实体（MyBatis-Plus）
 */
@Data
@TableName("sys_user")
public class User {
    /** 用户ID，主键 */
    @TableId
    private String userId;
    /** 用户名 */
    private String username;
    /** 密码（BCrypt加密） */
    private String password;
    /** 邮箱 */
    private String email;
    /** 昵称 */
    private String nickname;
    /** 角色：user-普通用户，admin-管理员 */
    private String role;
    /** 状态：0-禁用，1-正常 */
    private Integer status;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
}
