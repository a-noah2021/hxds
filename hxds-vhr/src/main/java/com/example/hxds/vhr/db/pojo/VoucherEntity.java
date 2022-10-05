package com.example.hxds.vhr.db.pojo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;


@Data
public class VoucherEntity implements Serializable {
    /**
     * 主键
     */
    private Long id;

    /**
     * uuid
     */
    private String uuid;

    /**
     * 代金券标题
     */
    private String name;

    /**
     * 描述文字
     */
    private String remark;

    /**
     * 代金券标签，例如新人专用
     */
    private String tag;

    /**
     * 代金券数量，如果是0，则是无限量
     */
    private Integer totalQuota;

    /**
     * 实际领取数量
     */
    private Integer takeCount;

    /**
     * 已经使用的数量
     */
    private Integer usedCount;

    /**
     * 代金券面额
     */
    private BigDecimal discount;

    /**
     * 最少消费金额才能使用代金券
     */
    private BigDecimal withAmount;

    /**
     * 代金券赠送类型，如果是1则通用券，用户领取；如果是2，则是注册赠券
     */
    private Integer type;

    /**
     * 用户领券限制数量，如果是0，则是不限制；默认是1，限领一张
     */
    private Integer limitQuota;

    /**
     * 代金券状态，如果是1则是正常可用；如果是2则是过期; 如果是3则是下架
     */
    private Integer status;

    /**
     * 有效时间限制，如果是1，则基于领取时间的有效天数days；如果是2，则start_time和end_time是优惠券有效期；
     */
    private Integer timeType;

    /**
     * 基于领取时间的有效天数days
     */
    private Integer days;

    /**
     * 代金券开始时间
     */
    private String startTime;

    /**
     * 代金券结束时间
     */
    private String endTime;

    /**
     * 创建时间
     */
    private String createTime;

    private static final long serialVersionUID = 1L;
}