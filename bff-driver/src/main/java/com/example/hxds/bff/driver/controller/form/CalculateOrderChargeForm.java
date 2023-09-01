package com.example.hxds.bff.driver.controller.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @author zhanglumin
 * @since 2023/9/1 1:14 PM
 */
@Data
@Schema(description = "计算代驾费用的表单")
public class CalculateOrderChargeForm {

    @Schema(description = "代驾公里数")
    private String mileage;

    @Schema(description = "代驾开始时间")
    private String time;

    @Schema(description = "等时分钟")
    private Integer minute;
}
