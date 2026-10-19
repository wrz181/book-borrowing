package com.example.bookborrowing.service.impl;

import com.example.bookborrowing.common.BusinessException;
import com.example.bookborrowing.entity.SysRole;
import com.example.bookborrowing.mapper.SysPermissionMapper;
import com.example.bookborrowing.mapper.SysRoleMapper;
import com.example.bookborrowing.service.SysPermissionService;
import com.example.bookborrowing.service.SysRoleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 角色业务实现。
 */
@Service
public class SysRoleServiceImpl implements SysRoleService {

    /** 超级管理员角色编码：最高权限层，权限全量内置，任何界面不可修改、不可删除 */
    private static final String SUPER_ADMIN_ROLE_CODE = "SUPER_ADMIN";

    private final SysRoleMapper sysRoleMapper;

    private final SysPermissionMapper sysPermissionMapper;

    private final SysPermissionService sysPermissionService;

    public SysRoleServiceImpl(SysRoleMapper sysRoleMapper,
                              SysPermissionMapper sysPermissionMapper,
                              SysPermissionService sysPermissionService) {
        this.sysRoleMapper = sysRoleMapper;
        this.sysPermissionMapper = sysPermissionMapper;
        this.sysPermissionService = sysPermissionService;
    }

    @Override
    public List<SysRole> listAllWithPermissions() {
        List<SysRole> roles = sysRoleMapper.selectAll();
        for (SysRole role : roles) {
            role.setPermissions(sysPermissionMapper.selectPermCodesByRoleId(role.getId()));
        }
        return roles;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRolePermissions(Long roleId, List<String> permCodes) {
        SysRole role = sysRoleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException("角色不存在");
        }
        if (SUPER_ADMIN_ROLE_CODE.equals(role.getRoleCode())) {
            // 超级管理员是最高权限层：权限由系统内置全量授权（含 group:* 与 system:*），不接受任何调整
            throw new BusinessException("超级管理员（SUPER_ADMIN）为最高权限层，拥有全部权限，不可修改");
        }
        if (role.getIsBuiltin() != null && role.getIsBuiltin() == 1
                && !"NORMAL".equals(role.getRoleCode())) {
            // 内置管理员角色权限锁定，仅允许配置普通人员角色
            throw new BusinessException("内置管理员角色的权限不可修改");
        }
        List<String> codes = permCodes == null ? new ArrayList<>() : permCodes;
        sysRoleMapper.deleteRolePermissions(roleId);
        if (!codes.isEmpty()) {
            sysRoleMapper.insertRolePermissions(roleId, codes);
        }
        sysPermissionService.clearCache();
    }
}
