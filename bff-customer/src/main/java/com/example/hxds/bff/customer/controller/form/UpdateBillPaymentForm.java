package com.example.hxds.bff.customer.controller.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-09-02 22:45
 **/
@Data
@Schema(description = "更新账单实际支付费用的表单")
public class UpdateBillPaymentForm {

    @Schema(description = "实际支付金额")
    private String realPay;

    @Schema(description = "代金券面额")
    private String voucherFee;

    @Schema(description = "订单ID")
    private Long orderId;
}

