package com.example.hxds.bff.driver.controller.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @author zhanglumin
 * @since 2023/9/1 1:18 PM
 */
@Data
@Schema(description = "计算订单分账的表单")
public class CalculateProfitsharingForm {

    @Schema(description = "订单ID")
    private Long orderId;

    @Schema(description = "待分账费用")
    private String amount;
}
