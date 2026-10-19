package com.example.bookborrowing.dto;

import lombok.Data;

/**
 * 类ERP平台回传表单参数（携带单点登录票据）。
 */
@Data
public class ErpFormSubmitDTO {

    /** 单点登录票据 */
    private String ticket;

    /** 表单标题 */
    private String title;

    /** 表单类型：BOOK_PURCHASE-图书采购 BORROW_APPLY-借阅申请 */
    private String formType;

    /** 表单内容 */
    private String content;

    /** 处理意见 */
    private String handleComment;
}
