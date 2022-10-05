package com.example.hxds.odr.db.pojo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 订单评价表
 * @TableName tb_order_comment
 */
@Data
public class OrderCommentEntity implements Serializable {
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
     * 顾客ID
     */
    private Long customerId;

    /**
     * 评分，1星~5星
     */
    private Byte rate;

    /**
     * 差评备注
     */
    private String remark;

    /**
     * 状态，1未申诉，2已申诉，3申诉失败，4申诉成功
     */
    private Byte status;

    /**
     * 申诉工作流ID
     */
    private String instanceId;

    /**
     * 创建时间
     */
    private Date createTime;

    private static final long serialVersionUID = 1L;
}