package com.example.bookborrowing.dto;

import lombok.Data;

import java.util.List;

/**
 * 账号新增/修改参数。
 */
@Data
public class UserSaveDTO {

    /** 用户ID（新增时为空，修改时必填） */
    private Long id;

    /** 登录账号 */
    private String username;

    /** 初始密码（新增时使用，留空默认 123456；修改时不使用） */
    private String password;

    /** 昵称/姓名 */
    private String nickname;

    /** 主角色ID（默认身份，缺省取 roleIds 首个） */
    private Long roleId;

    /** 身份角色ID集合（多角色；为空时按 [roleId] 处理） */
    private List<Long> roleIds;
}
