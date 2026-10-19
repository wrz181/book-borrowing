package com.example.bookborrowing.mapper;

import com.example.bookborrowing.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户角色关联 Mapper（账号绑多角色，支撑三员身份切换）。
 */
@Mapper
public interface SysUserRoleMapper {

    /**
     * 查询用户拥有的全部角色ID。
     *
     * @param userId 用户ID
     * @return 角色ID列表
     */
    List<Long> selectRoleIdsByUserId(Long userId);

    /**
     * 查询用户拥有的全部角色（含编码/名称）。
     *
     * @param userId 用户ID
     * @return 角色列表
     */
    List<SysRole> selectRolesByUserId(Long userId);

    /**
     * 判断用户是否拥有指定角色。
     *
     * @param userId 用户ID
     * @param roleId 角色ID
     * @return 拥有返回 1，否则 0
     */
    int existsUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);

    /**
     * 删除用户全部角色关联。
     *
     * @param userId 用户ID
     * @return 影响行数
     */
    int deleteByUserId(Long userId);

    /**
     * 批量写入用户角色关联（幂等）。
     *
     * @param userId  用户ID
     * @param roleIds 角色ID集合（非空）
     * @return 影响行数
     */
    int insertBatch(@Param("userId") Long userId, @Param("roleIds") List<Long> roleIds);
}
