package com.example.bookborrowing.mapper;

import com.example.bookborrowing.entity.PurchaseFlow;
import org.apache.ibatis.annotations.Mapper;

/**
 * 采购流程实例 Mapper：一张采购单一条流程实例，按采购单ID存取。
 */
@Mapper
public interface PurchaseFlowMapper {

    /**
     * 按采购单ID查询流程实例（未删除）。
     *
     * @param purchaseId 采购单ID
     * @return 流程实例，不存在返回 null
     */
    PurchaseFlow selectByPurchaseId(Long purchaseId);

    /**
     * 按ID查询流程实例（未删除）。
     *
     * @param id 流程实例ID
     * @return 流程实例，不存在返回 null
     */
    PurchaseFlow selectById(Long id);

    /**
     * 新增流程实例。
     *
     * @param flow 流程实例
     * @return 影响行数
     */
    int insert(PurchaseFlow flow);

    /**
     * 按ID更新流程实例：只更新传入的非空字段，未传字段保持库中原值。
     *
     * @param flow 流程实例
     * @return 影响行数
     */
    int updateById(PurchaseFlow flow);

    /**
     * 按采购单ID逻辑删除流程实例。
     *
     * @param purchaseId 采购单ID
     * @return 影响行数
     */
    int deleteByPurchaseId(Long purchaseId);

    /**
     * 按采购单ID物理删除流程实例（删除草稿/已取消采购单时与其轨迹一起清理）。
     *
     * @param purchaseId 采购单ID
     * @return 影响行数
     */
    int deletePhysicallyByPurchaseId(Long purchaseId);
}
