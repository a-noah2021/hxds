package com.example.hxds.odr.db.pojo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 司机申诉表
 * @TableName tb_order_appeal
 */
@Data
public class OrderAppealEntity implements Serializable {
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
     * 申诉内容
     */
    private String detail;

    /**
     * 处理结果
     */
    private String result;

    /**
     * 申诉状态，1申诉中，2申诉成功，3申诉失败
     */
    private Byte status;

    /**
     * 创建时间
     */
    private Date createTime;

    private static final long serialVersionUID = 1L;
}