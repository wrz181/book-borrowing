package com.example.bookborrowing.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单点登录 ticket 解析结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SsoTicketDTO {

    /** 应用ID */
    private Long appId;

    /** 用户ID */
    private Long userId;
}
