package com.example.hxds.mis.api.controller.form;

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
 * @date: 2023-09-04 21:37
 **/
@Data
@Schema(description = "处理评价申诉的表单")
public class HandleCommentAppealForm {
    @NotNull(message = "customerId不能为空")
    @Min(value = 1,message = "commentId不能小于1")
    private Long commentId;

    @NotBlank(message = "result不能为空")
    @Pattern(regexp = "^同意$|^不同意$", message = "result内容不正确")
    @Schema(description = "处理结果")
    private String result;

    @Schema(description = "处理说明")
    private String note;

    @NotBlank(message = "instanceId不能为空")
    @Schema(description = "工作流实例ID")
    private String instanceId;

    @Schema(description = "用户ID")
    private Integer userId;

}
