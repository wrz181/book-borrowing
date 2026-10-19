package com.example.bookborrowing.controller;

import com.example.bookborrowing.annotation.RequirePermission;
import com.example.bookborrowing.common.Result;
import com.example.bookborrowing.entity.IntegrationApp;
import com.example.bookborrowing.service.IntegrationAppService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 集团管理员 - 单点登录应用集成配置接口。
 * 需要 group:app 权限。
 */
@RestController
@RequestMapping("/api/admin/apps")
@RequirePermission("group:app")
public class IntegrationAppController {

    private final IntegrationAppService integrationAppService;

    public IntegrationAppController(IntegrationAppService integrationAppService) {
        this.integrationAppService = integrationAppService;
    }

    /**
     * 查询全部应用。
     *
     * @return 应用列表
     */
    @GetMapping
    public Result<List<IntegrationApp>> list() {
        return Result.success(integrationAppService.listAll());
    }

    /**
     * 新增应用。配置后前台将出现对应栏目。
     *
     * @param app 应用实体
     * @return 空响应
     */
    @PostMapping
    public Result<Void> create(@RequestBody IntegrationApp app) {
        integrationAppService.create(app);
        return Result.success();
    }

    /**
     * 修改应用。
     *
     * @param id  应用ID
     * @param app 应用实体
     * @return 空响应
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody IntegrationApp app) {
        app.setId(id);
        integrationAppService.update(app);
        return Result.success();
    }

    /**
     * 删除应用（逻辑删除）。
     *
     * @param id 应用ID
     * @return 空响应
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        integrationAppService.delete(id);
        return Result.success();
    }
}
