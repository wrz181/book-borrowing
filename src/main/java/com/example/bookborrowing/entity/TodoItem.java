package com.example.bookborrowing.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 待办事项实体，对应表 todo_item。
 */
@Data
public class TodoItem {

    /** 主键ID */
    private Long id;

    /** 待办标题 */
    private String title;

    /** 待办内容 */
    private String content;

    /** 业务类型 */
    private String bizType;

    /** 关联业务ID */
    private Long bizId;

    /** 来源应用ID */
    private Long appId;

    /** 接收人ID */
    private Long receiverId;

    /** 状态：0-待办 1-已办 */
    private Integer status;

    /** 办理时间 */
    private LocalDateTime doneTime;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 逻辑删除标记：0-未删除 1-已删除 */
    private Integer isDeleted;

    /** 来源应用名称（非表字段，关联查询） */
    private String appName;
}
