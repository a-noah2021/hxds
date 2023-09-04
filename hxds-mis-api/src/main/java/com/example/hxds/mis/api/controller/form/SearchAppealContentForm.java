package com.example.hxds.mis.api.controller.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-09-04 21:45
 **/
@Data
@Schema(description = "查询审批工作流内容的表单")
public class SearchAppealContentForm {
    @NotBlank(message = "instanceId不能为空")
    @Schema(description = "工作流实例ID")
    private String instanceId;

    @NotNull(message = "isEnd不能为空")
    @Schema(description = "审批是否结束")
    private Boolean isEnd;
}
