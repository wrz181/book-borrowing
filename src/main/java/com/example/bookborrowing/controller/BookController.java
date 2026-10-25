package com.example.bookborrowing.controller;

import com.example.bookborrowing.common.PageResult;
import com.example.bookborrowing.common.Result;
import com.example.bookborrowing.dto.BookQueryDTO;
import com.example.bookborrowing.entity.Book;
import com.example.bookborrowing.service.BookService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 图书接口：图书增删改查、动态条件分页查询、图书详情一对多关联查询。
 */
@RestController
@RequestMapping("/api/books")
public class BookController {

    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    /**
     * 分页动态条件查询图书：图书名称、作者、分类、状态可单独或组合使用。
     *
     * @param pageNum  页码，默认1
     * @param pageSize 每页条数，默认10
     * @param query    动态查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    public Result<PageResult<Book>> page(@RequestParam(defaultValue = "1") int pageNum,
                                         @RequestParam(defaultValue = "10") int pageSize,
                                         BookQueryDTO query) {
        return Result.success(bookService.page(pageNum, pageSize, query));
    }

    /**
     * 图书详情：图书主对象 + 该图书全部借阅记录（一对多关联查询，个人指定功能 F2）。
     *
     * @param id 图书ID
     * @return 图书实体（含借阅记录列表）
     */
    @GetMapping("/{id}")
    public Result<Book> detail(@PathVariable Long id) {
        return Result.success(bookService.getDetail(id));
    }

    /**
     * 新增图书。
     *
     * @param book 图书实体
     * @return 空响应
     */
    @PostMapping
    public Result<Void> create(@RequestBody Book book) {
        bookService.create(book);
        return Result.success();
    }

    /**
     * 更新图书。
     *
     * @param id   图书ID
     * @param book 图书实体
     * @return 空响应
     */
    @PostMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Book book) {
        book.setId(id);
        bookService.update(book);
        return Result.success();
    }

    /**
     * 删除图书：存在借阅记录的图书不允许删除。
     *
     * @param id 图书ID
     * @return 空响应
     */
    @PostMapping("/{id}/delete")
    public Result<Void> delete(@PathVariable Long id) {
        bookService.remove(id);
        return Result.success();
    }
}
