package com.example.bookborrowing.dto;

import lombok.Data;

/**
 * 热门图书排行条目 VO。
 * 热度分由 Redis ZSet 维护：浏览一次 +1 分，提交借阅申请一次 +10 分；
 * Redis 不可用时由数据库按借阅记录数降级统计，此时 source 为 db。
 */
@Data
public class HotBookVO {

    /** 图书ID */
    private Long bookId;

    /** 图书名称 */
    private String bookName;

    /** 作者 */
    private String author;

    /** 分类名称 */
    private String categoryName;

    /** 热度分（Redis 实时统计为热度加权分；数据库降级统计时为 null） */
    private Double hotScore;

    /** 名次（从 1 开始） */
    private Integer rank;

    /** 统计来源：redis-实时排行 / db-数据库降级统计 */
    private String source;
}
