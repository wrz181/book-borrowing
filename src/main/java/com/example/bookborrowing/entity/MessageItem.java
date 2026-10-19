package com.example.bookborrowing.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息实体，对应表 message_item。
 */
@Data
public class MessageItem {

    /** 主键ID */
    private Long id;

    /** 消息标题 */
    private String title;

    /** 消息内容 */
    private String content;

    /** 消息类型 */
    private String msgType;

    /** 来源应用ID */
    private Long appId;

    /** 接收人ID */
    private Long receiverId;

    /** 是否已读：0-未读 1-已读 */
    private Integer readFlag;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 逻辑删除标记：0-未删除 1-已删除 */
    private Integer isDeleted;

    /** 来源应用名称（非表字段，关联查询） */
    private String appName;
}
