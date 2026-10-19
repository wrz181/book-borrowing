package com.example.bookborrowing.service.impl;

import com.example.bookborrowing.common.BusinessException;
import com.example.bookborrowing.common.PageResult;
import com.example.bookborrowing.entity.TodoItem;
import com.example.bookborrowing.mapper.TodoItemMapper;
import com.example.bookborrowing.service.TodoService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 待办业务实现。
 */
@Service
public class TodoServiceImpl implements TodoService {

    private final TodoItemMapper todoItemMapper;

    public TodoServiceImpl(TodoItemMapper todoItemMapper) {
        this.todoItemMapper = todoItemMapper;
    }

    @Override
    public PageResult<TodoItem> page(Long userId, Integer status, int pageNum, int pageSize) {
        int page = Math.max(pageNum, 1);
        int size = pageSize <= 0 ? 10 : pageSize;
        int offset = (page - 1) * size;
        // userId 为 null 时不限接收人（能力保留，当前调用方均传入登录用户ID）
        List<TodoItem> list = todoItemMapper.selectPage(offset, size, userId, status);
        long total = todoItemMapper.selectCount(userId, status);
        return new PageResult<>(list, total, page, size);
    }

    @Override
    public long countPending(Long userId) {
        return todoItemMapper.countPending(userId);
    }

    @Override
    public TodoItem create(TodoItem todo) {
        if (todo.getReceiverId() == null) {
            throw new BusinessException("待办接收人不能为空");
        }
        if (todo.getTitle() == null || todo.getTitle().isBlank()) {
            throw new BusinessException("待办标题不能为空");
        }
        if (todo.getStatus() == null) {
            todo.setStatus(0);
        }
        todoItemMapper.insert(todo);
        return todo;
    }

    @Override
    public void done(Long id, Long userId) {
        TodoItem todo = todoItemMapper.selectById(id);
        if (todo == null) {
            throw new BusinessException("待办不存在");
        }
        if (!todo.getReceiverId().equals(userId)) {
            throw new BusinessException("无权办理他人的待办");
        }
        if (todo.getStatus() != null && todo.getStatus() == 1) {
            throw new BusinessException("该待办已办理");
        }
        todoItemMapper.markDone(id, LocalDateTime.now());
    }
}
