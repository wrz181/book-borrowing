package com.example.bookborrowing.service;

import com.example.bookborrowing.common.PageResult;
import com.example.bookborrowing.dto.PurchaseQueryDTO;
import com.example.bookborrowing.entity.BookPurchase;

/**
 * 图书采购业务接口。
 * 业务闭环：草稿 → 提交申请 → 审批下发 ERP（超级节点）→ 回执 → 入库（增加或新增图书库存）。
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
     * 人工补偿入库（异常补偿用）：必须存在流程实例，且流程已完成（current_node = DONE）、采购单处于已提交(1)。
     * 无流程实例的采购单一律拒绝，须走『提交申请 → 审批下发 ERP』流程入库。
     *
     * @param id     采购单ID
     * @param remark 入库备注（可空）
     */
    void stockIn(Long id, String remark);

    /**
     * 流程引擎内部入库（ERP 审批通过自动入库）。
     * 不受「已提交/已回执」闸门限制（由流程引擎控制），但仍保持幂等：已入库时抛业务异常而不二次加库存。
     *
     * @param id     采购单ID
     * @param remark 入库备注（可空）
     */
    void stockInInternal(Long id, String remark);

    /**
     * 取消采购单：草稿/已提交 → 已取消，同时把未结束的流程实例置为已取消（CANCELED/3）并写轨迹。
     *
     * @param id       采购单ID
     * @param userId   操作人ID（可空）
     * @param userName 操作人姓名（可空）
     */
    void cancel(Long id, Long userId, String userName);
}
