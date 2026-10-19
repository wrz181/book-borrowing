package com.example.bookborrowing.mapper;

import com.example.bookborrowing.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 角色 Mapper。
 */
@Mapper
public interface SysRoleMapper {

    /**
     * 查询全部角色。
     *
     * @return 角色列表
     */
    List<SysRole> selectAll();

    /**
     * 按ID查询角色。
     *
     * @param id 角色ID
     * @return 角色实体，不存在返回 null
     */
    SysRole selectById(Long id);

    /**
     * 清除指定角色的全部权限。
     *
     * @param roleId 角色ID
     * @return 影响行数
     */
    int deleteRolePermissions(Long roleId);

    /**
     * 为角色批量新增权限关联。
     *
     * @param roleId    角色ID
     * @param permCodes 权限编码集合（非空）
     * @return 影响行数
     */
    int insertRolePermissions(@Param("roleId") Long roleId,
                              @Param("permCodes") List<String> permCodes);
}
