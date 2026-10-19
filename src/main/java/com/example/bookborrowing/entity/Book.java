package com.example.bookborrowing.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 图书实体，对应表 book。
 * 其中 categoryName / borrowRecords 为非数据库字段，用于关联查询结果承载。
 */
@Data
public class Book {

    /** 主键ID */
    private Long id;

    /** 图书名称 */
    private String bookName;

    /** 作者 */
    private String author;

    /** ISBN编号 */
    private String isbn;

    /** 出版社 */
    private String publisher;

    /** 所属分类ID */
    private Long categoryId;

    /** 单价 */
    private BigDecimal price;

    /** 库存数量 */
    private Integer stock;

    /** 状态：1-在架可借 0-已下架 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 逻辑删除标记：0-未删除 1-已删除 */
    private Integer isDeleted;

    /** 分类名称（关联查询字段，非数据库列） */
    private String categoryName;

    /** 该图书的全部借阅记录（一对多关联查询字段，非数据库列） */
    private List<BorrowRecord> borrowRecords;
}
