package com.example.bookborrowing.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 单点登录应用配置实体，对应表 integration_app。
 */
@Data
public class IntegrationApp {

    /** 主键ID */
    private Long id;

    /** 应用名称（配置后作为前台栏目名） */
    private String appName;

    /** 应用地址（类ERP平台入口URL） */
    private String appUrl;

    /** 集成方式：SSO-单点登录 */
    private String appType;

    /** 单点登录密钥（生成 ticket 用；接口不回显，仅接受写入） */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String ssoSecret;

    /** 是否启用待办集成：1-是 0-否 */
    private Integer todoEnabled;

    /** 是否启用消息集成：1-是 0-否 */
    private Integer msgEnabled;

    /** 是否启用：1-启用 0-停用 */
    private Integer enabled;

    /** 备注 */
    private String description;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 逻辑删除标记：0-未删除 1-已删除 */
    private Integer isDeleted;
}
