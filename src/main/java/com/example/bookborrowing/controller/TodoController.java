package com.example.bookborrowing.controller;

import com.example.bookborrowing.annotation.RequirePermission;
import com.example.bookborrowing.common.PageResult;
import com.example.bookborrowing.common.Result;
import com.example.bookborrowing.entity.TodoItem;
import com.example.bookborrowing.service.TodoService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 待办中心接口。
 * <p>
 * 鉴权：持有 group:todo（集团管理员）或 todo:own（普通人员）任一即可访问。
 * 数据范围恒为当前登录用户：只能看到 receiver_id = 自己 的记录，客户端无法通过查询参数指定他人接收人。
 */
@RestController
@RequestMapping("/api/todos")
@RequirePermission(anyOf = {"group:todo", "todo:own"})
public class TodoController {

    private final TodoService todoService;

    public TodoController(TodoService todoService) {
        this.todoService = todoService;
    }

    /**
     * 分页查询当前登录用户的待办。
     * 接收人一律取自登录态，前端传入的任何接收人参数都被忽略。
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
        if (userId == null) {
            // 兜底：拿不到登录态时绝不查询
            return Result.error(401, "未登录或登录已过期");
        }
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
    @PostMapping("/{id}/done")
    public Result<Void> done(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        todoService.done(id, userId);
        return Result.success();
    }
}
