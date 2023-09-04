package com.example.hxds.bff.customer.controller.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * @author zhanglumin
 * @since 2023/9/4 1:50 PM
 */
@Data
@Schema(description = "根据订单号查询评价的表单")
public class SearchCommentByOrderIdForm {

    @NotNull(message = "orderId不能为空")
    @Min(value = 1, message = "orderId不能小于1")
    @Schema(description = "订单ID")
    private Long orderId;

    @Schema(description = "乘客ID")
    private Long customerId;
}
