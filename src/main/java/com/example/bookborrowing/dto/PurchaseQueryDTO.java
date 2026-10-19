package com.example.bookborrowing.dto;

import lombok.Data;

/**
 * 图书采购单动态查询条件：各条件可单独使用，也可组合使用。
 */
@Data
public class PurchaseQueryDTO {

    /** 采购单号（模糊） */
    private String purchaseNo;

    /** 图书名称（模糊） */
    private String bookName;

    /** 所属分类ID */
    private Long categoryId;

    /** 状态：0-草稿 1-已提交 2-已入库 9-已取消 */
    private Integer status;

    /** 下发的集成应用ID */
    private Long appId;
}
