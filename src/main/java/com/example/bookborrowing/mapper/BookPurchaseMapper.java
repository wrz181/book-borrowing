package com.example.bookborrowing.mapper;

import com.example.bookborrowing.dto.PurchaseQueryDTO;
import com.example.bookborrowing.entity.BookPurchase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 图书采购单 Mapper：动态条件分页查询与状态流转。
 */
@Mapper
public interface BookPurchaseMapper {

    /**
     * 分页动态条件查询采购单（关联分类名称、应用名称）。
     *
     * @param offset 偏移量
     * @param limit  每页条数
     * @param query  动态查询条件
     * @return 采购单列表
     */
    List<BookPurchase> selectPage(@Param("offset") int offset,
                                 @Param("limit") int limit,
                                 @Param("query") PurchaseQueryDTO query);

    /**
     * 统计符合条件的采购单总数（与 selectPage 条件一致）。
     *
     * @param query 动态查询条件
     * @return 总记录数
     */
    long selectCount(@Param("query") PurchaseQueryDTO query);

    /**
     * 按ID查询采购单。
     *
     * @param id 采购单ID
     * @return 采购单实体，不存在返回 null
     */
    BookPurchase selectById(Long id);

    /**
     * 新增采购单。
     *
     * @param purchase 采购单实体
     * @return 影响行数
     */
    int insert(BookPurchase purchase);

    /**
     * 更新采购单可编辑字段（仅草稿状态下允许）。
     *
     * @param purchase 采购单实体
     * @return 影响行数
     */
    int updateById(BookPurchase purchase);

    /**
     * 逻辑删除采购单。
     *
     * @param id 采购单ID
     * @return 影响行数
     */
    int deleteById(Long id);

    /**
     * 标记为已提交：草稿 → 已提交，记录下发应用与集成表单。
     *
     * @param id          采购单ID
     * @param appId       下发的应用ID（可空）
     * @param formId      集成表单ID（可空）
     * @param submitTime  提交时间
     * @return 影响行数
     */
    int markSubmitted(@Param("id") Long id,
                      @Param("appId") Long appId,
                      @Param("formId") Long formId,
                      @Param("submitTime") LocalDateTime submitTime);

    /**
     * 标记为已入库：已提交 → 已入库，记录入库图书与结果说明。
     *
     * @param id          采购单ID
     * @param stockInTime 入库时间
     * @param bookId      入库图书ID
     * @param remark      入库结果说明
     * @return 影响行数
     */
    int markStockIn(@Param("id") Long id,
                    @Param("stockInTime") LocalDateTime stockInTime,
                    @Param("bookId") Long bookId,
                    @Param("remark") String remark);

    /**
     * 标记为已取消：草稿/已提交 → 已取消。
     *
     * @param id         采购单ID
     * @param cancelTime 取消时间
     * @return 影响行数
     */
    int markCanceled(@Param("id") Long id, @Param("cancelTime") LocalDateTime cancelTime);
}
