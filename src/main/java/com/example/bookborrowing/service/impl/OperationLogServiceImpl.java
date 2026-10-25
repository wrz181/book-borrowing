package com.example.bookborrowing.service.impl;

import com.example.bookborrowing.entity.OperationLog;
import com.example.bookborrowing.mapper.OperationLogMapper;
import com.example.bookborrowing.service.OperationLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 操作日志业务实现：借阅模块关键操作自动留痕。
 */
@Service
public class OperationLogServiceImpl implements OperationLogService {

    private static final Logger log = LoggerFactory.getLogger(OperationLogServiceImpl.class);

    private final OperationLogMapper operationLogMapper;

    public OperationLogServiceImpl(OperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    @Override
    public void record(Long userId, String userName, String action, String detail) {
        if (userId == null || action == null || action.isBlank()) {
            return;
        }
        OperationLog entry = new OperationLog();
        entry.setUserId(userId);
        entry.setUserName(userName);
        entry.setAction(action);
        entry.setDetail(detail);
        operationLogMapper.insert(entry);
        log.debug("记录操作日志：用户={}，操作={}，详情={}", userName, action, detail);
    }

    @Override
    public List<OperationLog> recent(int limit) {
        return operationLogMapper.selectRecent(limit);
    }
}
