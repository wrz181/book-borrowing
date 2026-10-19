package com.example.bookborrowing.controller;

import com.example.bookborrowing.annotation.RequirePermission;
import com.example.bookborrowing.common.Result;
import com.example.bookborrowing.dto.RolePermSaveDTO;
import com.example.bookborrowing.entity.SysPermission;
import com.example.bookborrowing.entity.SysRole;
import com.example.bookborrowing.service.SysPermissionService;
import com.example.bookborrowing.service.SysRoleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 集团管理员 - 角色权限配置接口。
 * 需要 group:role 权限。
 */
@RestController
@RequestMapping("/api/admin/roles")
@RequirePermission("group:role")
public class RolePermissionController {

    private final SysRoleService sysRoleService;

    private final SysPermissionService sysPermissionService;

    public RolePermissionController(SysRoleService sysRoleService, SysPermissionService sysPermissionService) {
        this.sysRoleService = sysRoleService;
        this.sysPermissionService = sysPermissionService;
    }

    /**
     * 查询全部角色（含已配置的权限编码）。
     *
     * @return 角色列表
     */
    @GetMapping
    public Result<List<SysRole>> list() {
        return Result.success(sysRoleService.listAllWithPermissions());
    }

    /**
     * 查询全部权限点（供勾选）。
     *
     * @return 权限点列表
     */
    @GetMapping("/permissions")
    public Result<List<SysPermission>> permissions() {
        return Result.success(sysPermissionService.listAll());
    }

    /**
     * 更新指定角色的权限。
     *
     * @param id   角色ID
     * @param body 权限编码集合
     * @return 空响应
     */
    @PostMapping("/{id}/permissions")
    public Result<Void> updatePermissions(@PathVariable Long id, @RequestBody RolePermSaveDTO body) {
        sysRoleService.updateRolePermissions(id, body.getPermCodes());
        return Result.success();
    }
}
