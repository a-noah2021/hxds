package com.example.hxds.bff.customer.controller.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
@Schema(description = "查询订单信息用于司乘同显功能的表单")
public class SearchOrderForMoveByIdForm {

    @NotNull(message = "orderId不能为空")
    @Schema(description = "订单ID")
    private Long orderId;

    @Schema(description = "乘客ID")
    private Long customerId;

}
