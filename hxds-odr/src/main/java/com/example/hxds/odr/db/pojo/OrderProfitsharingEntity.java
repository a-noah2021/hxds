package com.example.hxds.odr.db.pojo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 订单分账表
 *
 * @TableName tb_order_profitsharing
 */
@Data
public class OrderProfitsharingEntity implements Serializable {
    /**
     * 主键
     */
    private Long id;

    /**
     * 订单ID
     */
    private Long orderId;

    /**
     * 规则ID
     */
    private Long ruleId;

    /**
     * 总费用
     */
    private BigDecimal amountFee;

    /**
     * 微信支付渠道费率
     */
    private BigDecimal paymentRate;

    /**
     * 微信支付渠道费
     */
    private BigDecimal paymentFee;

    /**
     * 为代驾司机代缴税率
     */
    private BigDecimal taxRate;

    /**
     * 税率支出
     */
    private BigDecimal taxFee;

    /**
     * 企业分账收入
     */
    private BigDecimal systemIncome;

    /**
     * 司机分账收入
     */
    private BigDecimal driverIncome;

    /**
     * 分账状态，1未分账，2已分账
     */
    private Byte status;

    private static final long serialVersionUID = 1L;
}