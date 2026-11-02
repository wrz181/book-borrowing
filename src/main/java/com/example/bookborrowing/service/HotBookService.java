package com.example.bookborrowing.service;

import com.example.bookborrowing.dto.HotBookVO;

import java.util.List;

/**
 * 热门图书排行服务：基于 Redis ZSet 的实时热度统计。
 */
public interface HotBookService {

    /**
     * 记录一次图书详情浏览（+1 分）。Redis 不可用时静默忽略。
     *
     * @param bookId 图书ID
     */
    void recordView(Long bookId);

    /**
     * 记录一次借阅申请（+10 分）。Redis 不可用时静默忽略。
     *
     * @param bookId 图书ID
     */
    void recordBorrow(Long bookId);

    /**
     * 查询热门图书排行前 N 名。
     * Redis 可用时返回实时热度排行；不可用时自动降级为数据库按借阅记录数的统计。
     *
     * @param top 名次数
     * @return 排行列表（rank 从 1 开始）
     */
    List<HotBookVO> top(int top);
}
