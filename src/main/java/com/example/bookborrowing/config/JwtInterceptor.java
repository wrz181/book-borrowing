package com.example.bookborrowing.config;

import com.example.bookborrowing.entity.SysUser;
import com.example.bookborrowing.mapper.SysUserMapper;
import com.example.bookborrowing.service.support.RedisSupportService;
import com.example.bookborrowing.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 登录拦截器：除登录接口外，所有 /api/** 请求均需携带有效 token。
 * 解析成功后向请求注入 currentUserId 与 currentRole，供控制器做角色判断。
 * <p>
 * 云计算扩展：JWT 签名校验通过后，再校验 Redis token 白名单（分布式会话吊销）；
 * Redis 不可用时跳过白名单校验，退回纯 JWT 模式，保证业务可用性。
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {

    private static final String AUTH_HEADER = "Authorization";

    private static final String TOKEN_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;

    private final SysUserMapper sysUserMapper;

    private final RedisSupportService redisSupport;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** JWT 有效期（小时），与 Redis 白名单 TTL 保持一致 */
    @Value("${jwt.expire-hours:24}")
    private long expireHours;

    public JwtInterceptor(JwtUtil jwtUtil, SysUserMapper sysUserMapper, RedisSupportService redisSupport) {
        this.jwtUtil = jwtUtil;
        this.sysUserMapper = sysUserMapper;
        this.redisSupport = redisSupport;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 预检请求直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String token = request.getHeader(AUTH_HEADER);
        if (token == null || !token.startsWith(TOKEN_PREFIX)) {
            writeUnauthorized(response, "未登录或登录已过期");
            return false;
        }
        token = token.substring(TOKEN_PREFIX.length());
        try {
            Long userId = jwtUtil.parseUserId(token);
            SysUser user = userId == null ? null : sysUserMapper.selectById(userId);
            if (user == null) {
                writeUnauthorized(response, "登录状态无效，请重新登录");
                return false;
            }
            // 分布式会话校验：登出/被吊销的 token 直接拦截（Redis 不可用时此步自动放行）
            if (!redisSupport.tokenAlive(token, user.getId(), Duration.ofHours(expireHours))) {
                writeUnauthorized(response, "登录已失效，请重新登录");
                return false;
            }
            request.setAttribute("currentUserId", user.getId());
            request.setAttribute("currentRole", user.getRole());
            request.setAttribute("currentUserName",
                    user.getNickname() == null || user.getNickname().isBlank() ? user.getUsername() : user.getNickname());
            return true;
        } catch (Exception e) {
            writeUnauthorized(response, "登录状态无效，请重新登录");
            return false;
        }
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        Map<String, Object> body = new HashMap<>();
        body.put("code", 401);
        body.put("message", message);
        body.put("data", null);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
