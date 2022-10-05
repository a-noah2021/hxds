package com.example.hxds.dr.db.pojo;

import lombok.Data;

import java.io.Serializable;

@Data
public class DriverEntity implements Serializable {
    /**
     * 主键
     */
    private Long id;

    /**
     * 小程序长期授权
     */
    private String openId;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 性别
     */
    private String sex;

    /**
     * 姓名
     */
    private String name;

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
     * 1未认证，2已认证
     */
    private Byte realAuth;

    /**
     * 身份证编号
     */
    private String identityLicence;

    /**
     * 身份证图片云存储网址
     */
    private String identityLicenceImg;

    /**
     * 驾驶证编号
     */
    private String drivingLicence;

    /**
     * 驾驶证图片云存储网址
     */
    private String drivingLicenceImg;

    /**
     * 摘要信息，level等级，totalOrder接单数，weekOrder周接单，weekComment周好评，appeal正在申诉量
     */
    private String summary;

    /**
     * 是否在腾讯云归档存放司机面部信息
     */
    private Boolean archive;

    /**
     * 状态，1正常，2禁用，3.降低接单量
     */
    private Byte status;

    /**
     * 注册时间
     */
    private String createTime;

    private static final long serialVersionUID = 1L;
}