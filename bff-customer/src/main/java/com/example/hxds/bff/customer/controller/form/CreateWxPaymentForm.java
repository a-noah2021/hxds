package com.example.hxds.bff.customer.controller.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-09-02 23:25
 **/
@Data
@Schema(description = "创建支付订单")
public class CreateWxPaymentForm {
    @NotNull(message = "orderId不为空")
    @Min(value = 1, message = "orderId不能小于1")
    @Schema(description = "订单ID")
    private Long orderId;

    @Schema(description = "客户ID")
    private Long customerId;


    @Min(value = 1, message = "customerVoucherId不能小于1")
    @Schema(description = "领取的代金券ID")
    private Long customerVoucherId;

    @Min(value = 1, message = "customerVoucherId不能小于1")
    @Schema(description = "代金券ID")
    private Long voucherId;
}
