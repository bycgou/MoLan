package com.novel.system.dto.request;

import lombok.Data;

/**
 * 登录请求DTO
 */
@Data
public class LoginRequest {
    /** 用户名 */
    private String username;
    /** 密码 */
    private String password;
}
