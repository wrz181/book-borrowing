package com.example.bookborrowing.controller;

import com.example.bookborrowing.common.Result;
import com.example.bookborrowing.entity.BookCategory;
import com.example.bookborrowing.service.BookCategoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 图书分类接口：分类的增删改查。
 */
@RestController
@RequestMapping("/api/categories")
public class BookCategoryController {

    private final BookCategoryService bookCategoryService;

    public BookCategoryController(BookCategoryService bookCategoryService) {
        this.bookCategoryService = bookCategoryService;
    }

    /**
     * 查询全部分类，支持排序方式切换。
     *
     * @param sortBy 排序方式：default=创建顺序（默认），name=按名称，updateTime=按修改时间
     * @return 分类列表
     */
    @GetMapping
    public Result<List<BookCategory>> list(@RequestParam(required = false) String sortBy) {
        return Result.success(bookCategoryService.listAll(sortBy));
    }

    /**
     * 新增分类。
     *
     * @param category 分类实体
     * @return 空响应
     */
    @PostMapping
    public Result<Void> create(@RequestBody BookCategory category) {
        bookCategoryService.create(category);
        return Result.success();
    }

    /**
     * 更新分类。
     *
     * @param id       分类ID
     * @param category 分类实体
     * @return 空响应
     */
    @PostMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody BookCategory category) {
        category.setId(id);
        bookCategoryService.update(category);
        return Result.success();
    }

    /**
     * 删除分类。
     *
     * @param id 分类ID
     * @return 空响应
     */
    @PostMapping("/{id}/delete")
    public Result<Void> delete(@PathVariable Long id) {
        bookCategoryService.remove(id);
        return Result.success();
    }
}
