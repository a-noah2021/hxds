package com.example.hxds.odr.db.pojo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 订单监控表
 * @TableName tb_order_monitoring
 */
@Data
public class OrderMonitoringEntity implements Serializable {
    /**
     * 主键
     */
    private Long id;

    /**
     * 订单ID
     */
    private Long orderId;

    /**
     * 录音文档云存储网址
     */
    private String recording;

    /**
     * 谈话文字内容
     */
    private String content;

    /**
     * 谈话内容的标签，比如辱骂、挑逗、开房、包养等
     */
    private String tag;

    /**
     * 创建时间
     */
    private Date createTime;

    private static final long serialVersionUID = 1L;
}