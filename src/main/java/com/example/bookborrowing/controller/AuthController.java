package com.example.bookborrowing.controller;

import com.example.bookborrowing.common.BusinessException;
import com.example.bookborrowing.common.Result;
import com.example.bookborrowing.dto.LoginDTO;
import com.example.bookborrowing.entity.SysUser;
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

import java.util.HashMap;
import java.util.Map;

/**
 * 登录认证接口：JWT 签发、当前用户信息查询。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final SysUserService sysUserService;

    private final JwtUtil jwtUtil;

    public AuthController(SysUserService sysUserService, JwtUtil jwtUtil) {
        this.sysUserService = sysUserService;
        this.jwtUtil = jwtUtil;
    }

    /**
     * 登录：校验账号密码，签发 JWT token。
     *
     * @param loginDTO 登录参数
     * @return token 与用户信息
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
        log.info("用户登录成功：账号={}，用户ID={}，角色={}", user.getUsername(), user.getId(), user.getRole());

        Map<String, Object> data = new HashMap<>(4);
        data.put("token", token);
        data.put("userInfo", buildUserInfo(user));
        return Result.success(data);
    }

    /**
     * 查询当前登录用户信息。
     *
     * @param request HTTP 请求（拦截器已注入 currentUserId）
     * @return 用户信息（不含密码）
     */
    @GetMapping("/info")
    public Result<Map<String, Object>> info(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        SysUser user = sysUserService.getById(userId);
        if (user == null) {
            log.warn("查询当前用户信息失败：登录状态无效，userId={}", userId);
            return Result.error(401, "登录状态无效，请重新登录");
        }
        return Result.success(buildUserInfo(user));
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
        userInfo.put("role", user.getRole());
        return userInfo;
    }
}
