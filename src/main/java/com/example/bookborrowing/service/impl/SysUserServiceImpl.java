package com.example.bookborrowing.service.impl;

import com.example.bookborrowing.common.BusinessException;
import com.example.bookborrowing.common.PageResult;
import com.example.bookborrowing.dto.UserSaveDTO;
import com.example.bookborrowing.entity.SysRole;
import com.example.bookborrowing.entity.SysUser;
import com.example.bookborrowing.mapper.SysRoleMapper;
import com.example.bookborrowing.mapper.SysUserMapper;
import com.example.bookborrowing.mapper.SysUserRoleMapper;
import com.example.bookborrowing.service.SysPermissionService;
import com.example.bookborrowing.service.SysUserService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 系统用户业务实现：BCrypt 密码校验与账号管理。
 */
@Service
public class SysUserServiceImpl implements SysUserService {

    /** 默认初始密码 */
    private static final String DEFAULT_PASSWORD = "123456";

    /** 集团管理员可授予、可维护的角色编码白名单：普通人员 + 三员（同一账号可同时担任三员） */
    private static final Set<String> GRANTABLE_ROLE_CODES =
            Set.of("NORMAL", "SEC_SYS_ADMIN", "SEC_SEC_ADMIN", "SEC_AUDIT_ADMIN");

    /** 上级/同级管理员角色：持有这些角色的账号，集团管理员不可增删改（防同级/自我提权） */
    private static final Set<String> PROTECTED_ROLE_CODES =
            Set.of("SUPER_ADMIN", "GROUP_ADMIN", "SYS_ADMIN");

    /** 超级管理员种子账号（由 sql/upgrade-super-admin.sql 预置，集团管理员不可创建/覆盖） */
    private static final String SUPER_ADMIN_USERNAME = "superadmin";

    private final SysUserMapper sysUserMapper;

    private final SysRoleMapper sysRoleMapper;

    private final SysUserRoleMapper sysUserRoleMapper;

    private final SysPermissionService sysPermissionService;

    private final BCryptPasswordEncoder passwordEncoder;

    public SysUserServiceImpl(SysUserMapper sysUserMapper, SysRoleMapper sysRoleMapper,
                              SysUserRoleMapper sysUserRoleMapper, SysPermissionService sysPermissionService) {
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.sysPermissionService = sysPermissionService;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    /**
     * 校验目标角色全部落在集团管理员可授予白名单内（NORMAL + 三个三员角色）。
     * 任何一枚角色出界即整体拒绝，杜绝授予 SYS_ADMIN / GROUP_ADMIN / SUPER_ADMIN。
     *
     * @param roleIds 目标角色ID列表（非空）
     */
    private void assertGrantableRoles(List<Long> roleIds) {
        for (Long roleId : roleIds) {
            SysRole role = roleId == null ? null : sysRoleMapper.selectById(roleId);
            if (role == null) {
                throw new BusinessException("角色不存在：" + roleId);
            }
            if (!GRANTABLE_ROLE_CODES.contains(role.getRoleCode())) {
                throw new BusinessException("集团管理员仅能授予普通人员（NORMAL）或三员角色，不能授予「"
                        + role.getRoleName() + "」");
            }
        }
    }

    /**
     * 校验目标账号可被集团管理员维护：账号现有身份必须全部落在白名单内。
     * 持有 SYS_ADMIN / GROUP_ADMIN / SUPER_ADMIN 的账号整体拒绝，同时封堵「降级/改密后接管」路径。
     *
     * @param userId 被维护账号ID
     */
    private void assertUserMaintainable(Long userId) {
        List<SysRole> existRoles = sysUserRoleMapper.selectRolesByUserId(userId);
        if (existRoles == null || existRoles.isEmpty()) {
            // 兼容历史数据：无用户-角色关联时回退 sys_user.role_id 主角色
            existRoles = new ArrayList<>();
            SysUser existUser = sysUserMapper.selectById(userId);
            if (existUser != null && existUser.getRoleId() != null) {
                SysRole primaryRole = sysRoleMapper.selectById(existUser.getRoleId());
                if (primaryRole != null) {
                    existRoles.add(primaryRole);
                }
            }
        }
        for (SysRole existRole : existRoles) {
            if (PROTECTED_ROLE_CODES.contains(existRole.getRoleCode())) {
                throw new BusinessException("「" + existRole.getRoleName()
                        + "」账号归超级管理员直接管辖，集团管理员不可新增、修改、删除或重置密码");
            }
            if (!GRANTABLE_ROLE_CODES.contains(existRole.getRoleCode())) {
                throw new BusinessException("集团管理员仅能维护普通人员（NORMAL）与三员角色账号");
            }
        }
    }

    /**
     * 归并账号的身份角色集合：roleIds 优先，主角色 roleId 保证包含在内。
     *
     * @param dto 账号参数
     * @return 去重后的角色ID列表（非空）
     */
    private List<Long> resolveRoleIds(UserSaveDTO dto) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (dto.getRoleId() != null) {
            ids.add(dto.getRoleId());
        }
        if (dto.getRoleIds() != null) {
            ids.addAll(dto.getRoleIds());
        }
        if (ids.isEmpty()) {
            throw new BusinessException("请选择账号角色");
        }
        return new ArrayList<>(ids);
    }

    @Override
    public SysUser login(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new BusinessException("账号和密码不能为空");
        }
        SysUser user = sysUserMapper.selectByUsername(username.trim());
        if (user == null) {
            throw new BusinessException("账号不存在");
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException("密码错误");
        }
        return user;
    }

    @Override
    public SysUser getById(Long id) {
        return sysUserMapper.selectById(id);
    }

    @Override
    public PageResult<SysUser> page(String keyword, Long roleId, int pageNum, int pageSize) {
        int page = Math.max(pageNum, 1);
        int size = pageSize <= 0 ? 10 : pageSize;
        int offset = (page - 1) * size;
        List<SysUser> list = sysUserMapper.selectPage(offset, size, keyword, roleId);
        list.forEach(u -> u.setPassword(null));
        long total = sysUserMapper.selectCount(keyword, roleId);
        return new PageResult<>(list, total, page, size);
    }

    @Override
    public void create(UserSaveDTO dto) {
        if (dto.getUsername() == null || dto.getUsername().isBlank()) {
            throw new BusinessException("登录账号不能为空");
        }
        String username = dto.getUsername().trim();
        if (SUPER_ADMIN_USERNAME.equalsIgnoreCase(username)) {
            throw new BusinessException("「" + SUPER_ADMIN_USERNAME + "」为系统预置的超级管理员账号，不可创建或覆盖");
        }
        if (sysUserMapper.countByUsername(username, null) > 0) {
            throw new BusinessException("登录账号已存在");
        }
        List<Long> roleIds = resolveRoleIds(dto);
        assertGrantableRoles(roleIds);
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setNickname(dto.getNickname());
        user.setRoleId(roleIds.get(0));
        String rawPassword = (dto.getPassword() == null || dto.getPassword().isBlank())
                ? DEFAULT_PASSWORD : dto.getPassword();
        user.setPassword(passwordEncoder.encode(rawPassword));
        sysUserMapper.insert(user);
        sysUserRoleMapper.insertBatch(user.getId(), roleIds);
    }

    @Override
    public void update(UserSaveDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException("用户ID不能为空");
        }
        SysUser exist = sysUserMapper.selectById(dto.getId());
        if (exist == null) {
            throw new BusinessException("账号不存在");
        }
        // 被维护账号的现有身份必须全部在可维护范围内，持有上级/同级管理员角色一律整体拒绝
        assertUserMaintainable(dto.getId());
        // 目标身份同样必须全部在白名单内（防提权）
        List<Long> roleIds = resolveRoleIds(dto);
        assertGrantableRoles(roleIds);
        SysUser user = new SysUser();
        user.setId(dto.getId());
        user.setNickname(dto.getNickname());
        user.setRoleId(roleIds.get(0));
        sysUserMapper.updateById(user);
        // 身份列表整体替换
        sysUserRoleMapper.deleteByUserId(dto.getId());
        sysUserRoleMapper.insertBatch(dto.getId(), roleIds);
        // 角色可能变化，权限缓存失效
        sysPermissionService.clearCache();
    }

    @Override
    public void delete(Long id) {
        if (sysUserMapper.selectById(id) == null) {
            throw new BusinessException("账号不存在");
        }
        // 上级/同级管理员账号（含 superadmin）不可被集团管理员删除
        assertUserMaintainable(id);
        sysUserMapper.deleteById(id);
        sysUserRoleMapper.deleteByUserId(id);
        sysPermissionService.clearCache();
    }

    @Override
    public void resetPassword(Long id, String newPassword) {
        if (sysUserMapper.selectById(id) == null) {
            throw new BusinessException("账号不存在");
        }
        // 重置密码等同接管账号，须先校验可维护范围
        assertUserMaintainable(id);
        String rawPassword = (newPassword == null || newPassword.isBlank()) ? DEFAULT_PASSWORD : newPassword;
        sysUserMapper.updatePassword(id, passwordEncoder.encode(rawPassword));
    }
}
