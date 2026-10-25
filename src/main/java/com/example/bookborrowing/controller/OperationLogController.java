package com.example.bookborrowing.controller;

import com.example.bookborrowing.common.Result;
import com.example.bookborrowing.entity.OperationLog;
import com.example.bookborrowing.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 操作日志接口：借阅关键操作自动留痕，仅管理员可查看。
 */
@RestController
@RequestMapping("/api/operation-logs")
public class OperationLogController {

    private final OperationLogService operationLogService;

    public OperationLogController(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    /**
     * 查询最近的操作日志（时间倒序，默认最近 50 条，仅管理员）。
     *
     * @param limit   条数，默认 50
     * @param request HTTP 请求
     * @return 操作日志列表
     */
    @GetMapping
    public Result<List<OperationLog>> list(@RequestParam(defaultValue = "50") int limit,
                                           HttpServletRequest request) {
        if (!"admin".equals(request.getAttribute("currentRole"))) {
            return Result.error(403, "仅管理员可查看操作日志");
        }
        return Result.success(operationLogService.recent(Math.min(Math.max(limit, 1), 200)));
    }
}
