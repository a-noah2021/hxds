package com.example.hxds.bff.customer.controller.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-09-02 22:42
 **/
@Data
@Schema(description = "更新支付订单ID的表单")
public class UpdateOrderPrepayIdForm {

    @Schema(description = "预支付订单ID")
    private String prepayId;

    @Schema(description = "订单ID")
    private Long orderId;
}
