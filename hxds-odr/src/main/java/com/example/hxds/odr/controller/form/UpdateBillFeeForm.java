package com.example.hxds.odr.controller.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

/**
 * @author zhanglumin
 * @since 2023/8/24 7:52 PM
 */
@Data
@Schema(description = "更新账单的表单")
public class UpdateBillFeeForm {

    @NotBlank(message = "total不能为空")
    @Pattern(regexp = "^[1-9]\\d*\\.\\d{1,2}$|^0\\.\\d{1,2}$|^[1-9]\\d*$", message = "total内容不正确")
    @Schema(description = "总金额")
    private String total;

    @NotBlank(message = "mileageFee不能为空")
    @Pattern(regexp = "^[1-9]\\d*\\.\\d{1,2}$|^0\\.\\d{1,2}$|^[1-9]\\d*$", message = "mileageFee内容不正确")
    @Schema(description = "里程费")
    private String mileageFee;

    @NotBlank(message = "waitingFee不能为空")
    @Pattern(regexp = "^[1-9]\\d*\\.\\d{1,2}$|^0\\.\\d{1,2}$|^[1-9]\\d*$", message = "waitingFee内容不正确")
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

    @NotBlank(message = "returnFee不能为空")
    @Pattern(regexp = "^[1-9]\\d*\\.\\d{1,2}$|^0\\.\\d{1,2}$|^[1-9]\\d*$", message = "returnFee内容不正确")
    @Schema(description = "返程费用")
    private String returnFee;

    @NotBlank(message = "incentiveFee不能为空")
    @Pattern(regexp = "^[1-9]\\d*\\.\\d{1,2}$|^0\\.\\d{1,2}$|^[1-9]\\d*$", message = "incentiveFee内容不正确")
    @Schema(description = "系统奖励费用")
    private String incentiveFee;

    @NotNull(message = "orderId不能为空")
    @Min(value = 1, message = "orderId不能小于1")
    @Schema(description = "订单ID")
    private Long orderId;

    @NotBlank(message = "realMileage不能为空")
    @Pattern(regexp = "^[1-9]\\d*\\.\\d+$|^0\\.\\d+$|^[1-9]\\d*$", message = "realMileage内容不正确")
    @Schema(description = "代驾公里数")
    private String realMileage;

    @NotBlank(message = "returnMileage不能为空")
    @Pattern(regexp = "^[1-9]\\d*\\.\\d+$|^0\\.\\d+$|^[1-9]\\d*$", message = "returnMileage内容不正确")
    @Schema(description = "返程公里数")
    private String returnMileage;

    @NotNull(message = "ruleId不能为空")
    @Min(value = 1, message = "ruleId不能小于1")
    @Schema(description = "规则ID")
    private Long ruleId;

    @NotBlank(message = "paymentRate不能为空")
    @Pattern(regexp = "^0\\.\\d+$|^[1-9]\\d*$|^0$", message = "paymentRate内容不正确")
    @Schema(description = "支付手续费率")
    private String paymentRate;

    @NotBlank(message = "paymentFee不能为空")
    @Pattern(regexp = "^[1-9]\\d*\\.\\d{1,2}$|^0\\.\\d{1,2}$|^[1-9]\\d*$", message = "paymentFee内容不正确")
    @Schema(description = "支付手续费")
    private String paymentFee;

    @NotBlank(message = "taxRate不能为空")
    @Pattern(regexp = "^0\\.\\d+$|^[1-9]\\d*$|^0$", message = "taxRate内容不正确")
    @Schema(description = "代缴个税费率")
    private String taxRate;

    @NotBlank(message = "taxFee不能为空")
    @Pattern(regexp = "^[1-9]\\d*\\.\\d{1,2}$|^0\\.\\d{1,2}$|^[1-9]\\d*$", message = "taxFee内容不正确")
    @Schema(description = "代缴个税")
    private String taxFee;

    @NotBlank(message = "systemIncome不能为空")
    @Pattern(regexp = "^[1-9]\\d*\\.\\d{1,2}$|^0\\.\\d{1,2}$|^[1-9]\\d*$", message = "systemIncome内容不正确")
    @Schema(description = "代驾系统分账收入")
    private String systemIncome;

    @NotBlank(message = "driverIncome不能为空")
    @Pattern(regexp = "^[1-9]\\d*\\.\\d{1,2}$|^0\\.\\d{1,2}$|^[1-9]\\d*$", message = "driverIncome内容不正确")
    @Schema(description = "司机分账收入")
    private String driverIncome;

}

