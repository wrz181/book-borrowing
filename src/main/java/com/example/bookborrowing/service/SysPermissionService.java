package com.example.bookborrowing.service;

import com.example.bookborrowing.entity.SysPermission;

import java.util.List;
import java.util.Set;

/**
 * 权限点业务接口。
 */
public interface SysPermissionService {

    /**
     * 查询全部权限点。
     *
     * @return 权限点列表
     */
    List<SysPermission> listAll();

    /**
     * 查询指定角色拥有的权限编码。
     *
     * @param roleId 角色ID
     * @return 权限编码列表
     */
    List<String> listPermCodesByRoleId(Long roleId);

    /**
     * 查询指定用户的权限编码集合（带缓存）。
     *
     * @param userId 用户ID
     * @return 权限编码集合
     */
    Set<String> getPermCodesByUserId(Long userId);

    /**
     * 清空权限缓存（角色权限或账号角色变更后调用）。
     */
    void clearCache();
}
