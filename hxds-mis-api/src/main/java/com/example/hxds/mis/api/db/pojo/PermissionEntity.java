package com.example.hxds.mis.api.db.pojo;

import lombok.Data;

import java.io.Serializable;

/**
 * 权限表
 * @TableName tb_permission
 */
@Data
public class PermissionEntity implements Serializable {
    /**
     * 主键
     */
    private Integer id;

    /**
     * 权限
     */
    private String permissionName;

    /**
     * 模块ID
     */
    private Integer moduleId;

    /**
     * 行为ID
     */
    private Integer actionId;

    private static final long serialVersionUID = 1L;
}