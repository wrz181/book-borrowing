package com.example.bookborrowing.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色实体，对应表 sys_role。
 */
@Data
public class SysRole {

    /** 主键ID */
    private Long id;

    /** 角色编码 */
    private String roleCode;

    /** 角色名称 */
    private String roleName;

    /** 角色描述 */
    private String description;

    /** 是否内置角色：1-内置不可删除 0-自定义 */
    private Integer isBuiltin;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 逻辑删除标记：0-未删除 1-已删除 */
    private Integer isDeleted;

    /** 该角色拥有的权限编码集合（非表字段，查询时组装） */
    private java.util.List<String> permissions;
}
