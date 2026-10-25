package com.example.bookborrowing.service.impl;

import com.example.bookborrowing.common.BusinessException;
import com.example.bookborrowing.common.PageResult;
import com.example.bookborrowing.dto.BookQueryDTO;
import com.example.bookborrowing.entity.Book;
import com.example.bookborrowing.mapper.BookCategoryMapper;
import com.example.bookborrowing.mapper.BookMapper;
import com.example.bookborrowing.mapper.BorrowRecordMapper;
import com.example.bookborrowing.service.BookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 图书业务实现。
 */
@Service
public class BookServiceImpl implements BookService {

    private static final Logger log = LoggerFactory.getLogger(BookServiceImpl.class);

    private final BookMapper bookMapper;

    private final BookCategoryMapper bookCategoryMapper;

    private final BorrowRecordMapper borrowRecordMapper;

    public BookServiceImpl(BookMapper bookMapper, BookCategoryMapper bookCategoryMapper,
                           BorrowRecordMapper borrowRecordMapper) {
        this.bookMapper = bookMapper;
        this.bookCategoryMapper = bookCategoryMapper;
        this.borrowRecordMapper = borrowRecordMapper;
    }

    @Override
    public PageResult<Book> page(int pageNum, int pageSize, BookQueryDTO query) {
        int offset = (Math.max(pageNum, 1) - 1) * pageSize;
        List<Book> list = bookMapper.selectPage(offset, pageSize, query);
        long total = bookMapper.selectCount(query);
        log.info("分页查询图书：第 {} 页，每页 {} 条，共 {} 条", Math.max(pageNum, 1), pageSize, total);
        return new PageResult<>(list, total, Math.max(pageNum, 1), pageSize);
    }

    @Override
    public Book getDetail(Long id) {
        Book book = bookMapper.selectDetailById(id);
        if (book == null) {
            log.warn("图书详情不存在：id={}", id);
            throw new BusinessException("图书不存在");
        }
        return book;
    }

    @Override
    public void create(Book book) {
        // R02 关联对象校验：所属分类必须存在
        if (bookCategoryMapper.selectById(book.getCategoryId()) == null) {
            log.warn("新增图书失败：所属分类不存在，categoryId={}", book.getCategoryId());
            throw new BusinessException("所属图书分类不存在");
        }
        if (book.getStock() == null || book.getStock() < 0) {
            book.setStock(0);
        }
        if (book.getPrice() == null || book.getPrice().signum() < 0) {
            book.setPrice(java.math.BigDecimal.ZERO);
        }
        if (book.getStatus() == null) {
            book.setStatus(1);
        }
        bookMapper.insert(book);
        log.info("新增图书成功：id={}，书名={}", book.getId(), book.getBookName());
    }

    @Override
    public void update(Book book) {
        Book exist = bookMapper.selectById(book.getId());
        if (exist == null) {
            log.warn("修改图书失败：图书不存在，id={}", book.getId());
            throw new BusinessException("图书不存在");
        }
        if (bookCategoryMapper.selectById(book.getCategoryId()) == null) {
            log.warn("修改图书失败：所属分类不存在，id={}，categoryId={}", book.getId(), book.getCategoryId());
            throw new BusinessException("所属图书分类不存在");
        }
        bookMapper.updateById(book);
        log.info("修改图书成功：id={}，书名={}", book.getId(), book.getBookName());
    }

    @Override
    public void remove(Long id) {
        Book exist = bookMapper.selectById(id);
        if (exist == null) {
            log.warn("删除图书失败：图书不存在，id={}", id);
            throw new BusinessException("图书不存在");
        }
        // 存在未归还借阅记录的图书不允许删除，保证借阅业务数据完整
        if (borrowRecordMapper.countUnreturnedByBook(id) > 0) {
            log.warn("删除图书失败：存在未归还借阅记录，id={}，书名={}", id, exist.getBookName());
            throw new BusinessException("该图书存在未归还的借阅记录，不能删除");
        }
        // 逻辑删除：仅标记 is_deleted = 1，保留借阅记录历史，不物理删除行
        bookMapper.deleteById(id);
        log.info("删除图书成功：id={}，书名={}", id, exist.getBookName());
    }
}
