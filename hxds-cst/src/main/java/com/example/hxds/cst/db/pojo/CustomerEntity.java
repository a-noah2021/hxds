package com.example.hxds.cst.db.pojo;

import lombok.Data;

import java.io.Serializable;

@Data
public class CustomerEntity implements Serializable {
    /**
     * 主键
     */
    private Long id;

    /**
     * 小程序授权字符串
     */
    private String openId;

    /**
     * 客户昵称
     */
    private String nickname;

    /**
     * 性别
     */
    private String sex;

    /**
     * 头像
     */
    private String photo;

    /**
     * 电话
     */
    private String tel;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 1有效，2禁用
     */
    private Integer status;

    /**
     * 注册时间
     */
    private String createTime;

    private static final long serialVersionUID = 1L;
}