package com.example.bookborrowing.controller;

import com.example.bookborrowing.annotation.RequirePermission;
import com.example.bookborrowing.common.Result;
import com.example.bookborrowing.entity.IntegrationApp;
import com.example.bookborrowing.service.IntegrationAppService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 单点登录接口：集成应用栏目（已启用应用）与 ticket 生成。
 * 应用集成的配置与进入均属集团管理员职责，因此领票受 `group:app` 权限约束；
 * 普通人员与系统管理员均不具备该权限，无法向外部平台发送表单。
 */
@RestController
@RequestMapping("/api/sso")
public class SsoController {

    private final IntegrationAppService integrationAppService;

    public SsoController(IntegrationAppService integrationAppService) {
        this.integrationAppService = integrationAppService;
    }

    /**
     * 查询已启用的应用（前台动态栏目），不返回密钥。
     *
     * @return 应用列表
     */
    @GetMapping("/apps")
    public Result<List<IntegrationApp>> apps() {
        List<IntegrationApp> apps = integrationAppService.listEnabled();
        apps.forEach(a -> a.setSsoSecret(null));
        return Result.success(apps);
    }

    /**
     * 生成跳转到指定应用的单点登录 ticket。
     * 仅「应用集成配置」权限持有者（集团管理员）可领票，普通人员不得向外部平台发送表单。
     *
     * @param appId   应用ID
     * @param request HTTP 请求
     * @return ticket
     */
    @GetMapping("/ticket")
    @RequirePermission("group:app")
    public Result<String> ticket(@RequestParam Long appId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return Result.success(integrationAppService.generateTicket(appId, userId));
    }
}
