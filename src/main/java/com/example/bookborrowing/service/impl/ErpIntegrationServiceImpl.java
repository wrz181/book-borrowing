package com.example.bookborrowing.service.impl;

import com.example.bookborrowing.common.BusinessException;
import com.example.bookborrowing.common.PageResult;
import com.example.bookborrowing.dto.ErpFormHandleDTO;
import com.example.bookborrowing.dto.ErpFormSubmitDTO;
import com.example.bookborrowing.dto.ErpSsoUserDTO;
import com.example.bookborrowing.dto.SsoTicketDTO;
import com.example.bookborrowing.entity.BookPurchase;
import com.example.bookborrowing.entity.IntegrationApp;
import com.example.bookborrowing.entity.IntegrationForm;
import com.example.bookborrowing.entity.MessageItem;
import com.example.bookborrowing.entity.PurchaseFlow;
import com.example.bookborrowing.entity.SysUser;
import com.example.bookborrowing.entity.TodoItem;
import com.example.bookborrowing.mapper.BookPurchaseMapper;
import com.example.bookborrowing.mapper.IntegrationFormMapper;
import com.example.bookborrowing.service.ErpIntegrationService;
import com.example.bookborrowing.service.IntegrationAppService;
import com.example.bookborrowing.service.MessageService;
import com.example.bookborrowing.service.PurchaseFlowService;
import com.example.bookborrowing.service.SysUserService;
import com.example.bookborrowing.service.TodoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 类ERP平台集成业务实现。
 */
@Service
public class ErpIntegrationServiceImpl implements ErpIntegrationService {

    /** 集成模块日志，命名与模块级日志配置一致 */
    private static final Logger log = LoggerFactory.getLogger(ErpIntegrationServiceImpl.class);

    /** 待处理表单一次最多返回条数 */
    private static final int MAX_FORM_PAGE_SIZE = 200;

    private final IntegrationAppService integrationAppService;

    private final IntegrationFormMapper integrationFormMapper;

    private final BookPurchaseMapper bookPurchaseMapper;

    private final TodoService todoService;

    private final MessageService messageService;

    private final SysUserService sysUserService;

    private final PurchaseFlowService purchaseFlowService;

    public ErpIntegrationServiceImpl(IntegrationAppService integrationAppService,
                                     IntegrationFormMapper integrationFormMapper,
                                     BookPurchaseMapper bookPurchaseMapper,
                                     TodoService todoService,
                                     MessageService messageService,
                                     SysUserService sysUserService,
                                     PurchaseFlowService purchaseFlowService) {
        this.integrationAppService = integrationAppService;
        this.integrationFormMapper = integrationFormMapper;
        this.bookPurchaseMapper = bookPurchaseMapper;
        this.todoService = todoService;
        this.messageService = messageService;
        this.sysUserService = sysUserService;
        this.purchaseFlowService = purchaseFlowService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public IntegrationForm submitForm(ErpFormSubmitDTO dto) {
        SsoTicketDTO ticket = requireTicket(dto.getTicket());
        IntegrationApp app = integrationAppService.getById(ticket.getAppId());
        warnIfAppUnavailable(ticket.getAppId(), app);
        SysUser user = sysUserService.getById(ticket.getUserId());
        String senderName = user == null ? null : user.getNickname();

        IntegrationForm form = new IntegrationForm();
        form.setAppId(ticket.getAppId());
        form.setTitle(dto.getTitle() == null || dto.getTitle().isBlank()
                ? (app == null ? "集成表单" : app.getAppName() + " 表单") : dto.getTitle());
        form.setFormType(dto.getFormType() == null ? "BOOK_PURCHASE" : dto.getFormType());
        form.setContent(dto.getContent());
        form.setStatus(1);
        form.setSenderId(ticket.getUserId());
        form.setSenderName(senderName);
        form.setHandleTime(LocalDateTime.now());
        form.setHandleComment(dto.getHandleComment());
        integrationFormMapper.insert(form);

        notifySender(ticket.getAppId(), app, form, ticket.getUserId(), dto.getHandleComment());
        log.info("外部平台提交表单成功，formId={}, appId={}, formType={}, senderId={}",
                form.getId(), ticket.getAppId(), form.getFormType(), ticket.getUserId());
        // 写库全部成功后才消费票据；此前的任何失败（校验、写库、通知）都不会执行到这里，票据仍可重试
        integrationAppService.consumeTicket(dto.getTicket());
        log.info("外部提交票据已消费（一次性），formId={}, appId={}", form.getId(), ticket.getAppId());
        return form;
    }

    @Override
    public ErpSsoUserDTO validateSso(String ticket) {
        SsoTicketDTO parsed = requireTicket(ticket);
        IntegrationApp app = integrationAppService.getById(parsed.getAppId());
        warnIfAppUnavailable(parsed.getAppId(), app);
        SysUser user = sysUserService.getById(parsed.getUserId());
        return new ErpSsoUserDTO(
                parsed.getAppId(),
                app == null ? null : app.getAppName(),
                parsed.getUserId(),
                user == null ? null : user.getUsername(),
                user == null ? null : user.getNickname());
    }

    @Override
    public List<IntegrationForm> listForms(String ticket, Integer status) {
        SsoTicketDTO parsed = requireTicket(ticket);
        int queryStatus = status == null ? 0 : status;
        return integrationFormMapper.selectPage(0, MAX_FORM_PAGE_SIZE, parsed.getAppId(), queryStatus);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public IntegrationForm handleForm(Long formId, String ticket, String comment, Boolean agreed) {
        SsoTicketDTO parsed = requireTicket(ticket);
        IntegrationForm form = integrationFormMapper.selectById(formId);
        if (form == null) {
            throw new BusinessException("表单不存在");
        }
        if (!parsed.getAppId().equals(form.getAppId())) {
            log.error("越权回执被拒绝，formId={}, 票据应用={}, 表单所属应用={}", formId, parsed.getAppId(), form.getAppId());
            throw new BusinessException("无权处理其他应用的业务表单");
        }
        int rows = integrationFormMapper.updateHandle(formId, LocalDateTime.now(), comment);
        if (rows == 0) {
            log.warn("回执被拒绝：表单已被处理，formId={}, appId={}", formId, parsed.getAppId());
            throw new BusinessException("表单已被处理，请刷新后重试");
        }
        // 回写关联采购单为已回执；无关联采购单（外部系统自发表单）时静默跳过
        int purchaseRows = bookPurchaseMapper.markAcknowledged(formId);
        if (purchaseRows > 0) {
            log.info("外部平台回执成功，formId={}, appId={}, 回写采购单 {} 条为已回执", formId, parsed.getAppId(), purchaseRows);
        } else {
            log.info("外部平台回执成功，formId={}, appId={}, 无关联采购单，跳过采购单回写", formId, parsed.getAppId());
        }
        IntegrationForm handled = integrationFormMapper.selectById(formId);
        IntegrationApp app = integrationAppService.getById(parsed.getAppId());
        if (agreed == null) {
            // 旧链路：外部平台仅"办理完成"，回执通知由这里发出
            notifySender(parsed.getAppId(), app, handled, handled.getSenderId(), comment);
        } else {
            // 流程链路：同意/不同意的通知由流程引擎按 PURCHASE_FLOW 语义发出，避免同一事件产生两条待办与两条消息
            log.info("ERP 审批结论回执，formId={}, agreed={}，通知交由流程引擎发出", formId, agreed);
        }
        // ERP 明确同意/不同意时推进采购流程引擎（自动入库或打回）；agreed 为 null 时完全保持旧链路
        applyFlowDecision(formId, agreed, comment);
        // 顺序要求：先校验、再写库、最后才消费，写库失败时票据不会被白白烧掉
        integrationAppService.consumeTicket(ticket);
        log.info("回执票据已消费（一次性），formId={}, appId={}", formId, parsed.getAppId());
        return handled;
    }

    /**
     * 按 ERP 审批结论推进关联采购流程：表单能关联到流程实例才处理，关联不到（外部自发表单、旧链路）静默忽略。
     *
     * @param formId  集成表单ID
     * @param agreed  审批结论（null 表示不涉及流程引擎）
     * @param comment 处理意见
     */
    private void applyFlowDecision(Long formId, Boolean agreed, String comment) {
        if (agreed == null) {
            return;
        }
        BookPurchase purchase = bookPurchaseMapper.selectByIntegrationFormId(formId);
        if (purchase == null) {
            log.info("表单 {} 未关联采购单，跳过流程引擎处理", formId);
            return;
        }
        PurchaseFlow flow = purchaseFlowService.getByPurchaseId(purchase.getId());
        if (flow == null) {
            log.info("表单 {} 关联的采购单 {} 无流程实例，跳过流程引擎处理", formId, purchase.getId());
            return;
        }
        purchaseFlowService.handleErpDecision(purchase.getId(), agreed, comment);
        log.info("ERP 回执已推进流程，formId={}, purchaseId={}, agreed={}", formId, purchase.getId(), agreed);
    }

    @Override
    public PageResult<IntegrationForm> page(Long appId, Integer status, int pageNum, int pageSize) {
        int page = Math.max(pageNum, 1);
        int size = pageSize <= 0 ? 10 : pageSize;
        int offset = (page - 1) * size;
        List<IntegrationForm> list = integrationFormMapper.selectPage(offset, size, appId, status);
        long total = integrationFormMapper.selectCount(appId, status);
        return new PageResult<>(list, total, page, size);
    }

    /**
     * 校验单点登录票据，无效或已被消费时抛出业务异常（两种提示语区分开）。
     *
     * @param ticket 票据
     * @return 解析结果
     */
    private SsoTicketDTO requireTicket(String ticket) {
        SsoTicketDTO parsed = integrationAppService.validateTicket(ticket);
        if (parsed == null) {
            if (integrationAppService.isTicketConsumed(ticket)) {
                log.warn("单点登录票据已使用，拒绝重复使用");
                throw new BusinessException("单点登录票据已使用，请重新从图书借阅系统进入");
            }
            log.warn("单点登录票据校验失败：票据为空、过期、签名不符或来源应用不可用");
            throw new BusinessException("单点登录票据无效或已过期，请重新从图书借阅系统进入");
        }
        return parsed;
    }

    /**
     * 记录集成应用不可用（已删除或已停用）的告警，不改变原有业务流程。
     *
     * @param appId 应用ID
     * @param app   应用实体（可为 null）
     */
    private void warnIfAppUnavailable(Long appId, IntegrationApp app) {
        if (app == null) {
            log.warn("集成应用不存在或已删除，appId={}", appId);
        } else if (app.getEnabled() == null || app.getEnabled() != 1) {
            log.warn("集成应用已停用，appId={}, appName={}", appId, app.getAppName());
        }
    }

    /**
     * 生成待办与消息提醒给发起人（外部平台处理完成后回调）。
     *
     * @param appId     应用ID
     * @param app       应用实体
     * @param form      表单
     * @param receiverId 接收人ID
     * @param comment   处理意见
     */
    private void notifySender(Long appId, IntegrationApp app, IntegrationForm form,
                              Long receiverId, String comment) {
        if (receiverId == null) {
            return;
        }
        String appName = app == null ? "外系统" : app.getAppName();

        TodoItem todo = new TodoItem();
        todo.setTitle("【" + appName + "】已处理：" + form.getTitle());
        todo.setContent(comment);
        todo.setBizType("INTEGRATION_FORM");
        todo.setBizId(form.getId());
        todo.setAppId(appId);
        todo.setReceiverId(receiverId);
        todoService.create(todo);

        MessageItem message = new MessageItem();
        message.setTitle("表单处理完成通知");
        message.setContent(form.getTitle() + " 已由外部平台处理完成。");
        message.setMsgType("INTEGRATION");
        message.setAppId(appId);
        message.setReceiverId(receiverId);
        messageService.create(message);
    }
}
