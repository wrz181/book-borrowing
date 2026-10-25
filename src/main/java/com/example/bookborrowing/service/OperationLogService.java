package com.example.bookborrowing.service;

import com.example.bookborrowing.entity.OperationLog;

import java.util.List;

/**
 * 操作日志业务接口。
 */
public interface OperationLogService {

    /**
     * 记录一条关键操作日志（借阅申请/审批/归还等）。
     *
     * @param userId   操作用户ID
     * @param userName 操作人姓名（可空）
     * @param action   操作类型
     * @param detail   操作详情
     */
    void record(Long userId, String userName, String action, String detail);

    /**
     * 查询最近的操作日志。
     *
     * @param limit 条数上限
     * @return 操作日志列表（时间倒序）
     */
    List<OperationLog> recent(int limit);
}
