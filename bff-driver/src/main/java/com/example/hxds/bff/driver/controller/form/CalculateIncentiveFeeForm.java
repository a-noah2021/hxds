package com.example.hxds.bff.driver.controller.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

/**
 * @author zhanglumin
 * @since 2023/9/1 1:18 PM
 */
@Data
@Schema(description = "计算系统奖励的表单")
public class CalculateIncentiveFeeForm {

    @Schema(description = "司机ID")
    private long driverId;

    @NotBlank(message = "acceptTime不能为空")
    @Pattern(regexp = "^((((1[6-9]|[2-9]\\d)\\d{2})-(0?[13578]|1[02])-(0?[1-9]|[12]\\d|3[01]))|(((1[6-9]|[2-9]\\d)\\d{2})-(0?[13456789]|1[012])-(0?[1-9]|[12]\\d|30))|(((1[6-9]|[2-9]\\d)\\d{2})-0?2-(0?[1-9]|1\\d|2[0-8]))|(((1[6-9]|[2-9]\\d)(0[48]|[2468][048]|[13579][26])|((16|[2468][048]|[3579][26])00))-0?2-29-))\\s(20|21|22|23|[0-1]\\d):[0-5]\\d:[0-5]\\d$",
            message = "acceptTime内容不正确")
    @Schema(description = "接单时间")
    private String acceptTime;
}
