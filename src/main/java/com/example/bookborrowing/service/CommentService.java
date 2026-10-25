package com.example.bookborrowing.service;

import com.example.bookborrowing.entity.BookComment;

import java.util.List;

/**
 * 图书评论业务接口。
 */
public interface CommentService {

    /**
     * 按图书查询评论（时间倒序）。
     *
     * @param bookId 图书ID
     * @return 评论列表
     */
    List<BookComment> listByBook(Long bookId);

    /**
     * 发表评论（评论人与评分由服务端按登录态与入参确定）。
     *
     * @param comment       评论（bookId/rating/content）
     * @param currentUserId 当前登录用户ID
     */
    void add(BookComment comment, Long currentUserId);

    /**
     * 删除评论：仅评论本人或管理员可删。
     *
     * @param id            评论ID
     * @param currentUserId 当前登录用户ID
     * @param isAdmin       是否管理员
     */
    void remove(Long id, Long currentUserId, boolean isAdmin);
}
