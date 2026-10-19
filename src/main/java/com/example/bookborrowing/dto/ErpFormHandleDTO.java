package com.example.bookborrowing.dto;

import lombok.Data;

/**
 * 外部平台办理待处理表单参数（携带单点登录票据）。
 */
@Data
public class ErpFormHandleDTO {

    /** 单点登录票据 */
    private String ticket;

    /** 处理意见 */
    private String handleComment;

    /** ERP 审批结论：true-同意 false-不同意 null-不涉及（保持旧行为，不推进流程引擎） */
    private Boolean agreed;
}
