package com.example.bookborrowing.controller;

import com.example.bookborrowing.common.BusinessException;
import com.example.bookborrowing.common.Result;
import com.example.bookborrowing.dto.ErpFormHandleDTO;
import com.example.bookborrowing.dto.ErpFormSubmitDTO;
import com.example.bookborrowing.dto.ErpSsoUserDTO;
import com.example.bookborrowing.entity.IntegrationApp;
import com.example.bookborrowing.entity.IntegrationForm;
import com.example.bookborrowing.entity.MessageItem;
import com.example.bookborrowing.entity.TodoItem;
import com.example.bookborrowing.mapper.SysUserMapper;
import com.example.bookborrowing.service.ErpIntegrationService;
import com.example.bookborrowing.service.IntegrationAppService;
import com.example.bookborrowing.service.MessageService;
import com.example.bookborrowing.service.TodoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 外系统（类ERP平台）回调接口：单点登录校验 / 待办表单办理 / 表单回传 / 待办与消息推送。
 * 不走 JWT，改用单点登录票据或应用级 AppId + Secret 校验，路径 /api/open/** 已在配置中排除 JWT 拦截。
 */
@RestController
@RequestMapping("/api/open")
public class OpenIntegrationController {

    private final IntegrationAppService integrationAppService;

    private final TodoService todoService;

    private final MessageService messageService;

    private final ErpIntegrationService erpIntegrationService;

    private final SysUserMapper sysUserMapper;

    public OpenIntegrationController(IntegrationAppService integrationAppService,
                                     TodoService todoService,
                                     MessageService messageService,
                                     ErpIntegrationService erpIntegrationService,
                                     SysUserMapper sysUserMapper) {
        this.integrationAppService = integrationAppService;
        this.todoService = todoService;
        this.messageService = messageService;
        this.erpIntegrationService = erpIntegrationService;
        this.sysUserMapper = sysUserMapper;
    }

    /**
     * 单点登录校验：外部平台用票据换取登录用户信息（实现「登录打开」效果）。
     *
     * @param ticket 单点登录票据
     * @return 登录用户信息
     */
    @PostMapping("/sso/validate")
    public Result<ErpSsoUserDTO> validateSso(@RequestParam String ticket) {
        return Result.success(erpIntegrationService.validateSso(ticket));
    }

    /**
     * 查询本应用下发的集成表单（默认仅待处理），供外部平台待办列表展示。
     *
     * @param ticket 单点登录票据
     * @param status 状态（可空，默认 0-待处理）
     * @return 表单列表
     */
    @GetMapping("/forms")
    public Result<List<IntegrationForm>> forms(@RequestParam String ticket,
                                               @RequestParam(required = false) Integer status) {
        return Result.success(erpIntegrationService.listForms(ticket, status));
    }

    /**
     * 外部平台办理待处理表单：处理完成后自动回传待办与消息给发起人。
     *
     * @param id  表单ID
     * @param dto 办理参数（含 ticket、处理意见）
     * @return 处理后的表单
     */
    @PostMapping("/forms/{id}/handle")
    public Result<IntegrationForm> handleForm(@PathVariable Long id, @RequestBody ErpFormHandleDTO dto) {
        return Result.success(erpIntegrationService.handleForm(id, dto.getTicket(), dto.getHandleComment(), dto.getAgreed()));
    }

    /**
     * 类ERP平台回传已处理的表单：单点登录票据校验通过后，生成待办与消息提醒。
     *
     * @param dto 回传参数（含 ticket）
     * @return 保存后的集成表单
     */
    @PostMapping("/form/submit")
    public Result<IntegrationForm> submitForm(@RequestBody ErpFormSubmitDTO dto) {
        return Result.success(erpIntegrationService.submitForm(dto));
    }

    /**
     * 外系统发起待办（ERP 侧完成表单后推送）。
     *
     * @param appId   应用ID（请求头 X-App-Id）
     * @param secret  应用密钥（请求头 X-App-Secret）
     * @param todo    待办实体
     * @return 保存后的待办
     */
    @PostMapping("/todo")
    public Result<TodoItem> pushTodo(@RequestHeader("X-App-Id") Long appId,
                                     @RequestHeader("X-App-Secret") String secret,
                                     @RequestBody TodoItem todo) {
        IntegrationApp app = verify(appId, secret);
        if (todo == null) {
            throw new BusinessException("待办内容不能为空");
        }
        // 收件人必须真实存在且未删除，否则待办投递后永久无人可见
        checkReceiver(todo.getReceiverId(), "待办接收人");
        todo.setAppId(app.getId());
        if (todo.getBizType() == null) {
            todo.setBizType("INTEGRATION_FORM");
        } else {
            checkTypeLength(todo.getBizType(), "bizType");
        }
        return Result.success(todoService.create(todo));
    }

    /**
     * 外系统发送消息提醒。
     *
     * @param appId   应用ID（请求头 X-App-Id）
     * @param secret  应用密钥（请求头 X-App-Secret）
     * @param message 消息实体
     * @return 保存后的消息
     */
    @PostMapping("/message")
    public Result<MessageItem> pushMessage(@RequestHeader("X-App-Id") Long appId,
                                           @RequestHeader("X-App-Secret") String secret,
                                           @RequestBody MessageItem message) {
        IntegrationApp app = verify(appId, secret);
        if (message == null) {
            throw new BusinessException("消息内容不能为空");
        }
        // 收件人必须真实存在且未删除，否则消息投递后永久无人可见
        checkReceiver(message.getReceiverId(), "消息接收人");
        message.setAppId(app.getId());
        if (message.getMsgType() == null) {
            message.setMsgType("INTEGRATION");
        } else {
            checkTypeLength(message.getMsgType(), "msgType");
        }
        return Result.success(messageService.create(message));
    }

    /**
     * 校验接收人存在且未被逻辑删除（receiver_id 无外键约束，需在此拦截非法投递）。
     *
     * @param receiverId 接收人ID
     * @param fieldName  字段中文名（用于错误提示）
     */
    private void checkReceiver(Long receiverId, String fieldName) {
        if (receiverId == null) {
            throw new BusinessException(fieldName + "不能为空（receiverId）");
        }
        if (sysUserMapper.selectById(receiverId) == null) {
            throw new BusinessException(fieldName + "不存在或已被删除：receiverId=" + receiverId);
        }
    }

    /**
     * 校验类型字段长度，与表字段 VARCHAR(50) 对齐，超长直接报业务错误而非数据库异常。
     *
     * @param value     字段值
     * @param fieldName 字段英文名
     */
    private void checkTypeLength(String value, String fieldName) {
        if (value.length() > 50) {
            throw new BusinessException(fieldName + " 长度不能超过 50 个字符");
        }
    }

    /**
     * 校验应用身份。
     *
     * @param appId  应用ID
     * @param secret 应用密钥
     * @return 应用实体
     */
    private IntegrationApp verify(Long appId, String secret) {
        IntegrationApp app = integrationAppService.getById(appId);
        if (app == null) {
            throw new BusinessException("应用不存在");
        }
        if (app.getEnabled() == null || app.getEnabled() != 1) {
            throw new BusinessException("应用已停用");
        }
        if (!app.getSsoSecret().equals(secret)) {
            throw new BusinessException("应用密钥错误");
        }
        return app;
    }
}
