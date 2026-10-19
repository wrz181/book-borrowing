package com.example.bookborrowing.service;

import com.example.bookborrowing.common.PageResult;
import com.example.bookborrowing.dto.UserSaveDTO;
import com.example.bookborrowing.entity.SysUser;

/**
 * 系统用户业务接口。
 */
public interface SysUserService {

    /**
     * 登录校验。
     *
     * @param username 登录账号
     * @param password 明文密码
     * @return 登录成功的用户
     */
    SysUser login(String username, String password);

    /**
     * 按ID查询用户。
     *
     * @param id 用户ID
     * @return 用户实体
     */
    SysUser getById(Long id);

    /**
     * 分页查询账号。
     *
     * @param keyword  账号/昵称关键字（可空）
     * @param roleId   角色ID（可空）
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    PageResult<SysUser> page(String keyword, Long roleId, int pageNum, int pageSize);

    /**
     * 新增账号。
     *
     * @param dto 账号参数
     */
    void create(UserSaveDTO dto);

    /**
     * 修改账号（昵称、角色）。
     *
     * @param dto 账号参数
     */
    void update(UserSaveDTO dto);

    /**
     * 删除账号（逻辑删除）。
     *
     * @param id 用户ID
     */
    void delete(Long id);

    /**
     * 重置密码。
     *
     * @param id          用户ID
     * @param newPassword 新密码（明文，留空则重置为 123456）
     */
    void resetPassword(Long id, String newPassword);
}
