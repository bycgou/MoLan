package com.novel.system.entity.mysql;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户实体
 */
@Data
public class SysUser {
    /** 用户ID，主键 */
    private String userId;
    /** 用户名 */
    private String username;
    /** 密码（BCrypt加密） */
    private String password;
    /** 邮箱 */
    private String email;
    /** 昵称 */
    private String nickname;
    /** 头像URL */
    private String avatar;
    /** 角色：user-普通用户，admin-管理员 */
    private String role = "user";
    /** 会员等级：0-免费，1-普通会员，2-高级会员 */
    private Integer membershipLevel = 0;
    /** 当日生成次数 */
    private Integer dailyGenerationCount = 0;
    /** 最后一次生成日期 */
    private LocalDateTime lastGenerationDate;
    /** 累计创建小说数量 */
    private Integer totalNovelCount = 0;
    /** 状态：0-禁用，1-正常 */
    private Integer status = 1;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
}
