package com.example.hxds.bff.customer.controller.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.hibernate.validator.constraints.Range;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-09-04 00:59
 **/
@Data
@Schema(description = "保存订单评价的表单")
public class InsertCommentForm {

    @NotNull
    @Min(value = 1, message = "orderId不能小于1")
    @Schema(description = "订单ID")
    private Long orderId;

    @NotNull(message = "driverId不能为空")
    @Min(value = 1, message = "driverId不能小于1")
    @Schema(description = "司机ID")
    private Long driverId;

    @Schema(description = "客户ID")
    private Long customerId;


    @NotNull(message = "rate不能为空")
    @Range(min = 1, max = 5, message = "rate范围不正确")
    @Schema(description = "评价分数")
    private Byte rate;

    @Schema(description = "评价")
    private String remark = "默认系统好评";
}
