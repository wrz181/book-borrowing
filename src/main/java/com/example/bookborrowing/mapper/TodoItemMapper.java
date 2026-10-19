package com.example.bookborrowing.mapper;

import com.example.bookborrowing.entity.TodoItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 待办事项 Mapper。
 */
@Mapper
public interface TodoItemMapper {

    /**
     * 分页查询待办。
     *
     * @param offset     偏移量
     * @param limit      每页条数
     * @param receiverId 接收人ID（null 表示不限接收人，能力保留）
     * @param status     状态（可空，null 表示全部）
     * @return 待办列表
     */
    List<TodoItem> selectPage(@Param("offset") int offset,
                              @Param("limit") int limit,
                              @Param("receiverId") Long receiverId,
                              @Param("status") Integer status);

    /**
     * 统计待办总数。
     *
     * @param receiverId 接收人ID（null 表示不限接收人，能力保留）
     * @param status     状态（可空）
     * @return 总记录数
     */
    long selectCount(@Param("receiverId") Long receiverId, @Param("status") Integer status);

    /**
     * 统计某接收人未办待办数。
     *
     * @param receiverId 接收人ID
     * @return 未办数量
     */
    long countPending(Long receiverId);

    /**
     * 按ID查询待办。
     *
     * @param id 待办ID
     * @return 待办实体，不存在返回 null
     */
    TodoItem selectById(Long id);

    /**
     * 新增待办。
     *
     * @param todo 待办实体
     * @return 影响行数
     */
    int insert(TodoItem todo);

    /**
     * 办理待办（置为已办）。
     *
     * @param id       待办ID
     * @param doneTime 办理时间
     * @return 影响行数
     */
    int markDone(@Param("id") Long id, @Param("doneTime") LocalDateTime doneTime);
}
