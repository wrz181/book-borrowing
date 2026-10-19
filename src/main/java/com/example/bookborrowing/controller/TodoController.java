package com.example.bookborrowing.controller;

import com.example.bookborrowing.annotation.RequirePermission;
import com.example.bookborrowing.common.PageResult;
import com.example.bookborrowing.common.Result;
import com.example.bookborrowing.entity.TodoItem;
import com.example.bookborrowing.service.TodoService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 集团管理员 - 待办中心接口。
 * 需要 group:todo 权限。
 */
@RestController
@RequestMapping("/api/todos")
@RequirePermission("group:todo")
public class TodoController {

    private final TodoService todoService;

    public TodoController(TodoService todoService) {
        this.todoService = todoService;
    }

    /**
     * 分页查询当前用户的待办。
     *
     * @param status   状态（0-待办 1-已办，null 全部）
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @param request  HTTP 请求
     * @return 分页结果
     */
    @GetMapping("/page")
    public Result<PageResult<TodoItem>> page(@RequestParam(required = false) Integer status,
                                             @RequestParam(defaultValue = "1") int pageNum,
                                             @RequestParam(defaultValue = "10") int pageSize,
                                             HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return Result.success(todoService.page(userId, status, pageNum, pageSize));
    }

    /**
     * 查询未办待办数（菜单徽标）。
     *
     * @param request HTTP 请求
     * @return 未办数量
     */
    @GetMapping("/pending-count")
    public Result<Long> pendingCount(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return Result.success(todoService.countPending(userId));
    }

    /**
     * 办理待办。
     *
     * @param id      待办ID
     * @param request HTTP 请求
     * @return 空响应
     */
    @PutMapping("/{id}/done")
    public Result<Void> done(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        todoService.done(id, userId);
        return Result.success();
    }
}
