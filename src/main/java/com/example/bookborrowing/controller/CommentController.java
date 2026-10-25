package com.example.bookborrowing.controller;

import com.example.bookborrowing.common.Result;
import com.example.bookborrowing.entity.BookComment;
import com.example.bookborrowing.service.CommentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 图书评论接口：按图书查看评论、发表评论、删除本人评论（管理员可删任意评论）。
 */
@RestController
@RequestMapping("/api/comments")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    /**
     * 查询某图书的全部评论（时间倒序）。
     *
     * @param bookId 图书ID
     * @return 评论列表
     */
    @GetMapping
    public Result<List<BookComment>> list(@RequestParam Long bookId) {
        return Result.success(commentService.listByBook(bookId));
    }

    /**
     * 发表评论（评论人与用户ID按登录态确定）。
     *
     * @param comment 评论（bookId/rating/content）
     * @param request HTTP 请求
     * @return 空响应
     */
    @PostMapping
    public Result<Void> add(@RequestBody BookComment comment, HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return Result.error(401, "未登录或登录已过期");
        }
        commentService.add(comment, userId);
        return Result.success();
    }

    /**
     * 删除评论（仅评论本人或管理员）。
     *
     * @param id      评论ID
     * @param request HTTP 请求
     * @return 空响应
     */
    @PostMapping("/{id}/delete")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return Result.error(401, "未登录或登录已过期");
        }
        commentService.remove(id, userId, isAdmin(request));
        return Result.success();
    }

    private Long currentUserId(HttpServletRequest request) {
        Object attr = request.getAttribute("currentUserId");
        return attr instanceof Long userId ? userId : null;
    }

    private boolean isAdmin(HttpServletRequest request) {
        return "admin".equals(request.getAttribute("currentRole"));
    }
}
