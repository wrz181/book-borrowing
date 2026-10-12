package com.example.bookborrowing.dto;

import lombok.Data;

/**
 * 图书列表动态查询条件：各条件均可单独使用，也可组合使用。
 */
@Data
public class BookQueryDTO {

    /** 图书名称（模糊匹配） */
    private String bookName;

    /** 作者（模糊匹配） */
    private String author;

    /** 所属分类ID（精确匹配） */
    private Long categoryId;

    /** 图书状态：1-在架可借 0-已下架 */
    private Integer status;
}
