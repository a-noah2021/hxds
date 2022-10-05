package com.example.hxds.mis.api.db.pojo;

import lombok.Data;

import java.io.Serializable;

/**
 * 部门表
 * @TableName tb_dept
 */
@Data
public class DeptEntity implements Serializable {
    /**
     * 主键
     */
    private Integer id;

    /**
     * 部门名称
     */
    private String deptName;

    /**
     * 部门电话
     */
    private String tel;

    /**
     * 部门邮箱
     */
    private String email;

    /**
     * 备注
     */
    private String desc;

    private static final long serialVersionUID = 1L;
}