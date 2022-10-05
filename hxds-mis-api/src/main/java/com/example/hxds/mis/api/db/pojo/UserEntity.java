package com.example.hxds.mis.api.db.pojo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 管理系统用户表
 * @TableName tb_user
 */
@Data
public class UserEntity implements Serializable {
    /**
     * 主键
     */
    private Long id;

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码（AES加密）
     */
    private String password;

    /**
     * 姓名
     */
    private String name;


    /**
     * 性别
     */
    private String sex;

    /**
     * 电话
     */
    private String tel;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 角色
     */
    private String role;

    /**
     * 是否为超级管理员
     */
    private Integer root;

    /**
     * 部门编号
     */
    private Integer deptId;

    /**
     * 1有效，2离职，3禁用
     */
    private Byte status;

    /**
     * 创建日期
     */
    private Date createTime;

    private static final long serialVersionUID = 1L;
}