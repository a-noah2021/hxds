package com.example.hxds.mis.api.controller.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-07-09 21:32
 **/
@Data
@Schema(description = "根据ID查询取消规则的表单")
public class SearchCancelRuleByIdForm {
    @NotNull(message = "ruleId不为空")
    @Min(value = 1, message = "ruleId不能小于1")
    @Schema(description = "规则ID")
    private Long ruleId;
}
