package com.example.bookborrowing.service;

import com.example.bookborrowing.entity.BookPurchase;
import com.example.bookborrowing.entity.IntegrationApp;
import com.example.bookborrowing.entity.IntegrationBiz;
import com.example.bookborrowing.entity.IntegrationForm;

/**
 * 出站调用类ERP平台（外部超级节点）。
 * 采购流程走到超级节点前一节点（审批）提交时，由本服务主动推送表单并挂起等待回执。
 */
public interface ErpOutboundService {

    /**
     * 调用 ERP 创建表单（POST），返回 ERP 侧表单号。
     *
     * @param biz      集成业务配置（提供超级节点名称与出站地址）
     * @param form     待推送的集成表单（提供 integration_form.id）
     * @param purchase 采购单（提供业务字段）
     * @param app      集成应用（提供应用地址与应用身份）
     * @return ERP 表单号（数值）；调用失败抛 BusinessException（中文提示，含 HTTP 状态或异常摘要）
     */
    Long pushFormToErp(IntegrationBiz biz, IntegrationForm form, BookPurchase purchase, IntegrationApp app);

    /**
     * 通知 ERP 撤销其侧流程/表单（OA 侧流程已终止：取消 / 超时打回）。
     * 失败只记录告警，不抛出异常、不影响本地事务。
     *
     * @param app        集成应用（提供应用地址与应用身份）
     * @param biz        集成业务配置（提供超级节点出站地址）
     * @param erpFormId  ERP 侧表单号（流程实例上记录的 erpFormId）
     * @param reason     撤销说明（写入 ERP 侧撤销原因）
     */
    void cancelErpForm(IntegrationApp app, IntegrationBiz biz, Long erpFormId, String reason);
}
