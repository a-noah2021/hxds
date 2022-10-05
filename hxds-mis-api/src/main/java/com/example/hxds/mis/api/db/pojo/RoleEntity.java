package com.example.hxds.mis.api.db.pojo;

import lombok.Data;

import java.io.Serializable;

/**
 * 角色表
 * @TableName tb_role
 */
@Data
public class RoleEntity implements Serializable {
    /**
     * 主键
     */
    private Integer id;

    /**
     * 角色名称
     */
    private String roleName;

    /**
     * 权限集合
     */
    private String permissions;

    /**
     * 描述
     */
    private String desc;

    /**
     * 系统角色内置权限
     */
    private String defaultPermissions;

    /**
     * 是否为系统内置角色
     */
    private Integer systemic;

    private static final long serialVersionUID = 1L;
}