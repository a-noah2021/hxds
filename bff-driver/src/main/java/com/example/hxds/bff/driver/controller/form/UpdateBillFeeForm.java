package com.example.hxds.bff.driver.controller.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

/**
 * @author zhanglumin
 * @since 2023/9/1 1:25 PM
 */
@Data
@Schema(description = "更新订单账单费用的表单")
public class UpdateBillFeeForm {

    @Schema(description = "总金额")
    private String total;

    @Schema(description = "里程费")
    private String mileageFee;

    @Schema(description = "等时费")
    private String waitingFee;

    @NotBlank(message = "tollFee不能为空")
    @Pattern(regexp = "^[1-9]\\d*\\.\\d{1,2}$|^0\\.\\d{1,2}$|^[1-9]\\d*$", message = "tollFee内容不正确")
    @Schema(description = "路桥费")
    private String tollFee;

    @NotBlank(message = "parkingFee不能为空")
    @Pattern(regexp = "^[1-9]\\d*\\.\\d{1,2}$|^0\\.\\d{1,2}$|^[1-9]\\d*$", message = "parkingFee内容不正确")
    @Schema(description = "路桥费")
    private String parkingFee;

    @NotBlank(message = "otherFee不能为空")
    @Pattern(regexp = "^[1-9]\\d*\\.\\d{1,2}$|^0\\.\\d{1,2}$|^[1-9]\\d*$", message = "otherFee内容不正确")
    @Schema(description = "其他费用")
    private String otherFee;

    @Schema(description = "返程费用")
    private String returnFee;

    @Schema(description = "系统奖励费用")
    private String incentiveFee;

    @NotNull(message = "orderId不能为空")
    @Min(value = 1, message = "orderId不能小于1")
    @Schema(description = "订单ID")
    private Long orderId;

    @Schema(description = "司机ID")
    private Long driverId;

    @Schema(description = "代驾公里数")
    private String realMileage;

    @Schema(description = "返程公里数")
    private String returnMileage;

    @Schema(description = "规则ID")
    private Long ruleId;

    @Schema(description = "支付手续费率")
    private String paymentRate;

    @Schema(description = "支付手续费")
    private String paymentFee;

    @Schema(description = "代缴个税费率")
    private String taxRate;

    @Schema(description = "代缴个税")
    private String taxFee;

    @Schema(description = "代驾系统分账收入")
    private String systemIncome;

    @Schema(description = "司机分账收入")
    private String driverIncome;

}
