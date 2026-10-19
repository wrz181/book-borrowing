package com.example.bookborrowing.service;

import com.example.bookborrowing.common.PageResult;
import com.example.bookborrowing.entity.TodoItem;

/**
 * 待办业务接口。
 */
public interface TodoService {

    /**
     * 分页查询某用户的待办。
     *
     * @param userId   接收人ID
     * @param status   状态（可空）
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    PageResult<TodoItem> page(Long userId, Integer status, int pageNum, int pageSize);

    /**
     * 统计某用户未办待办数。
     *
     * @param userId 接收人ID
     * @return 未办数量
     */
    long countPending(Long userId);

    /**
     * 新增待办（集成回调调用）。
     *
     * @param todo 待办实体
     * @return 保存后的实体（含ID）
     */
    TodoItem create(TodoItem todo);

    /**
     * 办理待办。
     *
     * @param id     待办ID
     * @param userId 当前用户ID（校验归属）
     */
    void done(Long id, Long userId);
}
