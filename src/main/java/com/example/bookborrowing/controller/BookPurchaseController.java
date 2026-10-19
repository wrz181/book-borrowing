package com.example.bookborrowing.controller;

import com.example.bookborrowing.annotation.RequirePermission;
import com.example.bookborrowing.common.PageResult;
import com.example.bookborrowing.common.Result;
import com.example.bookborrowing.dto.PurchaseQueryDTO;
import com.example.bookborrowing.entity.BookPurchase;
import com.example.bookborrowing.entity.SysUser;
import com.example.bookborrowing.service.BookPurchaseService;
import com.example.bookborrowing.service.PurchaseFlowService;
import com.example.bookborrowing.service.SysUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 图书采购接口：采购单增删改查、动态条件分页查询、人工补偿入库、取消，以及超级节点流程引擎
 * （提交申请 → 审批下发 ERP → 回执/超时打回）。
 * 采购为集团管理职能：查看需 purchase:view，操作需 purchase:manage。
 */
@RestController
@RequestMapping("/api/purchases")
@RequirePermission("purchase:view")
public class BookPurchaseController {

    private final BookPurchaseService bookPurchaseService;

    private final PurchaseFlowService purchaseFlowService;

    private final SysUserService sysUserService;

    public BookPurchaseController(BookPurchaseService bookPurchaseService,
                                  PurchaseFlowService purchaseFlowService,
                                  SysUserService sysUserService) {
        this.bookPurchaseService = bookPurchaseService;
        this.purchaseFlowService = purchaseFlowService;
        this.sysUserService = sysUserService;
    }

    /**
     * 分页动态条件查询采购单：采购单号、图书名称、分类、状态、应用可单独或组合使用。
     *
     * @param pageNum  页码，默认1
     * @param pageSize 每页条数，默认10
     * @param query    动态查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    public Result<PageResult<BookPurchase>> page(@RequestParam(defaultValue = "1") int pageNum,
                                                 @RequestParam(defaultValue = "10") int pageSize,
                                                 PurchaseQueryDTO query) {
        return Result.success(bookPurchaseService.page(pageNum, pageSize, query));
    }

    /**
     * 采购单详情。
     *
     * @param id 采购单ID
     * @return 采购单实体
     */
    @GetMapping("/{id}")
    public Result<BookPurchase> detail(@PathVariable Long id) {
        return Result.success(bookPurchaseService.getById(id));
    }

    /**
     * 新增采购单（草稿）。
     *
     * @param purchase 采购单实体
     * @param request  HTTP 请求
     * @return 空响应
     */
    @PostMapping
    @RequirePermission("purchase:manage")
    public Result<Void> create(@RequestBody BookPurchase purchase, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        bookPurchaseService.create(purchase, userId, currentNickname(userId));
        return Result.success();
    }

    /**
     * 修改采购单（仅草稿允许）。
     *
     * @param id       采购单ID
     * @param purchase 采购单实体
     * @return 空响应
     */
    @PostMapping("/{id}")
    @RequirePermission("purchase:manage")
    public Result<Void> update(@PathVariable Long id, @RequestBody BookPurchase purchase) {
        purchase.setId(id);
        bookPurchaseService.update(purchase);
        return Result.success();
    }

    /**
     * 删除采购单（逻辑删除，仅草稿/已取消允许）。
     *
     * @param id 采购单ID
     * @return 空响应
     */
    @PostMapping("/{id}/delete")
    @RequirePermission("purchase:manage")
    public Result<Void> delete(@PathVariable Long id) {
        bookPurchaseService.delete(id);
        return Result.success();
    }

    /**
     * 人工补偿入库：要求流程已完成（current_node = DONE）且采购单处于已提交状态；
     * 无流程实例的采购单会被拒绝，须走流程引擎入库。
     *
     * @param id     采购单ID
     * @param remark 入库备注（可空）
     * @return 空响应
     */
    @PostMapping("/{id}/stock-in")
    @RequirePermission("purchase:manage")
    public Result<Void> stockIn(@PathVariable Long id, @RequestParam(required = false) String remark) {
        bookPurchaseService.stockIn(id, remark);
        return Result.success();
    }

    /**
     * 取消采购单：草稿/已提交 → 已取消，同时把未结束的流程实例置为已取消。
     *
     * @param id      采购单ID
     * @param request HTTP 请求
     * @return 空响应
     */
    @PostMapping("/{id}/cancel")
    @RequirePermission("purchase:manage")
    public Result<Void> cancel(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        bookPurchaseService.cancel(id, userId, currentNickname(userId));
        return Result.success();
    }

    /**
     * 提交申请：草稿 → 提交申请节点（流程引擎入口，超级节点由后端集成业务配置决定）。
     *
     * @param id      采购单ID
     * @param request HTTP 请求
     * @return 空响应
     */
    @PostMapping("/{id}/submit-apply")
    @RequirePermission("purchase:manage")
    public Result<Void> submitApply(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        purchaseFlowService.submitApply(id, userId, currentNickname(userId));
        return Result.success();
    }

    /**
     * 审批通过并下发 ERP：提交申请 → 审批 → 超级节点（出站失败整体回滚）。
     *
     * @param id      采购单ID
     * @param body    审批意见 {"comment":"..."}（可空 body）
     * @param request HTTP 请求
     * @return 空响应
     */
    @PostMapping("/{id}/flow/approve")
    @RequirePermission("purchase:manage")
    public Result<Void> flowApprove(@PathVariable Long id,
                                    @RequestBody(required = false) Map<String, String> body,
                                    HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        purchaseFlowService.approveAndSendToErp(id, userId, currentNickname(userId), commentOf(body));
        return Result.success();
    }

    /**
     * 驳回（打回）：提交申请 → 已打回。
     *
     * @param id      采购单ID
     * @param body    打回原因 {"comment":"..."}（可空 body）
     * @param request HTTP 请求
     * @return 空响应
     */
    @PostMapping("/{id}/flow/reject")
    @RequirePermission("purchase:manage")
    public Result<Void> flowReject(@PathVariable Long id,
                                   @RequestBody(required = false) Map<String, String> body,
                                   HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        purchaseFlowService.reject(id, userId, currentNickname(userId), commentOf(body));
        return Result.success();
    }

    /**
     * 打回后重新提交：已打回 → 提交申请。
     *
     * @param id      采购单ID
     * @param request HTTP 请求
     * @return 空响应
     */
    @PostMapping("/{id}/flow/resubmit")
    @RequirePermission("purchase:manage")
    public Result<Void> flowResubmit(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        purchaseFlowService.resubmit(id, userId, currentNickname(userId));
        return Result.success();
    }

    /**
     * 流程详情：流程实例 + 全过程轨迹 + 超级节点业务配置。
     *
     * @param id 采购单ID
     * @return {"flow":PurchaseFlow,"logs":List,"biz":IntegrationBiz}
     */
    @GetMapping("/{id}/flow")
    @RequirePermission("purchase:manage")
    public Result<Map<String, Object>> flowDetail(@PathVariable Long id) {
        return Result.success(purchaseFlowService.detail(id));
    }

    /**
     * 取请求体中的意见字段（body 可空）。
     *
     * @param body 请求体
     * @return 意见文本（可能为 null）
     */
    private String commentOf(Map<String, String> body) {
        return body == null ? null : body.get("comment");
    }

    /**
     * 查询当前用户昵称。
     *
     * @param userId 用户ID
     * @return 昵称（查询不到返回 null）
     */
    private String currentNickname(Long userId) {
        if (userId == null) {
            return null;
        }
        SysUser user = sysUserService.getById(userId);
        return user == null ? null : user.getNickname();
    }
}
