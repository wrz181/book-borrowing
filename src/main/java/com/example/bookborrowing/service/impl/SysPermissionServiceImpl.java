package com.example.bookborrowing.service.impl;

import com.example.bookborrowing.common.PermissionCache;
import com.example.bookborrowing.entity.SysPermission;
import com.example.bookborrowing.mapper.SysPermissionMapper;
import com.example.bookborrowing.service.SysPermissionService;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 权限点业务实现，带内存缓存。
 */
@Service
public class SysPermissionServiceImpl implements SysPermissionService {

    private final SysPermissionMapper sysPermissionMapper;

    private final PermissionCache permissionCache;

    public SysPermissionServiceImpl(SysPermissionMapper sysPermissionMapper, PermissionCache permissionCache) {
        this.sysPermissionMapper = sysPermissionMapper;
        this.permissionCache = permissionCache;
    }

    @Override
    public List<SysPermission> listAll() {
        return sysPermissionMapper.selectAll();
    }

    @Override
    public List<String> listPermCodesByRoleId(Long roleId) {
        return sysPermissionMapper.selectPermCodesByRoleId(roleId);
    }

    @Override
    public Set<String> getPermCodesByUserId(Long userId) {
        String key = "u:" + userId;
        Set<String> cached = permissionCache.get(key);
        if (cached != null) {
            return cached;
        }
        Set<String> perms = new HashSet<>(sysPermissionMapper.selectPermCodesByUserId(userId));
        permissionCache.put(key, perms);
        return perms;
    }

    @Override
    public void clearCache() {
        permissionCache.clear();
    }
}
