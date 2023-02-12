package com.example.hxds.odr.controller.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-02-12 21:24
 **/
@Data
@Schema(description = "更新订单状态的表单")
public class DeleteUnAcceptOrderForm {

    @NotNull(message = "orderId不能为空")
    @Min(value = 1,message = "orderId不能小于1")
    @Schema(description = "订单ID")
    private Long orderId;

    @Min(value = 0, message = "driverId不能小于0")
    @Schema(description = "司机ID")
    private Long driverId;

    @Min(value = 0, message = "customerId不能小于0")
    @Schema(description = "乘客ID")
    private Long customerId;

}
