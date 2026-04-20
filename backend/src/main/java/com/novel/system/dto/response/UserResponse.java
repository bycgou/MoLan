package com.novel.system.dto.response;

import lombok.Data;

/**
 * 用户响应DTO
 */
@Data
public class UserResponse {
    /** 用户ID */
    private String userId;
    /** 用户名 */
    private String username;
    /** 邮箱 */
    private String email;
    /** 昵称 */
    private String nickname;
    /** 角色：user-普通用户，admin-管理员 */
    private String role;
    /** 状态：0-禁用，1-正常 */
    private Integer status;
    /** JWT令牌 */
    private String token;
}
