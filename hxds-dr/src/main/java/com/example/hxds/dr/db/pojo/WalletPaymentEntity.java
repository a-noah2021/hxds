package com.example.hxds.dr.db.pojo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class WalletPaymentEntity implements Serializable {
    /**
     * 主键
     */
    private Long id;

    /**
     * 司机ID
     */
    private Long driverId;

    /**
     * 支付金额
     */
    private BigDecimal amount;

    /**
     * 1话费，2罚款，3抽奖，4缴费，5其他
     */
    private Byte type;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private String createTime;

    private static final long serialVersionUID = 1L;


}