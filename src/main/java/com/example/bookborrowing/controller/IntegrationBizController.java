package com.example.bookborrowing.controller;

import com.example.bookborrowing.annotation.RequirePermission;
import com.example.bookborrowing.common.PageResult;
import com.example.bookborrowing.common.Result;
import com.example.bookborrowing.entity.IntegrationBiz;
import com.example.bookborrowing.service.IntegrationBizService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 集团管理员 - 集成业务配置（超级节点配置）接口。
 * 需要 group:app 权限（与集成应用配置同一权限点）。
 */
@RestController
@RequestMapping("/api/admin/biz")
@RequirePermission("group:app")
public class IntegrationBizController {

    private final IntegrationBizService integrationBizService;

    public IntegrationBizController(IntegrationBizService integrationBizService) {
        this.integrationBizService = integrationBizService;
    }

    /**
     * 分页查询集成业务配置：业务类型、业务名称模糊匹配，启用状态精确过滤。
     *
     * @param bizType  业务类型（可空）
     * @param bizName  业务名称（可空）
     * @param enabled  是否启用（可空）
     * @param pageNum  页码，默认1
     * @param pageSize 每页条数，默认10
     * @return 分页结果
     */
    @GetMapping("/page")
    public Result<PageResult<IntegrationBiz>> page(@RequestParam(required = false) String bizType,
                                                  @RequestParam(required = false) String bizName,
                                                  @RequestParam(required = false) Integer enabled,
                                                  @RequestParam(defaultValue = "1") int pageNum,
                                                  @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(integrationBizService.page(pageNum, pageSize, bizType, bizName, enabled));
    }

    /**
     * 业务配置详情。
     *
     * @param id 业务配置ID
     * @return 业务配置实体
     */
    @GetMapping("/{id}")
    public Result<IntegrationBiz> detail(@PathVariable Long id) {
        return Result.success(integrationBizService.getById(id));
    }

    /**
     * 新增业务配置：一个业务类型只能配置一个超级节点。
     *
     * @param biz 业务配置实体
     * @return 空响应
     */
    @PostMapping
    public Result<Void> create(@RequestBody IntegrationBiz biz) {
        integrationBizService.create(biz);
        return Result.success();
    }

    /**
     * 修改业务配置。
     *
     * @param id  业务配置ID
     * @param biz 业务配置实体
     * @return 空响应
     */
    @PostMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody IntegrationBiz biz) {
        integrationBizService.update(id, biz);
        return Result.success();
    }

    /**
     * 删除业务配置（逻辑删除）。
     *
     * @param id 业务配置ID
     * @return 空响应
     */
    @PostMapping("/{id}/delete")
    public Result<Void> delete(@PathVariable Long id) {
        integrationBizService.delete(id);
        return Result.success();
    }
}
