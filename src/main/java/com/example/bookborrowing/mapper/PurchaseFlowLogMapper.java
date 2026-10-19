package com.example.bookborrowing.mapper;

import com.example.bookborrowing.entity.PurchaseFlowLog;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 采购流程节点流转轨迹 Mapper：只追加与按流程查询，轨迹不修改不删除。
 */
@Mapper
public interface PurchaseFlowLogMapper {

    /**
     * 新增一条流转轨迹。
     *
     * @param flowLog 轨迹实体
     * @return 影响行数
     */
    int insert(PurchaseFlowLog flowLog);

    /**
     * 按流程实例ID查询轨迹，按 create_time、id 升序返回，用于展示完整流转链路。
     *
     * @param flowId 流程实例ID
     * @return 轨迹列表
     */
    List<PurchaseFlowLog> selectByFlowId(Long flowId);

    /**
     * 按流程实例ID物理删除轨迹（删除采购单时级联清理）。
     *
     * @param flowId 流程实例ID
     * @return 影响行数
     */
    int deleteByFlowId(Long flowId);
}
