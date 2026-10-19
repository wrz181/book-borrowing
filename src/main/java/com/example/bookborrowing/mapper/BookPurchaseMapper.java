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
     * 标记为已回执：已提交 → 已回执（外部平台回执集成表单后按表单ID反查回写）。
     *
     * @param formId 集成表单ID
     * @return 影响行数，0 表示没有关联该表单的待回执采购单
     */
    int markAcknowledged(@Param("formId") Long formId);

    /**
     * 按集成表单ID反查采购单（ERP 回执时定位采购单与流程实例）。
     *
     * @param formId 集成表单ID
     * @return 采购单，未关联返回 null
     */
    BookPurchase selectByIntegrationFormId(@Param("formId") Long formId);

    /**
     * 流程审批下发后回写集成应用与集成表单关联，状态保持已提交（等待 ERP 回执）。
     *
     * @param id         采购单ID
     * @param appId      集成应用ID
     * @param formId     集成表单ID
     * @param submitTime 提交时间
     * @return 影响行数，0 表示采购单已不在已提交状态
     */
    int markFormIssued(@Param("id") Long id,
                       @Param("appId") Long appId,
                       @Param("formId") Long formId,
                       @Param("submitTime") LocalDateTime submitTime);

    /**
     * 流程被打回后回退采购单状态：已回执 → 已提交（保持流程中，可重新提交后再次下发）。
     *
     * @param id 采购单ID
     * @return 影响行数
     */
    int markReopened(@Param("id") Long id);

    /**
     * 标记为已入库：已提交/已回执 → 已入库，记录入库图书与结果说明。
     * 闸门（须流程已完成）由 Service 层控制，此处只保证已入库记录不被二次加库存。
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
     * 流程引擎内部入库：不受「已回执」表单闸门限制（闸门由流程节点控制），
     * 但仍以状态条件更新保证幂等（已入库时影响行数为 0）。
     *
     * @param id          采购单ID
     * @param stockInTime 入库时间
     * @param bookId      入库图书ID
     * @param remark      入库结果说明
     * @return 影响行数，0 表示采购单已入库或状态不允许入库
     */
    int markStockInInternal(@Param("id") Long id,
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

    /**
     * 查询处于超级节点、等待 ERP 回执的采购单ID集（回调超时巡检用）。
     * 条件：流程 current_node = SUPER、node_status = 0、erp_submit_time 非空、双方均未删除。
     *
     * @return 采购单ID列表，按进入超级节点时间正序
     */
    List<Long> selectSuperNodeWaitPurchaseIds();
}
