package com.novel.system.dto.request;

import lombok.Data;

/**
 * 注册请求DTO
 */
@Data
public class RegisterRequest {
    /** 用户名 */
    private String username;
    /** 密码 */
    private String password;
    /** 邮箱 */
    private String email;
    /** 昵称 */
    private String nickname;
}
