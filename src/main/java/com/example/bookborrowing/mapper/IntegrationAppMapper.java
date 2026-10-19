package com.example.bookborrowing.mapper;

import com.example.bookborrowing.entity.IntegrationApp;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 单点登录应用配置 Mapper。
 */
@Mapper
public interface IntegrationAppMapper {

    /**
     * 查询全部应用（含停用），按ID倒序。
     *
     * @return 应用列表
     */
    List<IntegrationApp> selectAll();

    /**
     * 查询已启用的应用（前台动态栏目用）。
     *
     * @return 应用列表
     */
    List<IntegrationApp> selectEnabled();

    /**
     * 按ID查询应用。
     *
     * @param id 应用ID
     * @return 应用实体，不存在返回 null
     */
    IntegrationApp selectById(Long id);

    /**
     * 新增应用。
     *
     * @param app 应用实体
     * @return 影响行数
     */
    int insert(IntegrationApp app);

    /**
     * 更新应用。
     *
     * @param app 应用实体
     * @return 影响行数
     */
    int updateById(IntegrationApp app);

    /**
     * 按ID逻辑删除应用。
     *
     * @param id 应用ID
     * @return 影响行数
     */
    int deleteById(Long id);
}
