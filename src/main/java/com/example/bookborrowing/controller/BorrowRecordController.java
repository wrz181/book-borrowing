package com.example.bookborrowing.controller;

import com.example.bookborrowing.common.PageResult;
import com.example.bookborrowing.common.Result;
import com.example.bookborrowing.dto.BorrowRecordQueryDTO;
import com.example.bookborrowing.entity.BorrowRecord;
import com.example.bookborrowing.service.BorrowRecordService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 借阅记录接口：阅览申请、管理员确认/驳回、归还申请与确认、动态条件分页查询。
 * <p>
 * 数据范围由登录角色决定（不接收任何前端传入的用户/范围参数）：
 * 管理员（admin）可读写全部记录；学生（student）只能读写 borrower_id = 当前登录用户ID 的记录。
 */
@RestController
@RequestMapping("/api/borrow-records")
public class BorrowRecordController {

    private final BorrowRecordService borrowRecordService;

    public BorrowRecordController(BorrowRecordService borrowRecordService) {
        this.borrowRecordService = borrowRecordService;
    }

    /**
     * 分页动态条件查询借阅记录：图书名称、借阅人、状态、借阅日期范围可单独或组合使用。
     *
     * @param pageNum  页码，默认1
     * @param pageSize 每页条数，默认10
     * @param query    动态查询条件
     * @param request  HTTP 请求（拦截器已注入 currentUserId / currentRole）
     * @return 分页结果
     */
    @GetMapping("/page")
    public Result<PageResult<BorrowRecord>> page(@RequestParam(defaultValue = "1") int pageNum,
                                                 @RequestParam(defaultValue = "10") int pageSize,
                                                 BorrowRecordQueryDTO query,
                                                 HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return Result.error(401, "未登录或登录已过期");
        }
        return Result.success(borrowRecordService.page(pageNum, pageSize, query, scopeBorrowerId(request, userId)));
    }

    /**
     * 查询借阅记录详情（含关联图书名称、作者、分类名称）。
     *
     * @param id      记录ID
     * @param request HTTP 请求
     * @return 借阅记录实体
     */
    @GetMapping("/{id}")
    public Result<BorrowRecord> detail(@PathVariable Long id, HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return Result.error(401, "未登录或登录已过期");
        }
        return Result.success(borrowRecordService.getDetail(id, scopeBorrowerId(request, userId)));
    }

    /**
     * 提交阅览申请：生成「待确认」记录（不扣库存），等待管理员确认。
     *
     * @param record  借阅记录实体
     * @param request HTTP 请求
     * @return 空响应
     */
    @PostMapping
    public Result<Void> create(@RequestBody BorrowRecord record, HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return Result.error(401, "未登录或登录已过期");
        }
        borrowRecordService.create(record, userId, isAdmin(request));
        return Result.success();
    }

    /**
     * 更新借阅记录（仅管理员）。
     *
     * @param id     记录ID
     * @param record 借阅记录实体
     * @return 空响应
     */
    @PostMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody BorrowRecord record, HttpServletRequest request) {
        if (!isAdmin(request)) {
            return Result.error(403, "仅管理员可修改借阅记录");
        }
        record.setId(id);
        borrowRecordService.update(record);
        return Result.success();
    }

    /**
     * 删除借阅记录（仅管理员）：未归还记录删除时恢复库存。
     *
     * @param id 记录ID
     * @return 空响应
     */
    @PostMapping("/{id}/delete")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        if (!isAdmin(request)) {
            return Result.error(403, "仅管理员可删除借阅记录");
        }
        borrowRecordService.remove(id);
        return Result.success();
    }

    /**
     * 发起归还：借阅中 → 归还待确认，等待管理员确认。
     *
     * @param id      记录ID
     * @param request HTTP 请求
     * @return 空响应
     */
    @PostMapping("/{id}/return")
    public Result<Void> returnBook(@PathVariable Long id, HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return Result.error(401, "未登录或登录已过期");
        }
        borrowRecordService.requestReturn(id, userId, isAdmin(request));
        return Result.success();
    }

    /**
     * 管理员确认借阅：待确认 → 借阅中，扣减库存并联动图书状态。
     *
     * @param id      记录ID
     * @param request HTTP 请求
     * @return 空响应
     */
    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id, HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return Result.error(401, "未登录或登录已过期");
        }
        if (!isAdmin(request)) {
            return Result.error(403, "仅管理员可审批借阅申请");
        }
        borrowRecordService.approve(id, userId);
        return Result.success();
    }

    /**
     * 管理员驳回阅览申请：待确认 → 已驳回。
     *
     * @param id      记录ID
     * @param body    请求体（可含 reason 驳回原因）
     * @param request HTTP 请求
     * @return 空响应
     */
    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body,
                               HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return Result.error(401, "未登录或登录已过期");
        }
        if (!isAdmin(request)) {
            return Result.error(403, "仅管理员可审批借阅申请");
        }
        borrowRecordService.reject(id, body == null ? null : body.get("reason"), userId);
        return Result.success();
    }

    /**
     * 管理员确认归还：归还待确认 → 已归还，恢复库存与图书可借状态。
     *
     * @param id      记录ID
     * @param request HTTP 请求
     * @return 空响应
     */
    @PostMapping("/{id}/return-confirm")
    public Result<Void> confirmReturn(@PathVariable Long id, HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return Result.error(401, "未登录或登录已过期");
        }
        if (!isAdmin(request)) {
            return Result.error(403, "仅管理员可确认归还");
        }
        borrowRecordService.confirmReturn(id, userId);
        return Result.success();
    }

    /**
     * 管理员驳回归还：归还待确认 → 借阅中。
     *
     * @param id      记录ID
     * @param body    请求体（可含 reason 驳回原因）
     * @param request HTTP 请求
     * @return 空响应
     */
    @PostMapping("/{id}/return-reject")
    public Result<Void> rejectReturn(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body,
                                     HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return Result.error(401, "未登录或登录已过期");
        }
        if (!isAdmin(request)) {
            return Result.error(403, "仅管理员可审批归还申请");
        }
        borrowRecordService.rejectReturn(id, body == null ? null : body.get("reason"), userId);
        return Result.success();
    }

    /**
     * 读取拦截器注入的当前登录用户ID。
     *
     * @param request HTTP 请求
     * @return 用户ID，未登录返回 null
     */
    private Long currentUserId(HttpServletRequest request) {
        Object attr = request.getAttribute("currentUserId");
        return attr instanceof Long userId ? userId : null;
    }

    /**
     * 判断当前登录用户是否为管理员。
     *
     * @param request HTTP 请求
     * @return 管理员返回 true
     */
    private boolean isAdmin(HttpServletRequest request) {
        return "admin".equals(request.getAttribute("currentRole"));
    }

    /**
     * 计算数据范围：管理员返回 null（不限范围），学生返回本人用户ID。
     *
     * @param request HTTP 请求
     * @param userId  当前登录用户ID
     * @return 数据范围用户ID
     */
    private Long scopeBorrowerId(HttpServletRequest request, Long userId) {
        return isAdmin(request) ? null : userId;
    }
}
