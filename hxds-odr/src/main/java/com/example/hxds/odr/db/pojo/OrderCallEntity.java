package com.example.hxds.odr.db.pojo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 订单通话记录表
 * @TableName tb_order_call
 */
@Data
public class OrderCallEntity implements Serializable {
    /**
     * 主键
     */
    private Long id;

    /**
     * 订单ID
     */
    private Long orderId;

    /**
     * 通话分钟数
     */
    private Short minute;

    /**
     * 开始时间
     */
    private Date startTime;

    /**
     * 结束时间
     */
    private Date endTime;

    /**
     * 通话费
     */
    private BigDecimal fee;

    /**
     * 日期
     */
    private String date;

    /**
     * 创建时间
     */
    private Date createTime;

    private static final long serialVersionUID = 1L;
}