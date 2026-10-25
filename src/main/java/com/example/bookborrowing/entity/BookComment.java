package com.example.bookborrowing.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 图书评论实体，对应表 book_comment。
 */
@Data
public class BookComment {

    /** 主键ID */
    private Long id;

    /** 图书ID */
    private Long bookId;

    /** 评论用户ID */
    private Long userId;

    /** 评论人姓名（冗余展示用） */
    private String userName;

    /** 评分：1-5 星 */
    private Integer rating;

    /** 评论内容 */
    private String content;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 逻辑删除标记：0-未删除 1-已删除 */
    private Integer isDeleted;
}
