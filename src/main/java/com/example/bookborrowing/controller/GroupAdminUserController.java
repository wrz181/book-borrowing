package com.example.bookborrowing.controller;

import com.example.bookborrowing.annotation.RequirePermission;
import com.example.bookborrowing.common.PageResult;
import com.example.bookborrowing.common.Result;
import com.example.bookborrowing.dto.UserSaveDTO;
import com.example.bookborrowing.entity.SysUser;
import com.example.bookborrowing.service.SysUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 集团管理员 - 账号管理接口。
 * 需要 group:user 权限。
 */
@RestController
@RequestMapping("/api/admin/users")
@RequirePermission("group:user")
public class GroupAdminUserController {

    private final SysUserService sysUserService;

    public GroupAdminUserController(SysUserService sysUserService) {
        this.sysUserService = sysUserService;
    }

    /**
     * 分页查询账号。
     *
     * @param keyword  账号/昵称关键字
     * @param roleId   角色ID
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    @GetMapping("/page")
    public Result<PageResult<SysUser>> page(@RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) Long roleId,
                                            @RequestParam(defaultValue = "1") int pageNum,
                                            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(sysUserService.page(keyword, roleId, pageNum, pageSize));
    }

    /**
     * 新增账号（仅普通人员角色，防止越权创建管理员）。
     *
     * @param dto 账号参数
     * @return 空响应
     */
    @PostMapping
    public Result<Void> create(@RequestBody UserSaveDTO dto) {
        sysUserService.create(dto);
        return Result.success();
    }

    /**
     * 修改账号（昵称、角色，仅限普通人员账号）。
     *
     * @param id  用户ID
     * @param dto 账号参数
     * @return 空响应
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody UserSaveDTO dto) {
        dto.setId(id);
        sysUserService.update(dto);
        return Result.success();
    }

    /**
     * 删除账号（逻辑删除，不允许删除自己）。
     *
     * @param id      用户ID
     * @param request HTTP 请求
     * @return 空响应
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        Long currentUserId = (Long) request.getAttribute("currentUserId");
        if (id.equals(currentUserId)) {
            return Result.error("不能删除当前登录账号");
        }
        sysUserService.delete(id);
        return Result.success();
    }

    /**
     * 重置密码。
     *
     * @param id   用户ID
     * @param body 请求体（可含 password 字段，留空重置为 123456）
     * @return 空响应
     */
    @PutMapping("/{id}/password")
    public Result<Void> resetPassword(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        String password = body == null ? null : body.get("password");
        sysUserService.resetPassword(id, password);
        return Result.success();
    }
}
