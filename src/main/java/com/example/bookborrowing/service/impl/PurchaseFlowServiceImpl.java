package com.example.bookborrowing.service.impl;

import com.example.bookborrowing.common.BusinessException;
import com.example.bookborrowing.common.FlowNodeGuard;
import com.example.bookborrowing.entity.BookPurchase;
import com.example.bookborrowing.entity.IntegrationApp;
import com.example.bookborrowing.entity.IntegrationBiz;
import com.example.bookborrowing.entity.IntegrationForm;
import com.example.bookborrowing.entity.MessageItem;
import com.example.bookborrowing.entity.PurchaseFlow;
import com.example.bookborrowing.entity.PurchaseFlowLog;
import com.example.bookborrowing.entity.TodoItem;
import com.example.bookborrowing.mapper.BookPurchaseMapper;
import com.example.bookborrowing.mapper.IntegrationFormMapper;
import com.example.bookborrowing.mapper.PurchaseFlowLogMapper;
import com.example.bookborrowing.mapper.PurchaseFlowMapper;
import com.example.bookborrowing.service.BookPurchaseService;
import com.example.bookborrowing.service.ErpOutboundService;
import com.example.bookborrowing.service.IntegrationAppService;
import com.example.bookborrowing.service.IntegrationBizService;
import com.example.bookborrowing.service.MessageService;
import com.example.bookborrowing.service.PurchaseFlowService;
import com.example.bookborrowing.service.TodoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 采购流程引擎实现（致远 OA「超级节点」模式）。
 * 事务边界：审批下发与 ERP 回执各为一个事务，出站失败 / 入库失败均整体回滚。
 */
@Service
public class PurchaseFlowServiceImpl implements PurchaseFlowService {

    private static final Logger log = LoggerFactory.getLogger(PurchaseFlowServiceImpl.class);

    /** 待办业务类型：采购流程 */
    private static final String TODO_BIZ_TYPE = "PURCHASE_FLOW";

    /** 消息类型：采购 */
    private static final String MSG_TYPE_PURCHASE = "PURCHASE";

    /** ERP 回执操作人展示名（流程轨迹用，无系统登录用户） */
    private static final String ERP_OPERATOR_NAME = "ERP 系统";

    /** 回调超时兜底值：1440 分钟 = 24 小时（集成业务未配置时使用） */
    private static final int DEFAULT_CALLBACK_TIMEOUT_MINUTES = 1440;

    /** 超时打回操作人展示名（流程轨迹用，无系统登录用户） */
    private static final String TIMEOUT_OPERATOR_NAME = "系统";

    /** 自动入库备注 */
    private static final String AUTO_STOCK_IN_REMARK = "ERP 审批通过自动入库";

    private final PurchaseFlowMapper purchaseFlowMapper;

    private final PurchaseFlowLogMapper purchaseFlowLogMapper;

    private final BookPurchaseMapper bookPurchaseMapper;

    private final IntegrationFormMapper integrationFormMapper;

    private final IntegrationBizService integrationBizService;

    private final IntegrationAppService integrationAppService;

    private final ErpOutboundService erpOutboundService;

    private final BookPurchaseService bookPurchaseService;

    private final TodoService todoService;

    private final MessageService messageService;

    public PurchaseFlowServiceImpl(PurchaseFlowMapper purchaseFlowMapper,
                                   PurchaseFlowLogMapper purchaseFlowLogMapper,
                                   BookPurchaseMapper bookPurchaseMapper,
                                   IntegrationFormMapper integrationFormMapper,
                                   IntegrationBizService integrationBizService,
                                   IntegrationAppService integrationAppService,
                                   ErpOutboundService erpOutboundService,
                                   BookPurchaseService bookPurchaseService,
                                   TodoService todoService,
                                   MessageService messageService) {
        this.purchaseFlowMapper = purchaseFlowMapper;
        this.purchaseFlowLogMapper = purchaseFlowLogMapper;
        this.bookPurchaseMapper = bookPurchaseMapper;
        this.integrationFormMapper = integrationFormMapper;
        this.integrationBizService = integrationBizService;
        this.integrationAppService = integrationAppService;
        this.erpOutboundService = erpOutboundService;
        this.bookPurchaseService = bookPurchaseService;
        this.todoService = todoService;
        this.messageService = messageService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitApply(Long purchaseId, Long userId, String userName) {
        BookPurchase purchase = requirePurchase(purchaseId);
        if (purchase.getStatus() == null || purchase.getStatus() != BookPurchase.STATUS_DRAFT) {
            throw new BusinessException("仅草稿状态的采购单可以提交申请");
        }
        LocalDateTime now = LocalDateTime.now();
        IntegrationBiz biz = integrationBizService.getByBizType(IntegrationBiz.BIZ_TYPE_BOOK_PURCHASE);
        if (biz == null || biz.getAppId() == null) {
            throw new BusinessException("未配置图书采购的集成业务/超级节点，请先在后台「集成业务配置」里配置");
        }
        PurchaseFlow flow = purchaseFlowMapper.selectByPurchaseId(purchaseId);
        if (flow == null) {
            // 兜底：建单早于流程建模的历史数据，补建流程实例并直接进入「提交申请」节点
            flow = new PurchaseFlow();
            flow.setPurchaseId(purchaseId);
            flow.setBizType(IntegrationBiz.BIZ_TYPE_BOOK_PURCHASE);
            flow.setAppId(biz.getAppId());
            flow.setCurrentNode(PurchaseFlow.NODE_SUBMIT);
            flow.setNodeStatus(PurchaseFlow.NODE_STATUS_RUNNING);
            flow.setApplicantId(purchase.getApplicantId() == null ? userId : purchase.getApplicantId());
            flow.setApplicantName(purchase.getApplicantName() == null ? userName : purchase.getApplicantName());
            flow.setStartTime(now);
            purchaseFlowMapper.insert(flow);
        } else {
            String node = flow.getCurrentNode();
            FlowNodeGuard.assertFlowNode(flow, "提交申请", PurchaseFlow.NODE_DRAFT, PurchaseFlow.NODE_SUBMIT);
            if (!isRunning(flow)) {
                throw new BusinessException("流程已结束，不能重复提交申请");
            }
            if (PurchaseFlow.NODE_DRAFT.equals(node)) {
                // 建单即建流程（草稿节点），提交申请把节点推进到「提交申请」，并回填要下发的集成应用
                flow.setAppId(biz.getAppId());
                flow.setCurrentNode(PurchaseFlow.NODE_SUBMIT);
                flow.setNodeStatus(PurchaseFlow.NODE_STATUS_RUNNING);
                purchaseFlowMapper.updateById(flow);
            }
        }

        // 不走集成表单：草稿 → 已提交（表单在审批下发时创建，保证出站失败不留废表单）
        int rows = bookPurchaseMapper.markSubmitted(purchaseId, null, null, now);
        if (rows == 0) {
            throw new BusinessException("采购单状态已变更，提交申请失败");
        }
        writeLog(flow.getId(), PurchaseFlow.NODE_DRAFT, PurchaseFlow.NODE_SUBMIT,
                PurchaseFlowLog.ACTION_SUBMIT_APPLY, userId, userName, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveAndSendToErp(Long purchaseId, Long userId, String userName, String comment) {
        PurchaseFlow flow = requireFlow(purchaseId);
        FlowNodeGuard.assertFlowNode(flow, "审批下发", PurchaseFlow.NODE_SUBMIT);
        if (!isRunning(flow)) {
            throw new BusinessException("流程当前状态不允许审批下发");
        }
        BookPurchase purchase = requirePurchase(purchaseId);
        LocalDateTime now = LocalDateTime.now();

        // 1) 审批环节轨迹：SUBMIT → APPROVE
        writeLog(flow.getId(), PurchaseFlow.NODE_SUBMIT, PurchaseFlow.NODE_APPROVE,
                PurchaseFlowLog.ACTION_APPROVE_SEND_ERP, userId, userName, comment);

        // 2) 超级节点取自「集成业务配置」，不再由前端传 appId
        IntegrationBiz biz = integrationBizService.getByBizType(IntegrationBiz.BIZ_TYPE_BOOK_PURCHASE);
        if (biz == null) {
            throw new BusinessException("未配置图书采购的集成业务/超级节点，请先在后台配置");
        }
        IntegrationApp app = biz.getAppId() == null ? null : integrationAppService.getById(biz.getAppId());
        if (app == null) {
            throw new BusinessException("超级节点绑定的集成应用不存在，请先在后台检查集成业务配置");
        }
        if (app.getEnabled() == null || app.getEnabled() != 1) {
            throw new BusinessException("超级节点绑定的集成应用已停用，请先启用该应用");
        }

        // 3) 创建集成表单（与本方法同一事务：出站失败时表单不落库）
        IntegrationForm form = new IntegrationForm();
        form.setAppId(biz.getAppId());
        form.setTitle("图书采购单 " + purchase.getPurchaseNo() + "：" + purchase.getBookName()
                + " ×" + quantityOf(purchase) + " 册");
        form.setFormType(IntegrationBiz.BIZ_TYPE_BOOK_PURCHASE);
        form.setContent(buildFormContent(purchase));
        form.setStatus(0);
        form.setSenderId(purchase.getApplicantId() == null ? userId : purchase.getApplicantId());
        form.setSenderName(purchase.getApplicantName() == null ? userName : purchase.getApplicantName());
        integrationFormMapper.insert(form);

        // 4) 出站调用 ERP：失败抛业务异常 → 整个事务回滚（表单删除、节点停在提交申请节点）
        Long erpFormId = erpOutboundService.pushFormToErp(biz, form, purchase, app);

        // 5) 回写采购单的集成表单关联（状态保持已提交，等待 ERP 回执）
        int rows = bookPurchaseMapper.markFormIssued(purchaseId, biz.getAppId(), form.getId(), now);
        if (rows == 0) {
            throw new BusinessException("采购单状态已变更，审批下发失败");
        }

        // 6) 流程进入超级节点：挂起等待 ERP 回执
        PurchaseFlow update = new PurchaseFlow();
        update.setId(flow.getId());
        update.setCurrentNode(PurchaseFlow.NODE_SUPER);
        update.setNodeStatus(PurchaseFlow.NODE_STATUS_RUNNING);
        update.setErpFormId(erpFormId);
        update.setAppId(biz.getAppId());
        update.setApproveTime(now);
        update.setErpSubmitTime(now);
        purchaseFlowMapper.updateById(update);

        writeLog(flow.getId(), PurchaseFlow.NODE_APPROVE, PurchaseFlow.NODE_SUPER,
                PurchaseFlowLog.ACTION_APPROVE_SEND_ERP, userId, userName,
                "已下发「" + nullToEmpty(biz.getSuperNodeName()) + "」，ERP 表单号：" + erpFormId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long purchaseId, Long userId, String userName, String comment) {
        PurchaseFlow flow = requireFlow(purchaseId);
        FlowNodeGuard.assertFlowNode(flow, "驳回", PurchaseFlow.NODE_SUBMIT);
        PurchaseFlow update = new PurchaseFlow();
        update.setId(flow.getId());
        update.setCurrentNode(PurchaseFlow.NODE_REJECTED);
        update.setNodeStatus(PurchaseFlow.NODE_STATUS_REJECTED);
        update.setRejectReason(comment == null || comment.isBlank() ? "审批不通过" : comment);
        purchaseFlowMapper.updateById(update);
        // 采购单保持已提交（流程中），打回后可重新提交
        writeLog(flow.getId(), PurchaseFlow.NODE_SUBMIT, PurchaseFlow.NODE_REJECTED,
                PurchaseFlowLog.ACTION_ERP_REJECT, userId, userName, comment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resubmit(Long purchaseId, Long userId, String userName) {
        PurchaseFlow flow = requireFlow(purchaseId);
        FlowNodeGuard.assertFlowNode(flow, "重新提交", PurchaseFlow.NODE_REJECTED);
        PurchaseFlow update = new PurchaseFlow();
        update.setId(flow.getId());
        update.setCurrentNode(PurchaseFlow.NODE_SUBMIT);
        update.setNodeStatus(PurchaseFlow.NODE_STATUS_RUNNING);
        // updateById 只更新非空字段（该 Mapper 不在本次可改范围），用空串表达「清空打回原因」
        update.setRejectReason("");
        purchaseFlowMapper.updateById(update);
        writeLog(flow.getId(), PurchaseFlow.NODE_REJECTED, PurchaseFlow.NODE_SUBMIT,
                PurchaseFlowLog.ACTION_RESUBMIT, userId, userName, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleErpDecision(Long purchaseId, boolean agreed, String comment) {
        PurchaseFlow flow = requireFlow(purchaseId);
        if (isFlowFinished(flow.getCurrentNode())) {
            // 迟到回执：OA 侧流程已终止，ERP 的结论不再生效，只记告警，不推进状态、不发通知
            log.warn("ERP 回执迟到已忽略：purchaseId={}，当前节点={}，agreed={}，comment={}",
                    purchaseId, flow.getCurrentNode(), agreed, comment);
            return;
        }
        FlowNodeGuard.assertFlowNode(flow, "ERP 回执", PurchaseFlow.NODE_SUPER);
        BookPurchase purchase = requirePurchase(purchaseId);
        LocalDateTime now = LocalDateTime.now();

        PurchaseFlow update = new PurchaseFlow();
        update.setId(flow.getId());
        update.setErpCallbackTime(now);
        if (agreed) {
            update.setCurrentNode(PurchaseFlow.NODE_DONE);
            update.setNodeStatus(PurchaseFlow.NODE_STATUS_AGREED);
            update.setFinishTime(now);
            purchaseFlowMapper.updateById(update);
            // 自动入库：同 ISBN 累加库存 / 无匹配则新建图书；幂等由采购侧条件更新保证
            bookPurchaseService.stockInInternal(purchaseId, AUTO_STOCK_IN_REMARK);
            writeLog(flow.getId(), PurchaseFlow.NODE_SUPER, PurchaseFlow.NODE_DONE,
                    PurchaseFlowLog.ACTION_ERP_AGREE, null, ERP_OPERATOR_NAME, comment);
            notifyApplicant(flow, purchase, true, comment);
        } else {
            update.setCurrentNode(PurchaseFlow.NODE_REJECTED);
            update.setNodeStatus(PurchaseFlow.NODE_STATUS_REJECTED);
            update.setRejectReason(comment == null || comment.isBlank() ? "ERP 审批不同意" : comment);
            purchaseFlowMapper.updateById(update);
            // 采购单保持流程中（已回执状态回退为已提交，便于重新提交后再次下发）
            bookPurchaseMapper.markReopened(purchaseId);
            writeLog(flow.getId(), PurchaseFlow.NODE_SUPER, PurchaseFlow.NODE_REJECTED,
                    PurchaseFlowLog.ACTION_ERP_REJECT, null, ERP_OPERATOR_NAME, comment);
            notifyApplicant(flow, purchase, false, comment);
        }
    }

    @Override
    public List<Long> listSuperNodeWaitPurchaseIds() {
        return bookPurchaseMapper.selectSuperNodeWaitPurchaseIds();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean timeoutIfExpired(Long purchaseId) {
        if (purchaseId == null) {
            return false;
        }
        PurchaseFlow flow = getByPurchaseId(purchaseId);
        // 幂等：只有仍挂在超级节点、等待回执的流程才可能超时；已打回/已完成/已取消一律跳过
        if (flow == null
                || !PurchaseFlow.NODE_SUPER.equals(flow.getCurrentNode())
                || !isRunning(flow)
                || flow.getErpSubmitTime() == null) {
            return false;
        }
        int timeoutMinutes = timeoutMinutesOf(flow.getBizType());
        LocalDateTime deadline = flow.getErpSubmitTime().plusMinutes(timeoutMinutes);
        LocalDateTime now = LocalDateTime.now();
        if (!now.isAfter(deadline)) {
            return false;
        }
        BookPurchase purchase = requirePurchase(purchaseId);
        String reason = "ERP 超时未回执（" + timeoutMinutes + " 分钟），流程自动打回";

        PurchaseFlow update = new PurchaseFlow();
        update.setId(flow.getId());
        update.setCurrentNode(PurchaseFlow.NODE_REJECTED);
        update.setNodeStatus(PurchaseFlow.NODE_STATUS_REJECTED);
        update.setRejectReason(reason);
        update.setErpCallbackTime(now);
        purchaseFlowMapper.updateById(update);
        // 采购单保持流程中(1)可重新提交：若残留已回执(3)则回退为已提交(1)，否则该条件更新不影响任何行
        bookPurchaseMapper.markReopened(purchaseId);

        // OA 侧流程已终止：主动通知 ERP 撤销其表单，避免等到 ERP 回执时才发现流程已结束
        cancelErpFormQuietly(flow, "采购流程超时未回执，OA 侧已自动打回");

        writeLog(flow.getId(), PurchaseFlow.NODE_SUPER, PurchaseFlow.NODE_REJECTED,
                PurchaseFlowLog.ACTION_ERP_TIMEOUT, null, TIMEOUT_OPERATOR_NAME, reason);
        notifyApplicantTimeout(flow, purchase, timeoutMinutes);
        log.info("采购流程超时自动打回：purchaseId={}，超时分钟={}，erpSubmitTime={}",
                purchaseId, timeoutMinutes, flow.getErpSubmitTime());
        return true;
    }

    @Override
    public PurchaseFlow getByPurchaseId(Long purchaseId) {
        if (purchaseId == null) {
            return null;
        }
        return purchaseFlowMapper.selectByPurchaseId(purchaseId);
    }

    @Override
    public Map<String, Object> detail(Long purchaseId) {
        PurchaseFlow flow = getByPurchaseId(purchaseId);
        List<PurchaseFlowLog> logs = flow == null
                ? List.of()
                : purchaseFlowLogMapper.selectByFlowId(flow.getId());
        String bizType = flow == null || flow.getBizType() == null
                ? IntegrationBiz.BIZ_TYPE_BOOK_PURCHASE : flow.getBizType();
        Map<String, Object> result = new LinkedHashMap<>(4);
        result.put("flow", flow);
        result.put("logs", logs);
        result.put("biz", integrationBizService.getByBizType(bizType));
        return result;
    }

    /**
     * 校验采购单存在。
     *
     * @param purchaseId 采购单ID
     * @return 采购单
     */
    private BookPurchase requirePurchase(Long purchaseId) {
        if (purchaseId == null) {
            throw new BusinessException("采购单ID不能为空");
        }
        BookPurchase purchase = bookPurchaseMapper.selectById(purchaseId);
        if (purchase == null) {
            throw new BusinessException("采购单不存在");
        }
        return purchase;
    }

    /**
     * 校验流程实例存在。
     *
     * @param purchaseId 采购单ID
     * @return 流程实例
     */
    private PurchaseFlow requireFlow(Long purchaseId) {
        PurchaseFlow flow = getByPurchaseId(purchaseId);
        if (flow == null) {
            throw new BusinessException("该采购单尚未发起流程，请先提交申请");
        }
        return flow;
    }

    /**
     * 判断节点是否仍进行中。
     *
     * @param flow 流程实例
     * @return 进行中返回 true
     */
    private boolean isRunning(PurchaseFlow flow) {
        return flow.getNodeStatus() != null && flow.getNodeStatus() == PurchaseFlow.NODE_STATUS_RUNNING;
    }

    /**
     * 判断流程是否已终止（完成 / 已打回 / 已取消）。
     *
     * @param node 当前节点
     * @return 已终止返回 true
     */
    private boolean isFlowFinished(String node) {
        return PurchaseFlow.NODE_DONE.equals(node)
                || PurchaseFlow.NODE_REJECTED.equals(node)
                || PurchaseFlow.NODE_CANCELED.equals(node);
    }

    /**
     * 写一条流转轨迹。
     *
     * @param flowId       流程实例ID
     * @param fromNode     来源节点
     * @param toNode       目标节点
     * @param action       动作常量
     * @param operatorId   操作人ID（外部系统回执为 null）
     * @param operatorName 操作人姓名
     * @param comment      备注
     */
    private void writeLog(Long flowId, String fromNode, String toNode, String action,
                          Long operatorId, String operatorName, String comment) {
        PurchaseFlowLog flowLog = new PurchaseFlowLog();
        flowLog.setFlowId(flowId);
        flowLog.setFromNode(fromNode);
        flowLog.setToNode(toNode);
        flowLog.setAction(action);
        flowLog.setOperatorId(operatorId);
        flowLog.setOperatorName(operatorName == null ? ERP_OPERATOR_NAME : operatorName);
        flowLog.setComment(comment);
        purchaseFlowLogMapper.insert(flowLog);
    }

    /**
     * 给申请人写待办与消息（ERP 回执结果通知）。
     *
     * @param flow     流程实例
     * @param purchase 采购单
     * @param agreed   是否同意
     * @param comment  ERP 处理意见
     */
    private void notifyApplicant(PurchaseFlow flow, BookPurchase purchase, boolean agreed, String comment) {
        String suffix = comment == null || comment.isBlank() ? "" : "处理意见：" + comment;
        String title = agreed ? "图书采购流程已完成" : "图书采购流程被打回";
        String content = agreed
                ? "采购单 " + purchase.getPurchaseNo() + "（" + purchase.getBookName() + "）ERP 审批通过，已自动入库。"
                : "采购单 " + purchase.getPurchaseNo() + "（" + purchase.getBookName() + "）ERP 审批不同意，已打回，可修改后重新提交。";
        sendNotify(flow, purchase, title, suffix.isEmpty() ? content : content + suffix);
    }

    /**
     * 给申请人写待办与消息（回调超时自动打回通知）。
     *
     * @param flow           流程实例
     * @param purchase       采购单
     * @param timeoutMinutes 超时分钟数
     */
    private void notifyApplicantTimeout(PurchaseFlow flow, BookPurchase purchase, int timeoutMinutes) {
        sendNotify(flow, purchase, "图书采购流程超时打回",
                "采购单 " + purchase.getPurchaseNo() + "（" + purchase.getBookName() + "）ERP 在 "
                        + timeoutMinutes + " 分钟内未回执，系统已自动打回，可修改后重新提交。");
    }

    /**
     * 写一条待办 + 一条消息给申请人（同一事件只发一次，避免重复通知）。
     *
     * @param flow     流程实例
     * @param purchase 采购单
     * @param title    标题
     * @param content  正文
     */
    private void sendNotify(PurchaseFlow flow, BookPurchase purchase, String title, String content) {
        Long receiverId = flow.getApplicantId() == null ? purchase.getApplicantId() : flow.getApplicantId();
        if (receiverId == null) {
            return;
        }
        TodoItem todo = new TodoItem();
        todo.setTitle(title);
        todo.setContent(content);
        todo.setBizType(TODO_BIZ_TYPE);
        todo.setBizId(purchase.getId());
        todo.setAppId(flow.getAppId());
        todo.setReceiverId(receiverId);
        todoService.create(todo);

        MessageItem message = new MessageItem();
        message.setTitle(title);
        message.setContent(content);
        message.setMsgType(MSG_TYPE_PURCHASE);
        message.setAppId(flow.getAppId());
        message.setReceiverId(receiverId);
        messageService.create(message);
    }

    /**
     * 取业务类型对应的回调超时分钟数：未配置或配置非法时用默认 1440 分钟。
     *
     * @param bizType 业务类型
     * @return 超时分钟数
     */
    private int timeoutMinutesOf(String bizType) {
        IntegrationBiz biz = integrationBizService.getByBizType(bizType);
        if (biz == null || biz.getCallbackTimeoutMinutes() == null || biz.getCallbackTimeoutMinutes() <= 0) {
            return DEFAULT_CALLBACK_TIMEOUT_MINUTES;
        }
        return biz.getCallbackTimeoutMinutes();
    }

    /**
     * 通知 ERP 撤销其侧表单（OA 侧流程已终止）。取不到集成应用/集成业务时只告警跳过；
     * 出站方法自身不抛异常，不影响本地流程状态与事务。
     *
     * @param flow   流程实例
     * @param reason 撤销说明
     */
    private void cancelErpFormQuietly(PurchaseFlow flow, String reason) {
        if (flow == null || flow.getErpFormId() == null) {
            return;
        }
        IntegrationApp app = flow.getAppId() == null ? null : integrationAppService.getById(flow.getAppId());
        String bizType = flow.getBizType() == null ? IntegrationBiz.BIZ_TYPE_BOOK_PURCHASE : flow.getBizType();
        IntegrationBiz biz = integrationBizService.getByBizType(bizType);
        if (app == null || biz == null) {
            log.warn("通知 ERP 撤销表单跳过：purchaseId={}，erpFormId={}，appId={}，bizType={}（集成应用或集成业务配置缺失）",
                    flow.getPurchaseId(), flow.getErpFormId(), flow.getAppId(), bizType);
            return;
        }
        erpOutboundService.cancelErpForm(app, biz, flow.getErpFormId(), reason);
    }

    /**
     * 采购数量兜底（不少于1）。
     *
     * @param purchase 采购单
     * @return 数量
     */
    private int quantityOf(BookPurchase purchase) {
        return purchase.getQuantity() == null || purchase.getQuantity() < 1 ? 1 : purchase.getQuantity();
    }

    /**
     * 构造集成表单内容（JSON），格式与人工提交链路保持一致。
     *
     * @param purchase 采购单
     * @return JSON 字符串
     */
    private String buildFormContent(BookPurchase purchase) {
        StringBuilder sb = new StringBuilder(256);
        sb.append('{');
        sb.append("\"purchaseId\":").append(purchase.getId()).append(',');
        sb.append("\"purchaseNo\":\"").append(escape(purchase.getPurchaseNo())).append("\",");
        sb.append("\"bookName\":\"").append(escape(purchase.getBookName())).append("\",");
        sb.append("\"author\":\"").append(escape(purchase.getAuthor())).append("\",");
        sb.append("\"isbn\":\"").append(escape(purchase.getIsbn())).append("\",");
        sb.append("\"publisher\":\"").append(escape(purchase.getPublisher())).append("\",");
        sb.append("\"quantity\":").append(quantityOf(purchase)).append(',');
        sb.append("\"unitPrice\":").append(purchase.getUnitPrice()).append(',');
        sb.append("\"totalPrice\":").append(purchase.getTotalPrice()).append(',');
        sb.append("\"source\":\"图书借阅管理系统\"");
        sb.append('}');
        return sb.toString();
    }

    /**
     * 简单 JSON 字符串转义。
     *
     * @param value 原值
     * @return 转义后的值（null 视为空串）
     */
    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * null 转空串。
     *
     * @param value 原值
     * @return 非空字符串
     */
    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
