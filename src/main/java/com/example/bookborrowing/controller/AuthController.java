package com.example.bookborrowing.controller;

import com.example.bookborrowing.common.BusinessException;
import com.example.bookborrowing.common.Result;
import com.example.bookborrowing.dto.LoginDTO;
import com.example.bookborrowing.entity.SysUser;
import com.example.bookborrowing.service.SysUserService;
import com.example.bookborrowing.service.support.RedisSupportService;
import com.example.bookborrowing.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 登录认证接口：JWT 签发、当前用户信息查询、退出登录。
 * <p>
 * 云计算扩展：登录成功后把 token 写入 Redis 白名单（TTL 与 JWT 有效期一致），
 * 拦截器除校验 JWT 签名外还校验白名单，实现服务端可控的会话吊销（分布式会话）；
 * Redis 不可用时自动退回纯 JWT 校验，登录不受影响。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private static final String TOKEN_PREFIX = "Bearer ";

    private final SysUserService sysUserService;

    private final JwtUtil jwtUtil;

    private final RedisSupportService redisSupport;

    /** JWT 有效期（小时），与 Redis 白名单 TTL 保持一致 */
    @Value("${jwt.expire-hours:24}")
    private long expireHours;

    public AuthController(SysUserService sysUserService, JwtUtil jwtUtil, RedisSupportService redisSupport) {
        this.sysUserService = sysUserService;
        this.jwtUtil = jwtUtil;
        this.redisSupport = redisSupport;
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
        // token 写入 Redis 白名单：支持登出吊销与活跃续期；Redis 不可用时该步自动跳过
        redisSupport.saveToken(token, user.getId(), Duration.ofHours(expireHours));
        log.info("用户登录成功：账号={}，用户ID={}，角色={}", user.getUsername(), user.getId(), user.getRole());

        Map<String, Object> data = new HashMap<>(4);
        data.put("token", token);
        data.put("userInfo", buildUserInfo(user));
        return Result.success(data);
    }

    /**
     * 退出登录：从 Redis 白名单删除 token，实现服务端会话吊销。
     *
     * @param request HTTP 请求（携带 Authorization 头）
     * @return 空响应
     */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith(TOKEN_PREFIX)) {
            redisSupport.removeToken(auth.substring(TOKEN_PREFIX.length()));
        }
        log.info("用户退出登录成功");
        return Result.success();
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
