package com.example.bookborrowing.mapper;

import com.example.bookborrowing.entity.IntegrationForm;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 集成表单 Mapper。
 */
@Mapper
public interface IntegrationFormMapper {

    /**
     * 分页查询集成表单。
     *
     * @param offset 偏移量
     * @param limit  每页条数
     * @param appId  应用ID（可空）
     * @param status 状态（可空）
     * @return 表单列表
     */
    List<IntegrationForm> selectPage(@Param("offset") int offset,
                                     @Param("limit") int limit,
                                     @Param("appId") Long appId,
                                     @Param("status") Integer status);

    /**
     * 统计集成表单总数。
     *
     * @param appId  应用ID（可空）
     * @param status 状态（可空）
     * @return 总记录数
     */
    long selectCount(@Param("appId") Long appId, @Param("status") Integer status);

    /**
     * 按ID查询表单。
     *
     * @param id 表单ID
     * @return 表单实体，不存在返回 null
     */
    IntegrationForm selectById(Long id);

    /**
     * 新增表单。
     *
     * @param form 表单实体
     * @return 影响行数
     */
    int insert(IntegrationForm form);

    /**
     * 处理表单：置为已处理并记录处理意见。
     *
     * @param id            表单ID
     * @param handleTime    处理时间
     * @param handleComment 处理意见
     * @return 影响行数
     */
    int updateHandle(@Param("id") Long id,
                     @Param("handleTime") LocalDateTime handleTime,
                     @Param("handleComment") String handleComment);
}
