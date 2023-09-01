package com.example.hxds.bff.driver.controller.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * @author zhanglumin
 * @since 2023/9/1 1:03 PM
 */
@Data
@Schema(description = "查询司机是否关联某订单的表单")
public class ValidDriverOwnOrderForm {

    @Schema(description = "司机ID")
    private Long driverId;

    @NotNull(message = "orderId不能为空")
    @Min(value = 1, message = "orderId不能小于1")
    @Schema(description = "订单ID")
    private Long orderId;
}
