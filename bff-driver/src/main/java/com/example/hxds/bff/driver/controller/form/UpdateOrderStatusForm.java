package com.example.hxds.bff.driver.controller.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.hibernate.validator.constraints.Range;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-02-19 19:43
 **/
@Data
@Schema(description = "更新订单状态的表单")
public class UpdateOrderStatusForm {
    @NotNull(message = "orderId不能为空")
    @Min(value = 1, message = "orderId不能小于1")
    @Schema(description = "订单ID")
    private Long orderId;

    @NotNull(message = "status不能为空")
    @Range(min = 1, max = 12, message = "status内容不正确")
    @Schema(description = "订单状态")
    private Byte status;

    @NotNull(message = "customerId不能为空")
    @Min(value = 1, message = "customerId不能小于1")
    @Schema(description = "客户ID")
    private Long customerId;
}
