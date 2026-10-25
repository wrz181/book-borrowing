package com.example.bookborrowing.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统用户实体，对应表 sys_user。
 */
@Data
public class SysUser {

    /** 主键ID */
    private Long id;

    /** 登录账号 */
    private String username;

    /** 密码（BCrypt加密，不对外返回） */
    private String password;

    /** 昵称/姓名 */
    private String nickname;

    /** 角色：admin-管理员 student-学生 */
    private String role;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 逻辑删除标记：0-未删除 1-已删除 */
    private Integer isDeleted;
}
