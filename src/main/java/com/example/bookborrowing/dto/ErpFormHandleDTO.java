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
}
