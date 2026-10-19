package com.example.bookborrowing.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 采购流程节点流转轨迹实体，对应表 purchase_flow_log。
 * 每次节点流转（提交申请、审批出站、ERP 同意/打回、重新提交、取消）写一条记录，用于展示流程轨迹。
 */
@Data
public class PurchaseFlowLog {

    /** 动作：提交申请 */
    public static final String ACTION_SUBMIT_APPLY = "SUBMIT_APPLY";

    /** 动作：审批通过并出站调用 ERP */
    public static final String ACTION_APPROVE_SEND_ERP = "APPROVE_SEND_ERP";

    /** 动作：ERP 同意 */
    public static final String ACTION_ERP_AGREE = "ERP_AGREE";

    /** 动作：ERP 打回 */
    public static final String ACTION_ERP_REJECT = "ERP_REJECT";

    /** 动作：打回后重新提交 */
    public static final String ACTION_RESUBMIT = "RESUBMIT";

    /** 动作：取消 */
    public static final String ACTION_CANCEL = "CANCEL";

    /** 动作：超级节点回调超时，流程自动打回 */
    public static final String ACTION_ERP_TIMEOUT = "ERP_TIMEOUT";

    /** 主键ID */
    private Long id;

    /** 流程实例ID（purchase_flow.id） */
    private Long flowId;

    /** 来源节点 */
    private String fromNode;

    /** 目标节点 */
    private String toNode;

    /** 动作：SUBMIT_APPLY/APPROVE_SEND_ERP/ERP_AGREE/ERP_REJECT/RESUBMIT/CANCEL/ERP_TIMEOUT */
    private String action;

    /** 操作人ID */
    private Long operatorId;

    /** 操作人姓名 */
    private String operatorName;

    /** 备注说明 */
    private String comment;

    /** 创建时间 */
    private LocalDateTime createTime;
}
