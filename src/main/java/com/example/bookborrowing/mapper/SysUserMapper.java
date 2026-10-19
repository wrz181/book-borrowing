package com.example.bookborrowing.mapper;

import com.example.bookborrowing.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 系统用户 Mapper。
 */
@Mapper
public interface SysUserMapper {

    /**
     * 按登录账号查询用户（含角色编码/名称）。
     *
     * @param username 登录账号
     * @return 用户实体，不存在返回 null
     */
    SysUser selectByUsername(String username);

    /**
     * 按ID查询用户（含角色编码/名称）。
     *
     * @param id 用户ID
     * @return 用户实体，不存在返回 null
     */
    SysUser selectById(Long id);

    /**
     * 分页查询账号。
     *
     * @param offset  偏移量
     * @param limit   每页条数
     * @param keyword 账号/昵称关键字（可空）
     * @param roleId  角色ID（可空）
     * @return 账号列表
     */
    List<SysUser> selectPage(@Param("offset") int offset,
                             @Param("limit") int limit,
                             @Param("keyword") String keyword,
                             @Param("roleId") Long roleId);

    /**
     * 统计账号总数。
     *
     * @param keyword 账号/昵称关键字（可空）
     * @param roleId  角色ID（可空）
     * @return 总记录数
     */
    long selectCount(@Param("keyword") String keyword, @Param("roleId") Long roleId);

    /**
     * 统计同账号的用户数（用于重名校验）。
     *
     * @param username  登录账号
     * @param excludeId 需排除的用户ID（新增时传 null）
     * @return 数量
     */
    int countByUsername(@Param("username") String username, @Param("excludeId") Long excludeId);

    /**
     * 新增用户。
     *
     * @param user 用户实体
     * @return 影响行数
     */
    int insert(SysUser user);

    /**
     * 更新用户基本信息（昵称、角色）。
     *
     * @param user 用户实体
     * @return 影响行数
     */
    int updateById(SysUser user);

    /**
     * 重置密码。
     *
     * @param id       用户ID
     * @param password BCrypt 加密后的密码
     * @return 影响行数
     */
    int updatePassword(@Param("id") Long id, @Param("password") String password);

    /**
     * 按ID逻辑删除用户。
     *
     * @param id 用户ID
     * @return 影响行数
     */
    int deleteById(Long id);
}
