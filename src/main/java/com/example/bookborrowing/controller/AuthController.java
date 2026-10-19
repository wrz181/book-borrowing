package com.example.bookborrowing.controller;

import com.example.bookborrowing.common.BusinessException;
import com.example.bookborrowing.common.Result;
import com.example.bookborrowing.dto.LoginDTO;
import com.example.bookborrowing.entity.SysRole;
import com.example.bookborrowing.entity.SysUser;
import com.example.bookborrowing.mapper.SysPermissionMapper;
import com.example.bookborrowing.mapper.SysUserRoleMapper;
import com.example.bookborrowing.service.SysPermissionService;
import com.example.bookborrowing.service.SysUserService;
import com.example.bookborrowing.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 登录认证接口：JWT 签发、当前用户信息与权限查询。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final SysUserService sysUserService;

    private final SysPermissionService sysPermissionService;

    private final SysPermissionMapper sysPermissionMapper;

    private final SysUserRoleMapper sysUserRoleMapper;

    private final JwtUtil jwtUtil;

    public AuthController(SysUserService sysUserService,
                          SysPermissionService sysPermissionService,
                          SysPermissionMapper sysPermissionMapper,
                          SysUserRoleMapper sysUserRoleMapper,
                          JwtUtil jwtUtil) {
        this.sysUserService = sysUserService;
        this.sysPermissionService = sysPermissionService;
        this.sysPermissionMapper = sysPermissionMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.jwtUtil = jwtUtil;
    }

    /**
     * 登录：校验账号密码，签发 JWT token，并返回角色与权限编码。
     *
     * @param loginDTO 登录参数
     * @return token、用户信息与权限集合
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginDTO loginDTO) {
        SysUser user;
        try {
            user = sysUserService.login(loginDTO.getUsername(), loginDTO.getPassword());
        } catch (BusinessException e) {
            // 登录失败为可预期的业务分支，记录用户名与原因，不记录密码
            log.warn("用户登录失败：账号={}，原因={}", loginDTO.getUsername(), e.getMessage());
            throw e;
        }
        String token = jwtUtil.generate(user.getId(), user.getUsername());
        log.info("用户登录成功：账号={}，用户ID={}，角色={}", user.getUsername(), user.getId(), user.getRoleCode());

        Map<String, Object> data = new HashMap<>(8);
        data.put("token", token);
        data.put("userInfo", buildUserInfo(user));
        data.put("roles", buildRoles(user.getId()));
        data.put("activeRoleId", user.getRoleId());
        data.put("permissions", permsFor(user.getId(), user.getRoleId()));
        return Result.success(data);
    }

    /**
     * 查询当前登录用户信息与权限。
     *
     * @param request HTTP 请求（拦截器已注入 currentUserId）
     * @return 用户信息与权限集合
     */
    @GetMapping("/info")
    public Result<Map<String, Object>> info(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        SysUser user = sysUserService.getById(userId);
        if (user == null) {
            log.warn("查询当前用户信息失败：登录状态无效，userId={}", userId);
            return Result.error(401, "登录状态无效，请重新登录");
        }
        Long activeRoleId = resolveActiveRole(request, user);
        log.debug("查询当前用户信息：userId={}，身份角色ID={}", userId, activeRoleId);
        Map<String, Object> data = new HashMap<>(8);
        data.put("userInfo", buildUserInfo(user));
        data.put("roles", buildRoles(user.getId()));
        data.put("activeRoleId", activeRoleId);
        data.put("permissions", permsFor(userId, activeRoleId));
        return Result.success(data);
    }

    /**
     * 解析当前身份角色：优先取请求头 X-Active-Role（须属于该用户），缺省为主角色。
     *
     * @param request HTTP 请求
     * @param user    当前用户
     * @return 身份角色ID
     */
    private Long resolveActiveRole(HttpServletRequest request, SysUser user) {
        String header = request.getHeader("X-Active-Role");
        if (header != null && !header.isBlank()) {
            try {
                Long roleId = Long.parseLong(header.trim());
                if (sysUserRoleMapper.existsUserRole(user.getId(), roleId) > 0) {
                    return roleId;
                }
            } catch (NumberFormatException ignored) {
                // 非法头按缺省身份处理
            }
        }
        return user.getRoleId();
    }

    /**
     * 按身份角色取权限；角色无效或为空时取全身份权限并集。
     *
     * @param userId 用户ID
     * @param roleId 身份角色ID
     * @return 权限编码集合
     */
    private Set<String> permsFor(Long userId, Long roleId) {
        if (roleId != null && sysUserRoleMapper.existsUserRole(userId, roleId) > 0) {
            return new HashSet<>(sysPermissionMapper.selectPermCodesByUserIdAndRole(userId, roleId));
        }
        return sysPermissionService.getPermCodesByUserId(userId);
    }

    /**
     * 组装用户身份角色列表。
     *
     * @param userId 用户ID
     * @return 角色信息列表（roleId/roleCode/roleName）
     */
    private List<Map<String, Object>> buildRoles(Long userId) {
        List<Map<String, Object>> roles = new ArrayList<>();
        for (SysRole role : sysUserRoleMapper.selectRolesByUserId(userId)) {
            Map<String, Object> item = new HashMap<>(4);
            item.put("roleId", role.getId());
            item.put("roleCode", role.getRoleCode());
            item.put("roleName", role.getRoleName());
            roles.add(item);
        }
        return roles;
    }

    /**
     * 组装用户基本信息（不含密码）。
     *
     * @param user 用户实体
     * @return 用户信息
     */
    private Map<String, Object> buildUserInfo(SysUser user) {
        Map<String, Object> userInfo = new HashMap<>(8);
        userInfo.put("id", user.getId());
        userInfo.put("username", user.getUsername());
        userInfo.put("nickname", user.getNickname());
        userInfo.put("roleId", user.getRoleId());
        userInfo.put("roleCode", user.getRoleCode());
        userInfo.put("roleName", user.getRoleName());
        return userInfo;
    }
}
