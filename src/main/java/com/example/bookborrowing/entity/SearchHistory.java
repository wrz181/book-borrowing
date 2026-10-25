package com.example.bookborrowing.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 搜索历史实体，对应表 search_history。
 */
@Data
public class SearchHistory {

    /** 主键ID */
    private Long id;

    /** 搜索用户ID */
    private Long userId;

    /** 搜索关键字 */
    private String keyword;

    /** 搜索时间 */
    private LocalDateTime searchTime;

    /** 逻辑删除标记：0-未删除 1-已删除 */
    private Integer isDeleted;
}
