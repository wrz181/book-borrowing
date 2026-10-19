package com.example.bookborrowing.dto;

import lombok.Data;

import java.util.List;

/**
 * 角色权限保存参数。
 */
@Data
public class RolePermSaveDTO {

    /** 勾选的权限编码集合 */
    private List<String> permCodes;
}
