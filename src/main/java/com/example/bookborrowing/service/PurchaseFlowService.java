package com.example.bookborrowing.service;

import com.example.bookborrowing.entity.PurchaseFlow;

import java.util.List;
import java.util.Map;

/**
 * 采购流程引擎（致远 OA「超级节点」模式）。
 * 节点链：草稿(DRAFT) → 提交申请(SUBMIT) → 审批(APPROVE) → 超级节点(SUPER) → 完成(DONE)；
 * ERP 不同意或回调超时则打回(REJECTED)，打回后可重新提交回到 SUBMIT；采购单取消时流程置为已取消(CANCELED)。
 */
public interface PurchaseFlowService {

    /**
     * 提交申请：采购单草稿 → 已提交，流程进入 SUBMIT 节点（无流程实例则创建）。
     *
     * @param purchaseId 采购单ID
     * @param userId     操作人ID
     * @param userName   操作人姓名
     */
    void submitApply(Long purchaseId, Long userId, String userName);

    /**
     * 审批通过并下发 ERP：SUBMIT → APPROVE → 出站调用 ERP → SUPER（挂起等待回执）。
     * 出站失败整体回滚，流程停在 APPROVE 之前的状态（表单不落库、节点不动）。
     *
     * @param purchaseId 采购单ID
     * @param userId     审批人ID
     * @param userName   审批人姓名
     * @param comment    审批意见（可空）
     */
    void approveAndSendToErp(Long purchaseId, Long userId, String userName, String comment);

    /**
     * 驳回（审批环节打回）：SUBMIT → REJECTED，采购单保持流程中，可重新提交。
     *
     * @param purchaseId 采购单ID
     * @param userId     操作人ID
     * @param userName   操作人姓名
     * @param comment    打回原因（可空）
     */
    void reject(Long purchaseId, Long userId, String userName, String comment);

    /**
     * 打回后重新提交：REJECTED → SUBMIT，清空打回原因。
     *
     * @param purchaseId 采购单ID
     * @param userId     操作人ID
     * @param userName   操作人姓名
     */
    void resubmit(Long purchaseId, Long userId, String userName);

    /**
     * ERP 回执处理：同意 → 完成并自动入库；不同意 → 打回。流程已结束时不再重复入库。
     *
     * @param purchaseId 采购单ID
     * @param agreed     是否同意
     * @param comment    ERP 处理意见（可空）
     */
    void handleErpDecision(Long purchaseId, boolean agreed, String comment);

    /**
     * 查询处于超级节点、等待 ERP 回执的采购单ID集（回调超时巡检用）。
     *
     * @return 采购单ID列表
     */
    List<Long> listSuperNodeWaitPurchaseIds();

    /**
     * 单条流程的超时打回：若流程仍在超级节点（SUPER/进行中）且已超过配置的回调超时，则置为已打回（REJECTED/2），
     * 写 ERP_TIMEOUT 轨迹并通知申请人。已是其它节点（已打回/已完成/已取消）时直接跳过，天然幂等。
     * 每条独立事务，供定时任务逐条调用，一条失败不影响其它条。
     *
     * @param purchaseId 采购单ID
     * @return 实际执行了超时打回返回 true，未超时或状态已变化返回 false
     */
    boolean timeoutIfExpired(Long purchaseId);

    /**
     * 按采购单ID查询流程实例。
     *
     * @param purchaseId 采购单ID
     * @return 流程实例，未发起返回 null
     */
    PurchaseFlow getByPurchaseId(Long purchaseId);

    /**
     * 流程详情：流程实例 + 全过程轨迹 + 超级节点业务配置。
     *
     * @param purchaseId 采购单ID
     * @return {"flow":PurchaseFlow,"logs":List,"biz":IntegrationBiz}
     */
    Map<String, Object> detail(Long purchaseId);
}
