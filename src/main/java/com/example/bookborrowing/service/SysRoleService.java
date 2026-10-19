package com.example.bookborrowing.service;

import com.example.bookborrowing.entity.SysRole;

import java.util.List;

/**
 * 角色业务接口。
 */
public interface SysRoleService {

    /**
     * 查询全部角色，并组装每个角色的权限编码集合。
     *
     * @return 角色列表
     */
    List<SysRole> listAllWithPermissions();

    /**
     * 更新角色权限。
     *
     * @param roleId    角色ID
     * @param permCodes 权限编码集合
     */
    void updateRolePermissions(Long roleId, List<String> permCodes);
}
