package com.example.bookborrowing.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 权限点实体，对应表 sys_permission。
 */
@Data
public class SysPermission {

    /** 主键ID */
    private Long id;

    /** 权限编码，如 book:add */
    private String permCode;

    /** 权限名称 */
    private String permName;

    /** 权限类型：MENU-菜单 ACTION-操作 */
    private String permType;

    /** 所属模块：book/category/borrow/purchase/group/system */
    private String module;

    /** 排序号 */
    private Integer sortNo;

    /** 创建时间 */
    private LocalDateTime createTime;
}
