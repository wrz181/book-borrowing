package com.example.bookborrowing.config;

import com.example.bookborrowing.entity.SysUser;
import com.example.bookborrowing.mapper.SysUserMapper;
import com.example.bookborrowing.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.HashMap;
import java.util.Map;

/**
 * JWT 登录拦截器：除登录接口外，所有 /api/** 请求均需携带有效 token。
 * 解析成功后向请求注入 currentUserId 与 currentRole，供控制器做角色判断。
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {

    private static final String AUTH_HEADER = "Authorization";

    private static final String TOKEN_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;

    private final SysUserMapper sysUserMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public JwtInterceptor(JwtUtil jwtUtil, SysUserMapper sysUserMapper) {
        this.jwtUtil = jwtUtil;
        this.sysUserMapper = sysUserMapper;
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
            request.setAttribute("currentUserId", user.getId());
            request.setAttribute("currentRole", user.getRole());
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
