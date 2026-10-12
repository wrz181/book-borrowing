package com.example.bookborrowing.controller;

import com.example.bookborrowing.common.Result;
import com.example.bookborrowing.dto.LoginDTO;
import com.example.bookborrowing.entity.SysUser;
import com.example.bookborrowing.service.SysUserService;
import com.example.bookborrowing.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 登录认证接口：JWT 签发与当前用户信息查询。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

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
     * @return token 与用户基本信息
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginDTO loginDTO) {
        SysUser user = sysUserService.login(loginDTO.getUsername(), loginDTO.getPassword());
        String token = jwtUtil.generate(user.getId(), user.getUsername());

        Map<String, Object> data = new HashMap<>(4);
        data.put("token", token);
        Map<String, Object> userInfo = new HashMap<>(4);
        userInfo.put("id", user.getId());
        userInfo.put("username", user.getUsername());
        userInfo.put("nickname", user.getNickname());
        data.put("userInfo", userInfo);
        return Result.success(data);
    }

    /**
     * 查询当前登录用户信息。
     *
     * @param request HTTP 请求（拦截器已注入 currentUserId）
     * @return 用户基本信息
     */
    @GetMapping("/info")
    public Result<Map<String, Object>> info(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        SysUser user = sysUserService.getById(userId);
        if (user == null) {
            return Result.error(401, "登录状态无效，请重新登录");
        }
        Map<String, Object> userInfo = new HashMap<>(4);
        userInfo.put("id", user.getId());
        userInfo.put("username", user.getUsername());
        userInfo.put("nickname", user.getNickname());
        return Result.success(userInfo);
    }
}
