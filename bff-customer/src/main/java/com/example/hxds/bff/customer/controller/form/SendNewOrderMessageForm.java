package com.example.hxds.bff.customer.controller.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.*;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-02-05 15:07
 **/
@Data
@Schema(description = "发送新订单消息的表单")
public class SendNewOrderMessageForm {

    @NotEmpty(message = "driversContent不能为空")
    @Schema(description = "司机的相关信息（司机ID#接单距离）")
    private String[] driversContent;

    @NotNull(message = "orderId不能为空")
    @Min(value = 1, message = "orderId不能小于1")
    @Schema(description = "订单ID")
    private Long orderId;

    @NotBlank(message = "from内容不正确")
    @Pattern(regexp = "[\\(\\)0-9A-Z#\\-_\\u4e00-\\u9fa5]{2,50}", message = "from内容不正确")
    @Schema(description = "订单起点")
    private String from;

    @NotBlank(message = "to内容不正确")
    @Pattern(regexp = "[\\(\\)0-9A-Z#\\-_\\u4e00-\\u9fa5]{2,50}", message = "to内容不正确")
    @Schema(description = "订单终点")
    private String to;

    @NotBlank(message = "expectsFee不能为空")
    @Pattern(regexp = "^[1-9]\\d*\\.\\d{1,2}$|^0\\.\\d{1,2}$|^[1-9]\\d*$", message = "expectsFee内容不正确")
    @Schema(description = "预估价格")
    private String expectsFee;

    @NotBlank(message = "mileage不能为空")
    @Pattern(regexp = "^[1-9]\\d*\\.\\d+$|^0\\.\\d*[1-9]\\d*$|^[1-9]\\d*$", message = "mileage内容不正确")
    @Schema(description = "预估里程")
    private String mileage;

    @NotNull(message = "minute")
    @Min(value = 1, message = "minute不能小于1")
    @Schema(description = "预估时长")
    private Integer minute;

    @NotBlank(message = "favourFee不能为空")
    @Pattern(regexp = "^[1-9]\\d*\\.\\d{1,2}$|^0\\.\\d{1,2}$|^[1-9]\\d*$", message = "favourFee内容不正确")
    @Schema(description = "顾客好处费")
    private String favourFee;
}