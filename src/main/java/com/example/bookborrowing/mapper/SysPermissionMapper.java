package com.example.bookborrowing.mapper;

import com.example.bookborrowing.entity.SysPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 权限点 Mapper。
 */
@Mapper
public interface SysPermissionMapper {

    /**
     * 查询全部权限点，按模块、排序号排序。
     *
     * @return 权限点列表
     */
    List<SysPermission> selectAll();

    /**
     * 查询指定角色拥有的权限编码。
     *
     * @param roleId 角色ID
     * @return 权限编码列表
     */
    List<String> selectPermCodesByRoleId(Long roleId);

    /**
     * 查询指定用户经由其角色获得的权限编码。
     *
     * @param userId 用户ID
     * @return 权限编码列表
     */
    List<String> selectPermCodesByUserId(Long userId);

    /**
     * 查询指定用户在指定身份角色下的权限编码。
     *
     * @param userId 用户ID
     * @param roleId 身份角色ID（须属于该用户）
     * @return 权限编码列表
     */
    List<String> selectPermCodesByUserIdAndRole(@Param("userId") Long userId, @Param("roleId") Long roleId);
}
