package com.example.hxds.vhr.controller.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-09-02 16:28
 **/
@Data
@Schema(description = "使用代金券的表单")
public class UseVoucherForm {
    @NotNull(message = "id不能为空")
    @Min(value = 1, message = "id不能小于1")
    @Schema(description = "领取代金券的ID")
    private Long id;

    @NotNull(message = "voucherId不能为空")
    @Min(value = 1, message = "voucherId不能小于1")
    @Schema(description = "代金券的ID")
    private Long voucherId;

    @NotNull(message = "customerId不能为空")
    @Min(value = 1, message = "customerId不能小于1")
    @Schema(description = "客户ID")
    private Long customerId;

    @NotNull(message = "orderId不能为空")
    @Min(value = 1, message = "orderId不能小于1")
    @Schema(description = "客户ID")
    private Long orderId;

    @NotBlank(message = "amount不能为空")
    @Pattern(regexp = "^[1-9]\\d*\\.\\d{1,2}$|^0\\.\\d{1,2}$|^[1-9]\\d*$", message = "amount内容不正确")
    @Schema(description = "订单金额")
    private String amount;

}