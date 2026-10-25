package com.example.bookborrowing.mapper;

import com.example.bookborrowing.dto.BookQueryDTO;
import com.example.bookborrowing.entity.Book;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 图书 Mapper：含动态条件查询与图书-借阅记录一对多关联查询。
 */
@Mapper
public interface BookMapper {

    /**
     * 分页动态条件查询图书（关联分类名称）。
     *
     * @param offset 偏移量
     * @param limit  每页条数
     * @param query  动态查询条件
     * @return 图书列表
     */
    List<Book> selectPage(@Param("offset") int offset,
                          @Param("limit") int limit,
                          @Param("query") BookQueryDTO query);

    /**
     * 统计符合条件的图书总数（与 selectPage 条件一致）。
     *
     * @param query 动态查询条件
     * @return 总记录数
     */
    long selectCount(@Param("query") BookQueryDTO query);

    /**
     * 按ID查询图书基础信息（含分类名称）。
     *
     * @param id 图书ID
     * @return 图书实体，不存在返回 null
     */
    Book selectById(Long id);

    /**
     * 按ID查询图书详情：图书主对象 + 其全部借阅记录（一对多关联查询）。
     *
     * @param id 图书ID
     * @return 图书实体（borrowRecords 已填充），不存在返回 null
     */
    Book selectDetailById(Long id);

    /**
     * 新增图书。
     *
     * @param book 图书实体
     * @return 影响行数
     */
    int insert(Book book);

    /**
     * 更新图书。
     *
     * @param book 图书实体
     * @return 影响行数
     */
    int updateById(Book book);

    /**
     * 按ID删除图书。
     *
     * @param id 图书ID
     * @return 影响行数
     */
    int deleteById(Long id);

    /**
     * 扣减图书库存（借阅时使用）。
     *
     * @param id 图书ID
     * @return 影响行数
     */
    int deductStock(Long id);

    /**
     * 增加图书库存（归还/删除未归还记录时使用）。
     *
     * @param id 图书ID
     * @return 影响行数
     */
    int addStock(Long id);

    /**
     * 更新图书状态（1-在架可借 0-已下架/已借完），借阅审批联动使用。
     *
     * @param id     图书ID
     * @param status 目标状态
     * @return 影响行数
     */
    int updateStatus(@Param("id") Long id, @Param("status") int status);
}
