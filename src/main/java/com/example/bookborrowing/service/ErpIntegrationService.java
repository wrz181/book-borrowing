package com.example.bookborrowing.service;

import com.example.bookborrowing.common.PageResult;
import com.example.bookborrowing.dto.ErpFormSubmitDTO;
import com.example.bookborrowing.dto.ErpSsoUserDTO;
import com.example.bookborrowing.entity.IntegrationForm;

import java.util.List;

/**
 * 类ERP平台集成业务接口：票据校验、表单下发/回传、待办与消息生成。
 */
public interface ErpIntegrationService {

    /**
     * 类ERP平台提交表单：校验票据 → 保存集成表单 → 生成待办 → 发送消息。
     *
     * @param dto 回传参数
     * @return 保存后的集成表单
     */
    IntegrationForm submitForm(ErpFormSubmitDTO dto);

    /**
     * 单点登录校验：外部平台凭票据换取登录用户信息（用于「登录态」展示）。
     *
     * @param ticket 单点登录票据
     * @return 登录用户信息
     */
    ErpSsoUserDTO validateSso(String ticket);

    /**
     * 查询当前票据所属应用的集成表单（默认仅待处理）。
     *
     * @param ticket 单点登录票据
     * @param status 状态（可空，默认 0-待处理）
     * @return 表单列表
     */
    List<IntegrationForm> listForms(String ticket, Integer status);

    /**
     * 外部平台办理待处理表单：置为已处理 → 生成待办 → 发送消息给发起人。
     *
     * @param formId  表单ID
     * @param ticket  单点登录票据
     * @param comment 处理意见
     * @param agreed  ERP 审批结论：true-同意 false-不同意 null-不涉及（保持旧行为）
     * @return 处理后的表单
     */
    IntegrationForm handleForm(Long formId, String ticket, String comment, Boolean agreed);

    /**
     * 分页查询集成表单（集团管理员查看下发/回流记录）。
     *
     * @param appId    应用ID（可空）
     * @param status   状态（可空）
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    PageResult<IntegrationForm> page(Long appId, Integer status, int pageNum, int pageSize);
}
