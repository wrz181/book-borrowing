package com.example.bookborrowing.dto;

import lombok.Data;

/**
 * 登录请求参数。
 */
@Data
public class LoginDTO {

    /** 登录账号 */
    private String username;

    /** 密码 */
    private String password;
}
