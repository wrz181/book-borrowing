package com.example.bookborrowing.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 图书采购单实体，对应表 book_purchase。
 * 业务闭环：草稿 → 提交下发外部集成应用 → 对方回执 → 入库（增加或新增图书库存）。
 */
@Data
public class BookPurchase {

    /** 状态：草稿 */
    public static final int STATUS_DRAFT = 0;

    /** 状态：已提交（已下发外部平台） */
    public static final int STATUS_SUBMITTED = 1;

    /** 状态：已入库 */
    public static final int STATUS_STOCKED_IN = 2;

    /** 状态：已取消 */
    public static final int STATUS_CANCELED = 9;

    /** 主键ID */
    private Long id;

    /** 采购单号 */
    private String purchaseNo;

    /** 图书名称 */
    private String bookName;

    /** 作者 */
    private String author;

    /** ISBN编号（入库匹配依据） */
    private String isbn;

    /** 出版社 */
    private String publisher;

    /** 所属分类ID（入库归入分类） */
    private Long categoryId;

    /** 采购数量 */
    private Integer quantity;

    /** 单价 */
    private BigDecimal unitPrice;

    /** 总价 */
    private BigDecimal totalPrice;

    /** 申请人ID */
    private Long applicantId;

    /** 申请人姓名 */
    private String applicantName;

    /** 下发的集成应用ID */
    private Long appId;

    /** 关联集成表单ID（下发后生成） */
    private Long integrationFormId;

    /** 状态：0-草稿 1-已提交 2-已入库 9-已取消 */
    private Integer status;

    /** 期望到货日期 */
    private LocalDate expectedDate;

    /** 备注 */
    private String remark;

    /** 提交（下发）时间 */
    private LocalDateTime submitTime;

    /** 入库时间 */
    private LocalDateTime stockInTime;

    /** 取消时间 */
    private LocalDateTime cancelTime;

    /** 入库图书ID */
    private Long stockInBookId;

    /** 入库结果说明 */
    private String stockInRemark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 逻辑删除标记：0-未删除 1-已删除 */
    private Integer isDeleted;

    /** 分类名称（非表字段，关联查询） */
    private String categoryName;

    /** 集成应用名称（非表字段，关联查询） */
    private String appName;

    /** 关联集成表单状态：0-待处理 1-已处理（非表字段，关联查询；未下发为 null） */
    private Integer formStatus;
}
