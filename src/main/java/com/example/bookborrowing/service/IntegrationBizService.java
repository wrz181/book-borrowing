package com.example.bookborrowing.service;

import com.example.bookborrowing.common.PageResult;
import com.example.bookborrowing.entity.IntegrationBiz;

/**
 * 集成业务配置（超级节点配置）业务接口。
 * 流程引擎在进入超级节点前调用 {@link #getByBizType(String)} 取配置，不再由前端传 appId。
 */
public interface IntegrationBizService {

    /**
     * 按业务类型取超级节点配置：只返回 enabled = 1 且未删除的记录。
     *
     * @param bizType 业务类型，如 BOOK_PURCHASE
     * @return 业务配置实体，未配置或已停用返回 null
     */
    IntegrationBiz getByBizType(String bizType);

    /**
     * 分页查询业务配置。
     *
     * @param pageNum  页码，从1开始
     * @param pageSize 每页条数
     * @param bizType  业务类型（可空，模糊匹配）
     * @param bizName  业务名称（可空，模糊匹配）
     * @param enabled  是否启用（可空）
     * @return 分页结果
     */
    PageResult<IntegrationBiz> page(int pageNum, int pageSize, String bizType, String bizName, Integer enabled);

    /**
     * 按ID查询业务配置。
     *
     * @param id 业务配置ID
     * @return 业务配置实体，不存在返回 null
     */
    IntegrationBiz getById(Long id);

    /**
     * 新增业务配置。校验业务类型唯一、绑定应用存在且启用、超级节点名称非空、超时大于0。
     *
     * @param biz 业务配置实体
     */
    void create(IntegrationBiz biz);

    /**
     * 修改业务配置。校验规则同新增，业务类型唯一性排除自身。
     *
     * @param id  业务配置ID
     * @param biz 业务配置实体（未传字段沿用库中原值）
     */
    void update(Long id, IntegrationBiz biz);

    /**
     * 删除业务配置（物理删除：调用方须注意删后同一业务类型可重新新增）。
     *
     * @param id 业务配置ID
     */
    void delete(Long id);
}
