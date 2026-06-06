package com.novel.system.dto.request;

import lombok.Data;
import lombok.ToString;

/**
 * 登录请求DTO
 */
@Data
public class LoginRequest {
    /** 用户名 */
    private String username;
    /** 密码 */
    @ToString.Exclude
    private String password;
}
