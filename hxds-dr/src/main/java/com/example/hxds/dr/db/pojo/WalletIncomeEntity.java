package com.example.hxds.dr.db.pojo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class WalletIncomeEntity implements Serializable {
    /**
     * 主键
     */
    private Long id;

    /**
     * uuid字符串
     */
    private String uuid;
    /**
     * 司机ID
     */
    private Long driverId;

    /**
     * 金额
     */
    private BigDecimal amount;

    /**
     * 1充值，2奖励，3补贴
     */
    private Byte type;

    /**
     * 预支付订单ID
     */
    private String prepayId;

    /**
     * 1未支付，2已支付，3已到账
     */
    private Byte status;

    /**
     * 备注信息
     */
    private String remark;

    /**
     * 创建时间
     */
    private String createTime;

    private static final long serialVersionUID = 1L;

}