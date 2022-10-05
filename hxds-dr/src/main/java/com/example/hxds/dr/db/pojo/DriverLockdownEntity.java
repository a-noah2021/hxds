package com.example.hxds.dr.db.pojo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class DriverLockdownEntity implements Serializable {
    /**
     * 主键
     */
    private Long id;

    /**
     * 司机ID
     */
    private Long driverId;

    /**
     * 原因
     */
    private String reason;

    /**
     * 订单ID
     */
    private Long orderId;

    /**
     * 起始日期
     */
    private Date startDate;

    /**
     * 结束日期
     */
    private Date endDate;

    /**
     * 天数
     */
    private Integer days;

    private static final long serialVersionUID = 1L;
}