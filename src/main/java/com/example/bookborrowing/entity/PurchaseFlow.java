package com.example.bookborrowing.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 采购流程实例实体，对应表 purchase_flow。
 * 一张采购单对应一条流程实例，记录当前所在节点与超级节点（ERP）交互的关键时间点。
 */
@Data
public class PurchaseFlow {

    /** 节点：草稿 */
    public static final String NODE_DRAFT = "DRAFT";

    /** 节点：提交申请 */
    public static final String NODE_SUBMIT = "SUBMIT";

    /** 节点：审批 */
    public static final String NODE_APPROVE = "APPROVE";

    /** 节点：超级节点（出站调用 ERP 并挂起等待回执） */
    public static final String NODE_SUPER = "SUPER";

    /** 节点：完成 */
    public static final String NODE_DONE = "DONE";

    /** 节点：已打回 */
    public static final String NODE_REJECTED = "REJECTED";

    /** 节点：已取消（采购单取消时同步终止流程） */
    public static final String NODE_CANCELED = "CANCELED";

    /** 节点结果：进行中 */
    public static final int NODE_STATUS_RUNNING = 0;

    /** 节点结果：已同意 */
    public static final int NODE_STATUS_AGREED = 1;

    /** 节点结果：已打回 */
    public static final int NODE_STATUS_REJECTED = 2;

    /** 节点结果：已取消 */
    public static final int NODE_STATUS_CANCELED = 3;

    /** 主键ID */
    private Long id;

    /** 采购单ID（book_purchase.id，唯一） */
    private Long purchaseId;

    /** 业务类型，如 BOOK_PURCHASE */
    private String bizType;

    /** 超级节点绑定的集成应用ID */
    private Long appId;

    /** 当前节点：DRAFT/SUBMIT/APPROVE/SUPER/DONE/REJECTED/CANCELED */
    private String currentNode;

    /** 节点结果：0-进行中 1-已同意 2-已打回 3-已取消 */
    private Integer nodeStatus;

    /** 调用 ERP 后拿到的表单ID */
    private Long erpFormId;

    /** 申请人ID */
    private Long applicantId;

    /** 申请人姓名 */
    private String applicantName;

    /** 流程发起时间 */
    private LocalDateTime startTime;

    /** 审批通过时间 */
    private LocalDateTime approveTime;

    /** 进入超级节点（出站调用 ERP）时间 */
    private LocalDateTime erpSubmitTime;

    /** ERP 回执时间 */
    private LocalDateTime erpCallbackTime;

    /** 流程完成时间 */
    private LocalDateTime finishTime;

    /** 打回原因 */
    private String rejectReason;

    /** 逻辑删除标记：0-未删除 1-已删除 */
    private Integer isDeleted;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 节点中文名，用于错误提示与展示。
     *
     * @param node 节点常量
     * @return 中文名称，未知节点原样返回
     */
    public static String nodeLabel(String node) {
        if (node == null) {
            return "未知";
        }
        return switch (node) {
            case NODE_DRAFT -> "草稿";
            case NODE_SUBMIT -> "提交申请";
            case NODE_APPROVE -> "审批";
            case NODE_SUPER -> "超级节点";
            case NODE_DONE -> "完成";
            case NODE_REJECTED -> "已打回";
            case NODE_CANCELED -> "已取消";
            default -> node;
        };
    }
}
