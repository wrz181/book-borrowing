package com.example.bookborrowing.service;

import com.example.bookborrowing.entity.BookCategory;

import java.util.List;

/**
 * 图书分类业务接口。
 */
public interface BookCategoryService {

    /**
     * 查询全部分类。
     *
     * @param sortBy 排序方式：null/default=按创建顺序（新增排在最后），name=按名称，updateTime=按修改时间倒序
     * @return 分类列表
     */
    List<BookCategory> listAll(String sortBy);

    /**
     * 新增分类。
     *
     * @param category 分类实体
     */
    void create(BookCategory category);

    /**
     * 更新分类。
     *
     * @param category 分类实体
     */
    void update(BookCategory category);

    /**
     * 删除分类：分类下存在图书时不允许删除。
     *
     * @param id 分类ID
     */
    void remove(Long id);
}
