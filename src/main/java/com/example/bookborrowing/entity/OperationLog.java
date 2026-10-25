package com.example.bookborrowing.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志实体，对应表 operation_log。
 */
@Data
public class OperationLog {

    /** 主键ID */
    private Long id;

    /** 操作用户ID */
    private Long userId;

    /** 操作人姓名（冗余展示用） */
    private String userName;

    /** 操作类型 */
    private String action;

    /** 操作详情 */
    private String detail;

    /** 操作时间 */
    private LocalDateTime createTime;

    /** 逻辑删除标记：0-未删除 1-已删除 */
    private Integer isDeleted;
}
