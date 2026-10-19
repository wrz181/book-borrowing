package com.example.bookborrowing.service.impl;

import com.example.bookborrowing.common.BusinessException;
import com.example.bookborrowing.common.PageResult;
import com.example.bookborrowing.dto.ErpFormSubmitDTO;
import com.example.bookborrowing.dto.ErpSsoUserDTO;
import com.example.bookborrowing.dto.SsoTicketDTO;
import com.example.bookborrowing.entity.IntegrationApp;
import com.example.bookborrowing.entity.IntegrationForm;
import com.example.bookborrowing.entity.MessageItem;
import com.example.bookborrowing.entity.SysUser;
import com.example.bookborrowing.entity.TodoItem;
import com.example.bookborrowing.mapper.IntegrationFormMapper;
import com.example.bookborrowing.service.ErpIntegrationService;
import com.example.bookborrowing.service.IntegrationAppService;
import com.example.bookborrowing.service.MessageService;
import com.example.bookborrowing.service.SysUserService;
import com.example.bookborrowing.service.TodoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 类ERP平台集成业务实现。
 */
@Service
public class ErpIntegrationServiceImpl implements ErpIntegrationService {

    /** 待处理表单一次最多返回条数 */
    private static final int MAX_FORM_PAGE_SIZE = 200;

    private final IntegrationAppService integrationAppService;

    private final IntegrationFormMapper integrationFormMapper;

    private final TodoService todoService;

    private final MessageService messageService;

    private final SysUserService sysUserService;

    public ErpIntegrationServiceImpl(IntegrationAppService integrationAppService,
                                     IntegrationFormMapper integrationFormMapper,
                                     TodoService todoService,
                                     MessageService messageService,
                                     SysUserService sysUserService) {
        this.integrationAppService = integrationAppService;
        this.integrationFormMapper = integrationFormMapper;
        this.todoService = todoService;
        this.messageService = messageService;
        this.sysUserService = sysUserService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public IntegrationForm submitForm(ErpFormSubmitDTO dto) {
        SsoTicketDTO ticket = requireTicket(dto.getTicket());
        IntegrationApp app = integrationAppService.getById(ticket.getAppId());
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
        return form;
    }

    @Override
    public ErpSsoUserDTO validateSso(String ticket) {
        SsoTicketDTO parsed = requireTicket(ticket);
        IntegrationApp app = integrationAppService.getById(parsed.getAppId());
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
    public IntegrationForm handleForm(Long formId, String ticket, String comment) {
        SsoTicketDTO parsed = requireTicket(ticket);
        IntegrationForm form = integrationFormMapper.selectById(formId);
        if (form == null) {
            throw new BusinessException("表单不存在");
        }
        if (!parsed.getAppId().equals(form.getAppId())) {
            throw new BusinessException("无权处理其他应用的业务表单");
        }
        int rows = integrationFormMapper.updateHandle(formId, LocalDateTime.now(), comment);
        if (rows == 0) {
            throw new BusinessException("表单已被处理，请刷新后重试");
        }
        IntegrationForm handled = integrationFormMapper.selectById(formId);
        IntegrationApp app = integrationAppService.getById(parsed.getAppId());
        notifySender(parsed.getAppId(), app, handled, handled.getSenderId(), comment);
        return handled;
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
     * 校验单点登录票据，无效时抛出业务异常。
     *
     * @param ticket 票据
     * @return 解析结果
     */
    private SsoTicketDTO requireTicket(String ticket) {
        SsoTicketDTO parsed = integrationAppService.validateTicket(ticket);
        if (parsed == null) {
            throw new BusinessException("单点登录票据无效或已过期，请重新从图书借阅系统进入");
        }
        return parsed;
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
