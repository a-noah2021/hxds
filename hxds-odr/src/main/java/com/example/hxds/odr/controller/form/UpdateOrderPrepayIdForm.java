package com.example.hxds.odr.controller.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-09-02 16:11
 **/
@Data
@Schema(description = "更新预支付订单ID的表单")
public class UpdateOrderPrepayIdForm {
    @NotBlank(message = "prepayId不能为空")
    @Schema(description = "预支付订单ID")
    private String prepayId;

    @NotNull(message = "orderId不能为空")
    @Min(value = 1, message = "orderId不能小于1")
    @Schema(description = "订单ID")
    private Long orderId;
}
