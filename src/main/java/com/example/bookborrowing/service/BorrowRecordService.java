package com.example.bookborrowing.service;

import com.example.bookborrowing.common.PageResult;
import com.example.bookborrowing.dto.BorrowRecordQueryDTO;
import com.example.bookborrowing.entity.BorrowRecord;

/**
 * 借阅记录业务接口。
 */
public interface BorrowRecordService {

    /**
     * 分页动态条件查询借阅记录（关联图书名称、作者、分类名称）。
     *
     * @param pageNum  页码（从1开始）
     * @param pageSize 每页条数
     * @param query    动态查询条件（图书名称/借阅人/状态/借阅日期范围，可组合）
     * @return 分页结果
     */
    PageResult<BorrowRecord> page(int pageNum, int pageSize, BorrowRecordQueryDTO query);

    /**
     * 查询借阅记录详情。
     *
     * @param id 记录ID
     * @return 借阅记录实体（含关联图书信息）
     */
    BorrowRecord getDetail(Long id);

    /**
     * 新增借阅记录：校验图书存在、R03 重复借阅规则、库存，并扣减库存。
     *
     * @param record 借阅记录实体
     */
    void create(BorrowRecord record);

    /**
     * 更新借阅记录：同样执行 R03 重复借阅规则校验。
     *
     * @param record 借阅记录实体
     */
    void update(BorrowRecord record);

    /**
     * 删除借阅记录：未归还记录删除时恢复图书库存。
     *
     * @param id 记录ID
     */
    void remove(Long id);

    /**
     * 归还图书：设置归还日期、状态改为已归还，并恢复库存。
     *
     * @param id 记录ID
     */
    void returnBook(Long id);
}
