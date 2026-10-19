package com.example.bookborrowing.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 集成表单实体，对应表 integration_form。
 * 本系统把表单下发给类ERP平台，平台处理后回调更新状态。
 */
@Data
public class IntegrationForm {

    /** 主键ID */
    private Long id;

    /** 目标应用ID */
    private Long appId;

    /** 表单标题 */
    private String title;

    /** 表单类型：BOOK_PURCHASE-图书采购 BORROW_APPLY-借阅申请 */
    private String formType;

    /** 表单内容（JSON） */
    private String content;

    /** 状态：0-待处理 1-已处理 */
    private Integer status;

    /** 发起人ID */
    private Long senderId;

    /** 发起人姓名 */
    private String senderName;

    /** 处理时间 */
    private LocalDateTime handleTime;

    /** 处理意见 */
    private String handleComment;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 逻辑删除标记：0-未删除 1-已删除 */
    private Integer isDeleted;

    /** 应用名称（非表字段，关联查询） */
    private String appName;
}
