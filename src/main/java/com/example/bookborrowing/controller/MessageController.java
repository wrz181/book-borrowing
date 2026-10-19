package com.example.bookborrowing.controller;

import com.example.bookborrowing.annotation.RequirePermission;
import com.example.bookborrowing.common.PageResult;
import com.example.bookborrowing.common.Result;
import com.example.bookborrowing.entity.MessageItem;
import com.example.bookborrowing.service.MessageService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 集团管理员 - 消息中心接口。
 * 需要 group:message 权限。
 */
@RestController
@RequestMapping("/api/messages")
@RequirePermission("group:message")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    /**
     * 分页查询当前用户的消息。
     *
     * @param readFlag 是否已读（0-未读 1-已读，null 全部）
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @param request  HTTP 请求
     * @return 分页结果
     */
    @GetMapping("/page")
    public Result<PageResult<MessageItem>> page(@RequestParam(required = false) Integer readFlag,
                                                @RequestParam(defaultValue = "1") int pageNum,
                                                @RequestParam(defaultValue = "10") int pageSize,
                                                HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return Result.success(messageService.page(userId, readFlag, pageNum, pageSize));
    }

    /**
     * 查询未读消息数（菜单徽标）。
     *
     * @param request HTTP 请求
     * @return 未读数量
     */
    @GetMapping("/unread-count")
    public Result<Long> unreadCount(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return Result.success(messageService.countUnread(userId));
    }

    /**
     * 标记单条消息已读。
     *
     * @param id      消息ID
     * @param request HTTP 请求
     * @return 空响应
     */
    @PutMapping("/{id}/read")
    public Result<Void> read(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        messageService.read(id, userId);
        return Result.success();
    }

    /**
     * 标记全部消息已读。
     *
     * @param request HTTP 请求
     * @return 空响应
     */
    @PutMapping("/read-all")
    public Result<Void> readAll(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        messageService.readAll(userId);
        return Result.success();
    }
}
