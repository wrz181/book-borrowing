package com.example.bookborrowing.service;

import com.example.bookborrowing.common.BusinessException;
import com.example.bookborrowing.common.PageResult;
import com.example.bookborrowing.dto.BookQueryDTO;
import com.example.bookborrowing.entity.Book;

/**
 * 图书业务接口。
 */
public interface BookService {

    /**
     * 分页动态条件查询图书。
     *
     * @param pageNum  页码（从1开始）
     * @param pageSize 每页条数
     * @param query    动态查询条件（图书名称/作者/分类/状态，可组合）
     * @return 分页结果
     */
    PageResult<Book> page(int pageNum, int pageSize, BookQueryDTO query);

    /**
     * 查询图书详情：图书主对象信息 + 该图书全部借阅记录（一对多关联查询）。
     *
     * @param id 图书ID
     * @return 图书实体（含借阅记录列表）
     */
    Book getDetail(Long id);

    /**
     * 新增图书。
     *
     * @param book 图书实体
     */
    void create(Book book);

    /**
     * 更新图书。
     *
     * @param book 图书实体
     */
    void update(Book book);

    /**
     * 删除图书：图书存在借阅记录时不允许删除。
     *
     * @param id 图书ID
     */
    void remove(Long id);
}
