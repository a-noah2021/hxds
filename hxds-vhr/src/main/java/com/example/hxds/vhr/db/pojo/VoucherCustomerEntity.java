package com.example.hxds.vhr.db.pojo;

import lombok.Data;

import java.io.Serializable;

/**
 * 代金券领取表
 *
 * @TableName tb_voucher_customer
 */
@Data
public class VoucherCustomerEntity implements Serializable {
    /**
     * 主键
     */
    private Long id;

    /**
     * 客户ID
     */
    private Long customerId;

    /**
     * 代金券ID
     */
    private Long voucherId;

    /**
     * 使用状态, 如果是1则未使用；如果是2则已使用；如果是3则已过期；如果是4则已经下架；
     */
    private Integer status;

    /**
     * 使用代金券的时间
     */
    private String usedTime;

    /**
     * 有效期开始时间
     */
    private String startTime;

    /**
     * 有效期截至时间
     */
    private String endTime;

    /**
     * 订单ID
     */
    private Long orderId;

    /**
     * 创建时间
     */
    private String createTime;

    private static final long serialVersionUID = 1L;
}