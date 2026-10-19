package com.example.bookborrowing.config;

import com.example.bookborrowing.annotation.RequirePermission;
import com.example.bookborrowing.common.PermissionCache;
import com.example.bookborrowing.common.Result;
import com.example.bookborrowing.mapper.SysPermissionMapper;
import com.example.bookborrowing.mapper.SysUserRoleMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 权限拦截器：在 JWT 拦截器之后执行，校验 {@link RequirePermission} 注解声明的权限。
 * 权限为后端强校验，前端隐藏按钮仅作体验层处理。
 */
@Component
public class PermissionInterceptor implements HandlerInterceptor {

    private final SysPermissionMapper sysPermissionMapper;

    private final SysUserRoleMapper sysUserRoleMapper;

    private final PermissionCache permissionCache;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public PermissionInterceptor(SysPermissionMapper sysPermissionMapper, SysUserRoleMapper sysUserRoleMapper,
                                 PermissionCache permissionCache) {
        this.sysPermissionMapper = sysPermissionMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.permissionCache = permissionCache;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RequirePermission annotation = handlerMethod.getMethodAnnotation(RequirePermission.class);
        if (annotation == null) {
            // 回退到类级注解（@RequirePermission 可标注在 Controller 类上）
            annotation = org.springframework.core.annotation.AnnotatedElementUtils
                    .findMergedAnnotation(handlerMethod.getBeanType(), RequirePermission.class);
        }
        if (annotation == null) {
            return true;
        }
        Object userIdAttr = request.getAttribute("currentUserId");
        if (!(userIdAttr instanceof Long userId)) {
            writeForbidden(response, 401, "未登录或登录已过期");
            return false;
        }
        // 顶栏身份切换：X-Active-Role 指定当前身份，须属于该用户；缺省按全身份权限并集
        Long activeRole = parseActiveRole(request);
        if (activeRole != null && sysUserRoleMapper.existsUserRole(userId, activeRole) == 0) {
            writeForbidden(response, 403, "无效的身份角色");
            return false;
        }
        Set<String> owned = loadPermissions(userId, activeRole);
        // value()：需全部满足（既有单值用法语义不变）
        for (String required : annotation.value()) {
            if (!owned.contains(required)) {
                writeForbidden(response, 403, "无操作权限：" + required);
                return false;
            }
        }
        // anyOf()：满足其一即可
        String[] anyOf = annotation.anyOf();
        if (anyOf.length > 0 && !containsAny(owned, anyOf)) {
            writeForbidden(response, 403, "无操作权限：" + String.join(" 或 ", anyOf));
            return false;
        }
        return true;
    }

    /**
     * 判断权限集合是否命中任意一个权限编码。
     *
     * @param owned 已拥有的权限集合
     * @param codes 要求的权限编码
     * @return 命中任意一个返回 true
     */
    private static boolean containsAny(Set<String> owned, String[] codes) {
        for (String code : codes) {
            if (owned.contains(code)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 解析请求头中的当前身份角色ID。
     *
     * @param request HTTP 请求
     * @return 角色ID；未携带或格式非法返回 null（按全身份并集处理）
     */
    private Long parseActiveRole(HttpServletRequest request) {
        String header = request.getHeader("X-Active-Role");
        if (header == null || header.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(header.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 读取用户权限集合（指定身份时按该身份解析），优先走缓存。
     *
     * @param userId     用户ID
     * @param activeRole 当前身份角色ID，null 表示全身份并集
     * @return 权限集合
     */
    private Set<String> loadPermissions(Long userId, Long activeRole) {
        String key = activeRole == null ? "u:" + userId : "u:" + userId + ":r:" + activeRole;
        Set<String> cached = permissionCache.get(key);
        if (cached != null) {
            return cached;
        }
        List<String> codes = activeRole == null
                ? sysPermissionMapper.selectPermCodesByUserId(userId)
                : sysPermissionMapper.selectPermCodesByUserIdAndRole(userId, activeRole);
        Set<String> perms = new HashSet<>(codes);
        permissionCache.put(key, perms);
        return perms;
    }

    private void writeForbidden(HttpServletResponse response, int code, String message) throws Exception {
        response.setStatus(code == 401 ? HttpServletResponse.SC_UNAUTHORIZED : HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        Result<Void> body = Result.error(code, message);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
