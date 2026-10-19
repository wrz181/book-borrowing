package com.example.bookborrowing.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 集成业务配置实体，对应表 integration_biz。
 * 描述某类业务（如图书采购）在流程中使用的超级节点：绑定哪个集成应用、节点名称、回调超时。
 * 一个业务类型只配置一个超级节点，由唯一键 uk_biz_type 保证。
 */
@Data
public class IntegrationBiz {

    /** 业务类型：图书采购（默认种子数据） */
    public static final String BIZ_TYPE_BOOK_PURCHASE = "BOOK_PURCHASE";

    /** 主键ID */
    private Long id;

    /** 业务类型，如 BOOK_PURCHASE（唯一） */
    private String bizType;

    /** 业务类型名称，如 图书采购 */
    private String bizName;

    /** 绑定的集成应用ID（integration_app.id） */
    private Long appId;

    /** 超级节点名称，如 ERP采购审批 */
    private String superNodeName;

    /** 出站地址；为空则取 app_url + /erp/forms */
    private String superNodeUrl;

    /** 回调超时（分钟），默认 1440 = 24 小时 */
    private Integer callbackTimeoutMinutes;

    /** 是否启用：1-启用 0-停用 */
    private Integer enabled;

    /** 备注 */
    private String description;

    /** 逻辑删除标记：0-未删除 1-已删除（删除已改为物理删除，字段保留以兼容历史数据；查询条件仍带 is_deleted = 0） */
    private Integer isDeleted;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
