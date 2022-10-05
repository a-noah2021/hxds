package com.example.hxds.odr.db.pojo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 订单违规表
 * @TableName tb_order_violation
 */
@Data
public class OrderViolationEntity implements Serializable {
    /**
     * 主键
     */
    private Long id;

    /**
     * 订单ID
     */
    private Long orderId;

    /**
     * 司机ID
     */
    private Long driverId;

    /**
     * 违纪类型，1服务，2驾驶
     */
    private Byte type;

    /**
     * 违纪原因
     */
    private String reason;

    /**
     * 状态，1未申诉，2已申诉，3申诉失败，4申诉成功
     */
    private Byte status;

    /**
     * 申诉工作流实例ID
     */
    private String instanceId;

    /**
     * 创建时间
     */
    private Date createTime;

    private static final long serialVersionUID = 1L;
}