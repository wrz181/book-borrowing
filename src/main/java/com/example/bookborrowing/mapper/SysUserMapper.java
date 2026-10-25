package com.example.bookborrowing.mapper;

import com.example.bookborrowing.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统用户 Mapper：登录与按ID查询。
 */
@Mapper
public interface SysUserMapper {

    /**
     * 按登录账号查询用户。
     *
     * @param username 登录账号
     * @return 用户实体，不存在返回 null
     */
    SysUser selectByUsername(String username);

    /**
     * 按ID查询用户。
     *
     * @param id 用户ID
     * @return 用户实体，不存在返回 null
     */
    SysUser selectById(Long id);
}
