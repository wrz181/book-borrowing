package com.example.bookborrowing.service.impl;

import com.example.bookborrowing.common.BusinessException;
import com.example.bookborrowing.common.PageResult;
import com.example.bookborrowing.dto.BookQueryDTO;
import com.example.bookborrowing.entity.Book;
import com.example.bookborrowing.mapper.BookCategoryMapper;
import com.example.bookborrowing.mapper.BookMapper;
import com.example.bookborrowing.service.BookService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 图书业务实现。
 */
@Service
public class BookServiceImpl implements BookService {

    private final BookMapper bookMapper;

    private final BookCategoryMapper bookCategoryMapper;

    public BookServiceImpl(BookMapper bookMapper, BookCategoryMapper bookCategoryMapper) {
        this.bookMapper = bookMapper;
        this.bookCategoryMapper = bookCategoryMapper;
    }

    @Override
    public PageResult<Book> page(int pageNum, int pageSize, BookQueryDTO query) {
        int offset = (Math.max(pageNum, 1) - 1) * pageSize;
        List<Book> list = bookMapper.selectPage(offset, pageSize, query);
        long total = bookMapper.selectCount(query);
        return new PageResult<>(list, total, Math.max(pageNum, 1), pageSize);
    }

    @Override
    public Book getDetail(Long id) {
        Book book = bookMapper.selectDetailById(id);
        if (book == null) {
            throw new BusinessException("图书不存在");
        }
        return book;
    }

    @Override
    public void create(Book book) {
        // R02 关联对象校验：所属分类必须存在
        if (bookCategoryMapper.selectById(book.getCategoryId()) == null) {
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
    }

    @Override
    public void update(Book book) {
        Book exist = bookMapper.selectById(book.getId());
        if (exist == null) {
            throw new BusinessException("图书不存在");
        }
        if (bookCategoryMapper.selectById(book.getCategoryId()) == null) {
            throw new BusinessException("所属图书分类不存在");
        }
        bookMapper.updateById(book);
    }

    @Override
    public void remove(Long id) {
        Book exist = bookMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException("图书不存在");
        }
        // 逻辑删除：仅标记 is_deleted = 1，保留借阅记录历史，不物理删除行
        bookMapper.deleteById(id);
    }
}
