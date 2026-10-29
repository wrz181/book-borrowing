package com.example.bookborrowing.service;

import com.example.bookborrowing.common.PageResult;
import com.example.bookborrowing.dto.BorrowRecordQueryDTO;
import com.example.bookborrowing.entity.BorrowRecord;

/**
 * 借阅记录业务接口。
 * <p>
 * 状态流转：0-借阅待确认 →（管理员确认）1-借阅中 →（学生申请归还）4-归还待确认 →（管理员确认）2-已归还；
 * 借阅待确认可驳回为 3-已驳回，归还待确认可驳回回 1-借阅中。
 */
public interface BorrowRecordService {

    /**
     * 分页动态条件查询借阅记录（关联图书名称、作者、分类名称）。
     *
     * @param pageNum         页码（从1开始）
     * @param pageSize        每页条数
     * @param query           动态查询条件（图书名称/借阅人/状态/借阅日期范围，可组合）
     * @param scopeBorrowerId 数据范围：非空时仅查询本人记录；null 表示不限范围（由 Controller 按角色计算）
     * @return 分页结果
     */
    PageResult<BorrowRecord> page(int pageNum, int pageSize, BorrowRecordQueryDTO query, Long scopeBorrowerId);

    /**
     * 查询借阅记录详情。
     *
     * @param id              记录ID
     * @param scopeBorrowerId 数据范围：非空时仅当记录属于该用户才返回；null 表示不限范围
     * @return 借阅记录实体（含关联图书信息）
     */
    BorrowRecord getDetail(Long id, Long scopeBorrowerId);

    /**
     * 提交阅览申请：生成「借阅待确认」记录，不扣库存，等待管理员确认。
     *
     * @param record        借阅记录实体
     * @param currentUserId 当前登录用户ID（学生强制作为借阅人）
     * @param manage        当前用户是否为管理员
     */
    void create(BorrowRecord record, Long currentUserId, boolean manage);

    /**
     * 更新借阅记录：仅允许修改「借阅待确认」状态的记录。
     *
     * @param record 借阅记录实体
     */
    void update(BorrowRecord record);

    /**
     * 删除借阅记录：已占用库存的记录删除时恢复库存。
     *
     * @param id 记录ID
     */
    void remove(Long id);

    /**
     * 发起归还：借阅中(1) → 归还待确认(4)。
     *
     * @param id            记录ID
     * @param currentUserId 当前登录用户ID
     * @param manage        当前用户是否为管理员；学生只能操作本人记录
     */
    void requestReturn(Long id, Long currentUserId, boolean manage);

    /**
     * 管理员确认借阅：借阅待确认(0) → 借阅中(1)，扣减库存，库存为 0 时图书状态置为不可借。
     *
     * @param id         记录ID
     * @param operatorId 操作人ID
     */
    void approve(Long id, Long operatorId);

    /**
     * 管理员驳回借阅：借阅待确认(0) → 已驳回(3)。
     *
     * @param id         记录ID
     * @param reason     驳回原因（可空）
     * @param operatorId 操作人ID
     */
    void reject(Long id, String reason, Long operatorId);

    /**
     * 管理员确认归还：归还待确认(4) → 已归还(2)，恢复库存并恢复图书可借状态。
     *
     * @param id         记录ID
     * @param operatorId 操作人ID
     */
    void confirmReturn(Long id, Long operatorId);

    /**
     * 管理员驳回归还：归还待确认(4) → 借阅中(1)。
     *
     * @param id         记录ID
     * @param reason     驳回原因（可空）
     * @param operatorId 操作人ID
     */
    void rejectReturn(Long id, String reason, Long operatorId);
}
