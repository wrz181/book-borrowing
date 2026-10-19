package com.example.bookborrowing.controller;

import com.example.bookborrowing.annotation.RequirePermission;
import com.example.bookborrowing.common.PageResult;
import com.example.bookborrowing.common.Result;
import com.example.bookborrowing.dto.PurchaseQueryDTO;
import com.example.bookborrowing.entity.BookPurchase;
import com.example.bookborrowing.entity.SysUser;
import com.example.bookborrowing.service.BookPurchaseService;
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

/**
 * 图书采购接口：采购单增删改查、动态条件分页查询、提交下发、入库、取消。
 * 采购为集团管理职能：查看需 purchase:view，操作需 purchase:manage。
 */
@RestController
@RequestMapping("/api/purchases")
@RequirePermission("purchase:view")
public class BookPurchaseController {

    private final BookPurchaseService bookPurchaseService;

    private final SysUserService sysUserService;

    public BookPurchaseController(BookPurchaseService bookPurchaseService, SysUserService sysUserService) {
        this.bookPurchaseService = bookPurchaseService;
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
    @PutMapping("/{id}")
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
    @DeleteMapping("/{id}")
    @RequirePermission("purchase:manage")
    public Result<Void> delete(@PathVariable Long id) {
        bookPurchaseService.delete(id);
        return Result.success();
    }

    /**
     * 提交采购单：草稿 → 已提交；指定应用时下发集成表单，等待外部平台回执。
     *
     * @param id      采购单ID
     * @param appId   下发的集成应用ID（可空）
     * @param request HTTP 请求
     * @return 空响应
     */
    @PutMapping("/{id}/submit")
    @RequirePermission("purchase:manage")
    public Result<Void> submit(@PathVariable Long id,
                               @RequestParam(required = false) Long appId,
                               HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        bookPurchaseService.submit(id, appId, currentNickname(userId));
        return Result.success();
    }

    /**
     * 采购入库：已提交 → 已入库，匹配已有图书增加库存或新增图书。
     *
     * @param id     采购单ID
     * @param remark 入库备注（可空）
     * @return 空响应
     */
    @PutMapping("/{id}/stock-in")
    @RequirePermission("purchase:manage")
    public Result<Void> stockIn(@PathVariable Long id, @RequestParam(required = false) String remark) {
        bookPurchaseService.stockIn(id, remark);
        return Result.success();
    }

    /**
     * 取消采购单：草稿/已提交 → 已取消。
     *
     * @param id 采购单ID
     * @return 空响应
     */
    @PutMapping("/{id}/cancel")
    @RequirePermission("purchase:manage")
    public Result<Void> cancel(@PathVariable Long id) {
        bookPurchaseService.cancel(id);
        return Result.success();
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
