package com.example.bookborrowing.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单点登录解析结果（返回给外部平台用于「登录态」展示）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ErpSsoUserDTO {

    /** 应用ID */
    private Long appId;

    /** 应用名称 */
    private String appName;

    /** 登录用户ID */
    private Long userId;

    /** 登录用户名 */
    private String username;

    /** 登录用户昵称 */
    private String nickname;
}
