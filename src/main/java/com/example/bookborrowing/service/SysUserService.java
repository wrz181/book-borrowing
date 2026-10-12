package com.example.bookborrowing.service;

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
}
