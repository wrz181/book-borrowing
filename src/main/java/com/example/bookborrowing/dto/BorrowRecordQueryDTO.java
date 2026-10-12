package com.example.bookborrowing.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 借阅记录动态查询条件：支持图书名称、借阅人、状态及借阅日期范围组合查询。
 */
@Data
public class BorrowRecordQueryDTO {

    /** 图书名称（模糊匹配） */
    private String bookName;

    /** 借阅人姓名（模糊匹配） */
    private String borrowerName;

    /** 借阅人学号/借阅证号（模糊匹配） */
    private String borrowerNo;

    /** 记录状态：1-借阅中 2-已归还 */
    private Integer status;

    /** 借阅日期起（含） */
    private LocalDate beginDate;

    /** 借阅日期止（含） */
    private LocalDate endDate;
}
