package com.example.bookborrowing.mapper;

import com.example.bookborrowing.entity.BookCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 图书分类 Mapper。
 */
@Mapper
public interface BookCategoryMapper {

    /**
     * 查询全部分类，按指定排序规则排序。
     *
     * @param orderBy 排序子句（由 Service 层白名单控制，如 "id ASC"、"category_name ASC"、"update_time DESC"）
     * @return 分类列表
     */
    List<BookCategory> selectAll(@Param("orderBy") String orderBy);

    /**
     * 按ID查询分类。
     *
     * @param id 分类ID
     * @return 分类实体，不存在返回 null
     */
    BookCategory selectById(Long id);

    /**
     * 新增分类。
     *
     * @param category 分类实体
     * @return 影响行数
     */
    int insert(BookCategory category);

    /**
     * 更新分类。
     *
     * @param category 分类实体
     * @return 影响行数
     */
    int updateById(BookCategory category);

    /**
     * 按ID删除分类。
     *
     * @param id 分类ID
     * @return 影响行数
     */
    int deleteById(Long id);

    /**
     * 统计分类下关联的图书数量。
     *
     * @param categoryId 分类ID
     * @return 图书数量
     */
    int countBookByCategoryId(Long categoryId);
}
