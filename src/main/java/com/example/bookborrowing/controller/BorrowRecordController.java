package com.example.bookborrowing.controller;

import com.example.bookborrowing.common.PageResult;
import com.example.bookborrowing.common.Result;
import com.example.bookborrowing.dto.BorrowRecordQueryDTO;
import com.example.bookborrowing.entity.BorrowRecord;
import com.example.bookborrowing.service.BorrowRecordService;
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
 * 借阅记录接口：借阅的增删改查、动态条件分页查询、归还操作。
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
     * @return 分页结果
     */
    @GetMapping("/page")
    public Result<PageResult<BorrowRecord>> page(@RequestParam(defaultValue = "1") int pageNum,
                                                 @RequestParam(defaultValue = "10") int pageSize,
                                                 BorrowRecordQueryDTO query) {
        return Result.success(borrowRecordService.page(pageNum, pageSize, query));
    }

    /**
     * 查询借阅记录详情（含关联图书名称、作者、分类名称）。
     *
     * @param id 记录ID
     * @return 借阅记录实体
     */
    @GetMapping("/{id}")
    public Result<BorrowRecord> detail(@PathVariable Long id) {
        return Result.success(borrowRecordService.getDetail(id));
    }

    /**
     * 新增借阅记录：执行 R03 重复借阅校验与库存扣减。
     *
     * @param record 借阅记录实体
     * @return 空响应
     */
    @PostMapping
    public Result<Void> create(@RequestBody BorrowRecord record) {
        borrowRecordService.create(record);
        return Result.success();
    }

    /**
     * 更新借阅记录。
     *
     * @param id     记录ID
     * @param record 借阅记录实体
     * @return 空响应
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody BorrowRecord record) {
        record.setId(id);
        borrowRecordService.update(record);
        return Result.success();
    }

    /**
     * 删除借阅记录：未归还记录删除时恢复库存。
     *
     * @param id 记录ID
     * @return 空响应
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        borrowRecordService.remove(id);
        return Result.success();
    }

    /**
     * 归还图书。
     *
     * @param id 记录ID
     * @return 空响应
     */
    @PutMapping("/{id}/return")
    public Result<Void> returnBook(@PathVariable Long id) {
        borrowRecordService.returnBook(id);
        return Result.success();
    }
}
