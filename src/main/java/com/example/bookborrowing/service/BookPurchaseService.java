package com.example.bookborrowing.service;

import com.example.bookborrowing.common.PageResult;
import com.example.bookborrowing.dto.PurchaseQueryDTO;
import com.example.bookborrowing.entity.BookPurchase;

/**
 * 图书采购业务接口。
 * 业务闭环：草稿 → 提交下发外部集成应用 → 对方回执 → 入库（增加或新增图书库存）。
 */
public interface BookPurchaseService {

    /**
     * 分页动态条件查询采购单。
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @param query    动态查询条件
     * @return 分页结果
     */
    PageResult<BookPurchase> page(int pageNum, int pageSize, PurchaseQueryDTO query);

    /**
     * 按ID查询采购单详情。
     *
     * @param id 采购单ID
     * @return 采购单实体
     */
    BookPurchase getById(Long id);

    /**
     * 新增采购单（草稿）。
     *
     * @param purchase 采购单实体
     * @param userId   申请人ID
     * @param userName 申请人姓名
     */
    void create(BookPurchase purchase, Long userId, String userName);

    /**
     * 修改采购单（仅草稿状态允许）。
     *
     * @param purchase 采购单实体
     */
    void update(BookPurchase purchase);

    /**
     * 删除采购单（逻辑删除，仅草稿/已取消允许）。
     *
     * @param id 采购单ID
     */
    void delete(Long id);

    /**
     * 提交采购单：草稿 → 已提交；若指定应用则下发集成表单，等待对方处理。
     *
     * @param id       采购单ID
     * @param appId    下发的集成应用ID（可空，空表示不下发外部平台）
     * @param userName 操作人姓名
     */
    void submit(Long id, Long appId, String userName);

    /**
     * 采购入库：已提交 → 已入库，匹配已有图书增加库存或新增图书。
     * 若已下发外部平台，需等待对方回执后方可入库。
     *
     * @param id     采购单ID
     * @param remark 入库备注（可空）
     */
    void stockIn(Long id, String remark);

    /**
     * 取消采购单：草稿/已提交 → 已取消。
     *
     * @param id 采购单ID
     */
    void cancel(Long id);
}
