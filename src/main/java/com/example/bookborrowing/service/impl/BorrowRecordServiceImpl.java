package com.example.bookborrowing.service.impl;

import com.example.bookborrowing.common.BusinessException;
import com.example.bookborrowing.common.PageResult;
import com.example.bookborrowing.dto.BorrowRecordQueryDTO;
import com.example.bookborrowing.entity.Book;
import com.example.bookborrowing.entity.BorrowRecord;
import com.example.bookborrowing.mapper.BookMapper;
import com.example.bookborrowing.mapper.BorrowRecordMapper;
import com.example.bookborrowing.service.BorrowRecordService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 借阅记录业务实现：R03 重复业务校验与库存联动。
 */
@Service
public class BorrowRecordServiceImpl implements BorrowRecordService {

    private final BorrowRecordMapper borrowRecordMapper;

    private final BookMapper bookMapper;

    public BorrowRecordServiceImpl(BorrowRecordMapper borrowRecordMapper, BookMapper bookMapper) {
        this.borrowRecordMapper = borrowRecordMapper;
        this.bookMapper = bookMapper;
    }

    @Override
    public PageResult<BorrowRecord> page(int pageNum, int pageSize, BorrowRecordQueryDTO query) {
        int offset = (Math.max(pageNum, 1) - 1) * pageSize;
        List<BorrowRecord> list = borrowRecordMapper.selectPage(offset, pageSize, query);
        long total = borrowRecordMapper.selectCount(query);
        return new PageResult<>(list, total, Math.max(pageNum, 1), pageSize);
    }

    @Override
    public BorrowRecord getDetail(Long id) {
        BorrowRecord record = borrowRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException("借阅记录不存在");
        }
        return record;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(BorrowRecord record) {
        validateAndPrepare(record, null);
        // 新增记录默认借阅中
        record.setStatus(1);
        record.setReturnDate(null);
        borrowRecordMapper.insert(record);
        // 扣减图书库存
        bookMapper.deductStock(record.getBookId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(BorrowRecord record) {
        BorrowRecord exist = borrowRecordMapper.selectById(record.getId());
        if (exist == null) {
            throw new BusinessException("借阅记录不存在");
        }
        validateAndPrepare(record, record.getId());
        borrowRecordMapper.updateById(record);
    }

    /**
     * 新增/修改公共校验：图书存在性、R03 重复借阅规则、未归还记录保护。
     *
     * @param record    借阅记录
     * @param excludeId 修改时排除自身的记录ID，新增时传 null
     */
    private void validateAndPrepare(BorrowRecord record, Long excludeId) {
        if (record.getBookId() == null) {
            throw new BusinessException("请选择借阅图书");
        }
        // R02 关联对象校验：借阅图书必须存在
        Book book = bookMapper.selectById(record.getBookId());
        if (book == null) {
            throw new BusinessException("借阅图书不存在");
        }
        if (record.getBorrowerNo() == null || record.getBorrowerNo().isBlank()) {
            throw new BusinessException("借阅人学号/借阅证号不能为空");
        }
        if (record.getBorrowerName() == null || record.getBorrowerName().isBlank()) {
            throw new BusinessException("借阅人姓名不能为空");
        }
        if (record.getBorrowDate() == null) {
            record.setBorrowDate(LocalDate.now());
        }
        if (record.getDueDate() == null) {
            throw new BusinessException("应还日期不能为空");
        }
        if (record.getDueDate().isBefore(record.getBorrowDate())) {
            throw new BusinessException("应还日期不能早于借阅日期");
        }
        // R03 重复业务校验：同一借阅人、同一图书、同一借阅日期不得重复生成借阅记录
        int duplicate = borrowRecordMapper.countByBorrowerBookDate(
                record.getBookId(), record.getBorrowerNo(), record.getBorrowDate(), excludeId);
        if (duplicate > 0) {
            throw new BusinessException("同一借阅人同一天不能重复借阅同一本图书");
        }
        // 业务保护：同一借阅人对同一图书存在未归还记录时不允许再借
        int unreturned = borrowRecordMapper.countUnreturned(
                record.getBookId(), record.getBorrowerNo(), excludeId);
        if (unreturned > 0) {
            throw new BusinessException("该借阅人存在该图书的未归还记录，不能重复借阅");
        }
        // 库存校验（修改记录时借阅日期不变，库存已在借出时扣减，这里仅对新增生效）
        if (excludeId == null && (book.getStock() == null || book.getStock() <= 0)) {
            throw new BusinessException("图书库存不足，不能借阅");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        BorrowRecord exist = borrowRecordMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException("借阅记录不存在");
        }
        borrowRecordMapper.deleteById(id);
        // 删除未归还记录时恢复图书库存
        if (exist.getStatus() != null && exist.getStatus() == 1) {
            bookMapper.addStock(exist.getBookId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void returnBook(Long id) {
        BorrowRecord exist = borrowRecordMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException("借阅记录不存在");
        }
        if (exist.getStatus() == null || exist.getStatus() != 1) {
            throw new BusinessException("该记录已归还，不能重复归还");
        }
        int rows = borrowRecordMapper.returnRecord(id, LocalDate.now());
        if (rows == 0) {
            throw new BusinessException("归还失败，记录状态已变化");
        }
        // 归还后恢复图书库存
        bookMapper.addStock(exist.getBookId());
    }
}
