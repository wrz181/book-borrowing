package com.example.bookborrowing.mapper;

import com.example.bookborrowing.entity.IntegrationBiz;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 集成业务配置 Mapper：超级节点配置的增删改查与分页检索。
 * 除 selectById/selectPage/selectCount 外，查询均以 is_deleted = 0 为前提。
 */
@Mapper
public interface IntegrationBizMapper {

    /**
     * 按ID查询业务配置（未删除）。
     *
     * @param id 业务配置ID
     * @return 业务配置实体，不存在返回 null
     */
    IntegrationBiz selectById(Long id);

    /**
     * 按业务类型查询业务配置：仅返回 enabled = 1 且 is_deleted = 0 的记录，
     * 供流程引擎取超级节点配置使用。
     *
     * @param bizType 业务类型，如 BOOK_PURCHASE
     * @return 业务配置实体，未配置或已停用返回 null
     */
    IntegrationBiz selectByBizType(String bizType);

    /**
     * 分页动态条件查询业务配置（业务类型、业务名称模糊匹配，启用状态精确过滤）。
     *
     * @param bizType 业务类型（可空，模糊匹配）
     * @param bizName 业务名称（可空，模糊匹配）
     * @param enabled 是否启用（可空）
     * @param offset  偏移量
     * @param limit   每页条数
     * @return 业务配置列表
     */
    List<IntegrationBiz> selectPage(@Param("bizType") String bizType,
                                    @Param("bizName") String bizName,
                                    @Param("enabled") Integer enabled,
                                    @Param("offset") int offset,
                                    @Param("limit") int limit);

    /**
     * 统计符合条件的业务配置总数（与 selectPage 条件一致）。
     *
     * @param bizType 业务类型（可空，模糊匹配）
     * @param bizName 业务名称（可空，模糊匹配）
     * @param enabled 是否启用（可空）
     * @return 总记录数
     */
    long selectCount(@Param("bizType") String bizType,
                     @Param("bizName") String bizName,
                     @Param("enabled") Integer enabled);

    /**
     * 新增业务配置。
     *
     * @param biz 业务配置实体
     * @return 影响行数
     */
    int insert(IntegrationBiz biz);

    /**
     * 按ID更新业务配置（全字段更新）。
     *
     * @param biz 业务配置实体
     * @return 影响行数
     */
    int updateById(IntegrationBiz biz);

    /**
     * 按ID物理删除业务配置（DELETE，非逻辑删除）。
     * 删除后同一 biz_type 可重新新增，唯一键 uk_biz_type 不再被历史行占用。
     *
     * @param id 业务配置ID
     * @return 影响行数
     */
    int deleteById(Long id);

    /**
     * 统计某业务类型已配置的记录数（未删除），用于唯一性校验。
     *
     * @param bizType   业务类型
     * @param excludeId 需要排除的ID（修改时排除自身，可空）
     * @return 记录数
     */
    long countByBizType(@Param("bizType") String bizType, @Param("excludeId") Long excludeId);
}
