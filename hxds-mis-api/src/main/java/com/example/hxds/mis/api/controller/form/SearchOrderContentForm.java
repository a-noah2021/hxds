package com.example.hxds.mis.api.controller.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-07-07 22:47
 **/
@Data
@Schema(description = "查询订单详情的表单")
public class SearchOrderContentForm {
    @NotNull(message = "orderId不能为空")
    @Min(value = 1, message = "orderId不能小于1")
    @Schema(description = "订单ID")
    private Long orderId;
}