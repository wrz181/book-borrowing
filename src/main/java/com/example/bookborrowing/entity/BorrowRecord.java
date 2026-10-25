package com.example.bookborrowing.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 借阅记录实体，对应表 borrow_record。
 * 其中 bookName / author / categoryName 为非数据库字段，用于关联查询结果承载。
 */
@Data
public class BorrowRecord {

    /** 主键ID */
    private Long id;

    /** 借阅图书ID */
    private Long bookId;

    /** 借阅人姓名 */
    private String borrowerName;

    /** 借阅人学号/借阅证号 */
    private String borrowerNo;

    /** 借阅人用户ID（关联 sys_user.id，数据范围控制用） */
    private Long borrowerId;

    /** 借阅日期 */
    private LocalDate borrowDate;

    /** 应还日期 */
    private LocalDate dueDate;

    /** 实际归还日期（未归还为空） */
    private LocalDate returnDate;

    /** 状态：0-待确认 1-借阅中 2-已归还 3-已驳回 4-归还待确认 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 逻辑删除标记：0-未删除 1-已删除 */
    private Integer isDeleted;

    /** 图书名称（关联查询字段，非数据库列） */
    private String bookName;

    /** 作者（关联查询字段，非数据库列） */
    private String author;

    /** 分类名称（关联查询字段，非数据库列） */
    private String categoryName;
}
